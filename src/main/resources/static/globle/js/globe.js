// The globe: a d3-geo orthographic projection drawn on a <canvas>.
//
// Canvas rather than SVG because the whole world is redrawn on every frame of a
// drag, and a few hundred country paths as DOM nodes is what makes an SVG globe
// stutter on a phone. Frames are only drawn while something is moving (a drag,
// inertia, a fly-to, a pulse, or the idle spin for its first 30 s while no dialog
// is open); a still globe costs nothing.
//
// The globe knows geometry and nothing about the game: main.js hands it a view
// (which countries are guessed, in what colour, whether the answer is showing)
// and it draws that. It reports hovers and taps back as country codes.
//
// d3 and topojson-client are classic UMD scripts loaded before the modules, so
// they are read off window here rather than imported.

import { ANSWER_COLOR, withAlpha } from "./scale.js";

const d3 = window.d3;
const topojson = window.topojson;

const SPHERE = { type: "Sphere" };
const TAU = Math.PI * 2;

const MIN_ZOOM = 1;
const MAX_ZOOM = 14;
const SPIN_DEG_PER_MS = 0.005;     // one turn every ~70 s: alive, not busy
// The idle spin redraws the whole globe every frame, which keeps a phone's main
// thread busy. It stops after this long, and pauses while a dialog is open.
const IDLE_SPIN_MS = 30000;
const INERTIA_MS = 330;            // velocity half-life-ish after a fling
const TINY_AREA_PX = 60;           // smaller than this on screen gets a marker dot
// Coarser copies of the map, in degrees between kept points. The whole globe at
// phone size cannot show more than one point every few kilometres, and the full
// 1:50m data is ~80k points: projecting and filling all of them every frame of a
// drag is what makes a canvas globe stutter.
const LOD_TOLERANCES = [0.3, 0.1, 0.03];
const LOD_ERROR_STILL_PX = 0.9;
const LOD_ERROR_MOVING_PX = 2.5;
const LABEL_FONT = "600 12px Poppins, system-ui, -apple-system, sans-serif";

const OCEAN_LIGHT = "#1d4d7d";
const OCEAN_DARK = "#071d36";
const GRATICULE = "rgba(255, 255, 255, 0.07)";
const LAND = "#4b5d55";
const LAND_DIM = "#2c3833";
const BORDER = "rgba(6, 14, 20, 0.6)";
const GUESS_STROKE = "rgba(255, 255, 255, 0.6)";
const RIM = "rgba(160, 205, 255, 0.28)";
const REVEAL_STROKE = "#86efac";

/** Where to look, and how close, when a regional game starts. */
const REGION_VIEWS = {
    world: { centre: [10, 22], zoom: 1 },
    africa: { centre: [18, 2], zoom: 1.55 },
    americas: { centre: [-78, 8], zoom: 1.1 },
    asia: { centre: [92, 30], zoom: 1.35 },
    europe: { centre: [14, 52], zoom: 2.3 },
    oceania: { centre: [150, -18], zoom: 1.6 },
};

const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
const wrap180 = (deg) => ((((deg + 180) % 360) + 360) % 360) - 180;

export class Globe {
    /**
     * options:
     *   reducedMotion  no spin, no inertia, no animated moves or pulses
     *   onHover(hit)   hit = { code, what: "country"|"capital", x, y, tap } or null
     *   onInteract()   the first time the player drags, zooms or uses a key
     */
    constructor(canvas, options = {}) {
        this.canvas = canvas;
        this.ctx = canvas.getContext("2d");
        this.reducedMotion = !!options.reducedMotion;
        this.onHover = options.onHover || (() => {});
        this.onInteract = options.onInteract || (() => {});

        this.projection = d3.geoOrthographic().clipAngle(90).precision(0.6);
        this.path = d3.geoPath(this.projection, this.ctx);
        this.graticule = d3.geoGraticule10();

        this.rotation = [-REGION_VIEWS.world.centre[0], -REGION_VIEWS.world.centre[1]];
        this.zoom = 1;
        this.width = 0;
        this.height = 0;
        this.dpr = 1;

        this.topology = null;
        this.features = new Map();
        this.centres = new Map();
        this.regionOf = new Map();
        this.geometries = new Map();
        this.areas = new Map();
        this.levels = [];
        this.lastDrawMoving = false;

        this.view = { kind: "country", region: "world", guesses: [], latestCode: null, answer: null, status: "PLAYING" };
        this.blind = false;
        this.spinWanted = true;
        this.spinMs = 0;              // idle spin so far, toward IDLE_SPIN_MS
        this.interacted = false;

        this.fly = null;
        this.inertia = null;
        this.pulses = [];
        this.markers = [];
        this.pointers = new Map();
        this.drag = null;
        this.pinch = null;
        this.raf = 0;
        this.hoverRaf = 0;
        this.lastFrame = 0;
        this.hatch = null;

        this.bindEvents();
        this.resizeObserver = new ResizeObserver(() => this.resize());
        this.resizeObserver.observe(canvas.parentElement || canvas);
        this.resize();
    }

    // ------------------------------------------------------------------ data

    /** Loads the TopoJSON world. regionOf maps code -> region for regional games. */
    setWorld(topology, regionOf) {
        const object = topology && topology.objects && topology.objects.countries;
        if (!object) {
            throw new Error("world.json has no countries object");
        }
        this.topology = topology;
        this.regionOf = regionOf || new Map();
        this.features.clear();
        this.centres.clear();
        this.areas.clear();
        // Guessed countries, picking and centring always use the full detail.
        for (const feature of topojson.feature(topology, object).features) {
            if (feature.id != null) {
                this.features.set(String(feature.id), feature);
            }
        }
        // Unguessed land is drawn from whichever level of detail the zoom needs.
        // Thinning works on the shared arcs, so neighbours still meet exactly.
        const decoded = decodeArcs(topology);
        this.levels = LOD_TOLERANCES.map((tol) => ({
            tol,
            topology: { type: "Topology", objects: topology.objects, arcs: decoded.map((arc) => thinArc(arc, tol)) },
        }));
        this.levels.push({ tol: 0, topology });
        this.geometries = new Map(object.geometries.map((g) => [String(g.id), g]));
        this.requestDraw();
    }

    get ready() {
        return this.topology !== null;
    }

    hasFeature(code) {
        return this.features.has(code);
    }

    /**
     * A point to centre on for a country: the centroid of its largest polygon, so
     * France centres on France rather than somewhere between Paris and French
     * Guiana, and Chile does not centre in the Pacific.
     */
    centreOf(code) {
        if (this.centres.has(code)) {
            return this.centres.get(code);
        }
        const feature = this.features.get(code);
        if (!feature || !feature.geometry) {
            return null;
        }
        let shape = feature.geometry;
        if (shape.type === "MultiPolygon") {
            let best = null;
            let bestArea = -1;
            for (const coordinates of shape.coordinates) {
                const polygon = { type: "Polygon", coordinates };
                let area = d3.geoArea(polygon);
                // A wrongly wound ring measures as the rest of the planet.
                if (area > TAU) area = 2 * TAU - area;
                if (area > bestArea) {
                    bestArea = area;
                    best = polygon;
                }
            }
            shape = best || shape;
        }
        const centre = d3.geoCentroid(shape);
        const result = Number.isFinite(centre[0]) && Number.isFinite(centre[1]) ? centre : null;
        this.centres.set(code, result);
        return result;
    }

    /**
     * What to draw.
     *   kind        "country" | "capital"
     *   region      dims land outside it in a regional game
     *   guesses     [{ code, color, capital: { name, lat, lng } | null }]
     *   latestCode  the newest guess, drawn a little larger in capitals mode
     *   answer      { code, name, lat, lng, capital } once the game is over, else null
     *   status      "PLAYING" | "WON" | "GAVE_UP"
     */
    setGame(view) {
        this.view = {
            kind: view.kind || "country",
            region: view.region || "world",
            guesses: view.guesses || [],
            latestCode: view.latestCode || null,
            answer: view.answer || null,
            status: view.status || "PLAYING",
        };
        // setGame runs on every render, so the spin's time limit restarts only when
        // spinning becomes wanted again (a new game), not on each call.
        const want = this.view.guesses.length === 0 && this.view.status === "PLAYING";
        if (want && !this.spinWanted) this.spinMs = 0;
        this.spinWanted = want;
        this.requestDraw();
    }

    /** Blind: only the sea and what you have guessed. */
    setBlind(on) {
        this.blind = !!on;
        this.requestDraw();
    }

    /** The coarsest level of detail whose error stays under about a pixel (two while moving). */
    levelFor(moving) {
        const pxPerDegree = (this.projection.scale() * Math.PI) / 180;
        const budget = moving ? LOD_ERROR_MOVING_PX : LOD_ERROR_STILL_PX;
        const level = this.levels.find((l) => l.tol * pxPerDegree <= budget) || this.levels[this.levels.length - 1];
        if (!level.land) {
            // One merged land shape and one border mesh draw far faster than ~250
            // separate country fills, and nothing about unguessed land needs them apart.
            const object = level.topology.objects.countries;
            level.land = withoutInverted(topojson.merge(level.topology, object.geometries));
            level.borders = topojson.mesh(level.topology, object, (a, b) => a !== b);
            level.regions = new Map();
        }
        return level;
    }

    /**
     * A country's outline at a level of detail, for drawing. Falls back to full
     * detail if thinning broke it; picking and centring always use full detail.
     */
    featureAt(level, code) {
        const full = this.features.get(code);
        if (level.tol === 0 || !full) return full;
        if (!level.features) level.features = new Map();
        if (!level.features.has(code)) {
            const geometry = this.geometries.get(code);
            const thin = geometry ? topojson.feature(level.topology, geometry) : null;
            level.features.set(code, (thin && withoutInvertedFeature(thin)) || full);
        }
        return level.features.get(code);
    }

    /** Land inside and outside a region, for dimming the rest of the world in a regional game. */
    regionShapes(level, region) {
        if (!level.regions.has(region)) {
            const geometries = level.topology.objects.countries.geometries;
            const inRegion = (g) => this.regionOf.get(String(g.id)) === region;
            const inside = geometries.filter(inRegion);
            level.regions.set(region, inside.length === 0 ? null : {
                inside: withoutInverted(topojson.merge(level.topology, inside)),
                outside: withoutInverted(topojson.merge(level.topology, geometries.filter((g) => !inRegion(g)))),
            });
        }
        return level.regions.get(region);
    }

    /** Solid angle of a country in steradians, cached: area on screen is this times r squared. */
    sphericalArea(code, feature) {
        let area = this.areas.get(code);
        if (area === undefined) {
            area = d3.geoArea(feature);
            if (area > TAU) area = 2 * TAU - area;
            this.areas.set(code, area);
        }
        return area;
    }

    isMoving() {
        return !!(this.fly || this.inertia || this.drag || this.pinch) || this.shouldSpin();
    }

    // ------------------------------------------------------------------ camera

    centre() {
        return [-this.rotation[0], -this.rotation[1]];
    }

    setCentre([lon, lat]) {
        this.rotation = [wrap180(-lon), clamp(-lat, -89, 89)];
    }

    rotateBy(dLambda, dPhi) {
        this.rotation = [wrap180(this.rotation[0] + dLambda), clamp(this.rotation[1] + dPhi, -89, 89)];
    }

    setZoom(zoom) {
        this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        this.requestDraw();
    }

    /** Smoothly turns the globe to put [lon, lat] in the middle, optionally zooming. */
    flyTo(lonlat, { zoom, duration } = {}) {
        if (!lonlat || !Number.isFinite(lonlat[0]) || !Number.isFinite(lonlat[1])) {
            return;
        }
        const from = this.centre();
        const zoomFrom = this.zoom;
        const zoomTo = zoom == null ? this.zoom : clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        const angle = d3.geoDistance(from, lonlat);
        this.inertia = null;
        if (this.reducedMotion || (angle < 0.002 && Math.abs(zoomTo - zoomFrom) < 0.01)) {
            this.fly = null;
            this.setCentre(lonlat);
            this.zoom = zoomTo;
            this.requestDraw();
            return;
        }
        this.fly = {
            start: performance.now(),
            duration: duration != null ? duration : Math.min(1700, 550 + angle * 420),
            interpolate: d3.geoInterpolate(from, lonlat),
            zoomFrom,
            zoomTo,
            // Pull the camera back a little on long trips, like a plane climbing.
            dip: Math.min(0.2, (angle / Math.PI) * 0.35),
        };
        this.requestDraw();
    }

    focusRegion(region) {
        const view = REGION_VIEWS[region] || REGION_VIEWS.world;
        this.flyTo(view.centre, { zoom: view.zoom });
    }

    zoomBy(factor) {
        this.markInteracted();
        this.flyTo(this.centre(), { zoom: this.zoom * factor, duration: 260 });
    }

    /** Back to the whole globe, looking at the given point (or where we are). */
    resetView(lonlat) {
        this.markInteracted();
        this.flyTo(lonlat || this.centre(), { zoom: 1 });
    }

    /** Expanding rings at a point: the "found it" moment. */
    pulse(lonlat, color = ANSWER_COLOR) {
        if (this.reducedMotion || !lonlat) {
            return;
        }
        this.pulses.push({ lonlat, color, start: performance.now(), duration: 2200 });
        this.requestDraw();
    }

    // ------------------------------------------------------------------ frames

    requestDraw() {
        if (!this.raf) {
            this.raf = requestAnimationFrame((now) => this.frame(now));
        }
    }

    shouldSpin() {
        return this.spinWanted && !this.interacted && !this.reducedMotion && !this.fly && !this.inertia
            && this.spinMs < IDLE_SPIN_MS
            // Nobody is watching it behind a modal sheet.
            && !document.querySelector("dialog[open]");
    }

    frame(now) {
        this.raf = 0;
        // Clamp the step so a tab coming back from the background does not jump.
        const dt = this.lastFrame ? Math.min(64, now - this.lastFrame) : 16;
        this.lastFrame = now;

        if (this.fly) {
            this.stepFly(now);
        } else if (this.inertia) {
            this.stepInertia(dt);
        } else if (this.shouldSpin()) {
            this.rotateBy(SPIN_DEG_PER_MS * dt, 0);
            this.spinMs += dt;
        }
        if (this.pulses.length) {
            this.pulses = this.pulses.filter((p) => now - p.start < p.duration);
        }

        this.draw(now);

        if (this.fly || this.inertia || this.shouldSpin() || this.pulses.length || this.lastDrawMoving) {
            // The last case is one more frame once motion stops, at full detail.
            this.requestDraw();
        } else {
            this.lastFrame = 0;
        }
    }

    stepFly(now) {
        const fly = this.fly;
        const t = Math.min(1, (now - fly.start) / fly.duration);
        const e = d3.easeCubicInOut(t);
        this.setCentre(fly.interpolate(e));
        const zoom = fly.zoomFrom + (fly.zoomTo - fly.zoomFrom) * e;
        this.zoom = zoom * (1 - fly.dip * Math.sin(Math.PI * t));
        if (t >= 1) {
            this.zoom = fly.zoomTo;
            this.fly = null;
        }
    }

    stepInertia(dt) {
        const inertia = this.inertia;
        this.rotateBy(inertia.vLambda * dt, inertia.vPhi * dt);
        const decay = Math.exp(-dt / INERTIA_MS);
        inertia.vLambda *= decay;
        inertia.vPhi *= decay;
        if (Math.hypot(inertia.vLambda, inertia.vPhi) < 0.002) {
            this.inertia = null;
        }
    }

    resize() {
        const rect = this.canvas.getBoundingClientRect();
        const dpr = Math.min(window.devicePixelRatio || 1, 2);
        const width = Math.max(1, Math.round(rect.width));
        const height = Math.max(1, Math.round(rect.height));
        if (width === this.width && height === this.height && dpr === this.dpr) {
            return;
        }
        this.width = width;
        this.height = height;
        this.dpr = dpr;
        this.canvas.width = Math.round(width * dpr);
        this.canvas.height = Math.round(height * dpr);
        // Resizing clears the canvas, so repaint now rather than flash a blank frame.
        this.draw(performance.now());
    }

    applyProjection() {
        const radius = Math.max(40, Math.min(this.width, this.height) / 2 - 18);
        this.projection
            .translate([this.width / 2, this.height / 2])
            .scale(radius * this.zoom)
            .rotate([this.rotation[0], this.rotation[1], 0]);
    }

    isVisible(lonlat) {
        return d3.geoDistance(lonlat, this.centre()) < Math.PI / 2 - 0.03;
    }

    hatchPattern() {
        if (!this.hatch) {
            const tile = document.createElement("canvas");
            tile.width = 10;
            tile.height = 10;
            const t = tile.getContext("2d");
            t.fillStyle = "rgba(34, 197, 94, 0.22)";
            t.fillRect(0, 0, 10, 10);
            t.strokeStyle = "rgba(134, 239, 172, 0.95)";
            t.lineWidth = 2.2;
            t.beginPath();
            t.moveTo(-2, 12); t.lineTo(12, -2);
            t.moveTo(-2, 2); t.lineTo(2, -2);
            t.moveTo(8, 12); t.lineTo(12, 8);
            t.stroke();
            this.hatch = this.ctx.createPattern(tile, "repeat");
        }
        return this.hatch;
    }

    draw(now = performance.now()) {
        const { ctx, width: w, height: h } = this;
        if (!w || !h) {
            return;
        }
        this.applyProjection();
        const path = this.path;
        const r = this.projection.scale();
        const [cx, cy] = this.projection.translate();
        const view = this.view;
        const finished = view.status !== "PLAYING" && view.answer;
        const answerCode = finished ? view.answer.code : null;
        const capitals = view.kind === "capital";
        const markers = [];
        const moving = this.isMoving();
        this.lastDrawMoving = moving;

        ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
        ctx.clearRect(0, 0, w, h);

        // Atmosphere: a soft glow just outside the limb.
        const halo = ctx.createRadialGradient(cx, cy, r * 0.98, cx, cy, r * 1.16);
        halo.addColorStop(0, "rgba(110, 180, 255, 0.30)");
        halo.addColorStop(1, "rgba(110, 180, 255, 0)");
        ctx.fillStyle = halo;
        ctx.beginPath();
        ctx.arc(cx, cy, r * 1.16, 0, TAU);
        ctx.fill();

        // Ocean, lit from the upper left.
        const ocean = ctx.createRadialGradient(cx - r * 0.35, cy - r * 0.4, r * 0.05, cx, cy, r * 1.05);
        ocean.addColorStop(0, OCEAN_LIGHT);
        ocean.addColorStop(1, OCEAN_DARK);
        ctx.beginPath();
        path(SPHERE);
        ctx.fillStyle = ocean;
        ctx.fill();

        ctx.beginPath();
        path(this.graticule);
        ctx.strokeStyle = GRATICULE;
        ctx.lineWidth = 0.6;
        ctx.stroke();

        const level = this.levels.length ? this.levelFor(moving) : null;
        if (!this.blind && level) {
            const regional = view.region !== "world" ? this.regionShapes(level, view.region) : null;
            if (regional) {
                ctx.beginPath();
                path(regional.outside);
                ctx.fillStyle = LAND_DIM;
                ctx.fill();
                ctx.beginPath();
                path(regional.inside);
                ctx.fillStyle = LAND;
                ctx.fill();
            } else {
                ctx.beginPath();
                path(level.land);
                ctx.fillStyle = LAND;
                ctx.fill();
            }
            ctx.beginPath();
            path(level.borders);
            ctx.strokeStyle = BORDER;
            ctx.lineWidth = 0.6;
            ctx.stroke();
        }

        // Guessed countries. In capitals mode the country is only tinted: the dot
        // at the capital is what carries the distance.
        for (const g of view.guesses) {
            if (g.code === answerCode || !level) continue;
            const feature = this.featureAt(level, g.code);
            if (!feature) continue;
            ctx.beginPath();
            path(feature);
            ctx.fillStyle = capitals ? withAlpha(g.color, 0.38) : g.color;
            ctx.fill();
            ctx.strokeStyle = capitals ? withAlpha(g.color, 0.75) : GUESS_STROKE;
            ctx.lineWidth = 0.8;
            ctx.stroke();
            if (!capitals) this.collectMarker(markers, g.code, feature, g.color, "guess");
        }

        // The answer, once the game is over: solid green when found, hatched when
        // revealed by giving up, so a screenshot can tell the two apart.
        if (answerCode && level) {
            const feature = this.featureAt(level, answerCode);
            if (feature) {
                const won = view.status === "WON";
                ctx.beginPath();
                path(feature);
                if (won) {
                    ctx.fillStyle = capitals ? withAlpha(ANSWER_COLOR, 0.5) : ANSWER_COLOR;
                    ctx.fill();
                    ctx.strokeStyle = "#dcfce7";
                    ctx.lineWidth = 1;
                    ctx.stroke();
                } else {
                    ctx.fillStyle = this.hatchPattern();
                    ctx.fill();
                    ctx.setLineDash([4, 3]);
                    ctx.strokeStyle = REVEAL_STROKE;
                    ctx.lineWidth = 1.5;
                    ctx.stroke();
                    ctx.setLineDash([]);
                }
                if (!capitals) this.collectMarker(markers, answerCode, feature, ANSWER_COLOR, won ? "won" : "revealed");
            }
        }

        // Shade the limb so the sphere reads as round, then a thin rim.
        const shade = ctx.createRadialGradient(cx - r * 0.3, cy - r * 0.35, r * 0.1, cx, cy, r);
        shade.addColorStop(0, "rgba(255, 255, 255, 0.07)");
        shade.addColorStop(0.55, "rgba(255, 255, 255, 0)");
        shade.addColorStop(1, "rgba(0, 6, 16, 0.38)");
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, TAU);
        ctx.fillStyle = shade;
        ctx.fill();
        ctx.strokeStyle = RIM;
        ctx.lineWidth = 1;
        ctx.stroke();

        for (const m of markers) this.drawMarker(m);
        if (capitals) this.drawCapitals(answerCode);
        if (answerCode && !capitals) {
            const centre = this.answerPoint();
            if (centre && this.isVisible(centre)) {
                const [x, y] = this.projection(centre);
                this.drawLabel(x, y, view.answer.name, view.status === "WON" ? ANSWER_COLOR : REVEAL_STROKE);
            }
        }
        this.drawPulses(now);
        this.markers = markers;
    }

    /** Where the answer sits: its own label point if the server gave one. */
    answerPoint() {
        const answer = this.view.answer;
        if (!answer) return null;
        if (Number.isFinite(answer.lng) && Number.isFinite(answer.lat)) return [answer.lng, answer.lat];
        return this.centreOf(answer.code);
    }

    /** A dot over a country too small to see at this zoom (Vatican, Tuvalu...). */
    collectMarker(markers, code, feature, color, style) {
        // Measured at the centre of the disc, where a country looks biggest; an
        // exact path.area() per country per frame costs as much as drawing it.
        const r = this.projection.scale();
        if (this.sphericalArea(code, feature) * r * r > TINY_AREA_PX) return;
        const centre = this.centreOf(code);
        if (!centre || !this.isVisible(centre)) return;
        const [x, y] = this.projection(centre);
        markers.push({ code, x, y, color, style });
    }

    drawMarker({ x, y, color, style }) {
        const ctx = this.ctx;
        ctx.beginPath();
        ctx.arc(x, y, 9, 0, TAU);
        ctx.fillStyle = withAlpha(color, 0.25);
        ctx.fill();
        if (style === "revealed") {
            ctx.setLineDash([3, 2]);
            ctx.strokeStyle = REVEAL_STROKE;
            ctx.lineWidth = 1.5;
            ctx.stroke();
            ctx.setLineDash([]);
        }
        ctx.beginPath();
        ctx.arc(x, y, 4.5, 0, TAU);
        ctx.fillStyle = color;
        ctx.fill();
        ctx.strokeStyle = "#ffffff";
        ctx.lineWidth = 1.5;
        ctx.stroke();
    }

    /** Capital dots, plus the points used for hover picking in capitals mode. */
    capitalPoints() {
        const view = this.view;
        const finished = view.status !== "PLAYING" && view.answer;
        const points = [];
        for (const g of view.guesses) {
            if (!g.capital) continue;
            points.push({
                code: g.code,
                lonlat: [g.capital.lng, g.capital.lat],
                name: g.capital.name,
                color: finished && g.code === view.answer.code ? ANSWER_COLOR : g.color,
                latest: g.code === view.latestCode,
                answer: finished && g.code === view.answer.code,
            });
        }
        if (finished && view.answer.capital && !points.some((p) => p.answer)) {
            const c = view.answer.capital;
            points.push({ code: view.answer.code, lonlat: [c.lng, c.lat], name: c.name, color: ANSWER_COLOR, latest: false, answer: true, revealed: true });
        }
        return points;
    }

    drawCapitals() {
        const ctx = this.ctx;
        const playing = this.view.status === "PLAYING";
        const labels = [];
        // Latest and answer last, so they sit on top of their neighbours.
        const points = this.capitalPoints().sort((a, b) => (a.latest || a.answer) - (b.latest || b.answer));
        for (const p of points) {
            if (!this.isVisible(p.lonlat)) continue;
            const [x, y] = this.projection(p.lonlat);
            const radius = p.latest || p.answer ? 6 : 4.5;
            if (p.answer) {
                ctx.beginPath();
                ctx.arc(x, y, radius + 5, 0, TAU);
                if (p.revealed) ctx.setLineDash([3, 2]);
                ctx.strokeStyle = p.revealed ? REVEAL_STROKE : ANSWER_COLOR;
                ctx.lineWidth = 1.5;
                ctx.stroke();
                ctx.setLineDash([]);
            }
            ctx.beginPath();
            ctx.arc(x, y, radius, 0, TAU);
            ctx.fillStyle = p.color;
            ctx.fill();
            ctx.strokeStyle = "#ffffff";
            ctx.lineWidth = p.latest || p.answer ? 2 : 1.4;
            ctx.stroke();
            if (p.answer || (p.latest && playing)) labels.push({ x, y, text: p.name, accent: p.color });
        }
        for (const l of labels) this.drawLabel(l.x, l.y, l.text, l.accent);
    }

    drawLabel(x, y, text, accent) {
        if (!text) return;
        const ctx = this.ctx;
        ctx.font = LABEL_FONT;
        const padX = 8;
        const height = 22;
        const width = ctx.measureText(text).width + padX * 2;
        let lx = x + 10;
        let ly = y - height - 6;
        if (lx + width > this.width - 4) lx = x - 10 - width;
        if (lx < 4) lx = 4;
        if (ly < 4) ly = y + 10;
        ctx.beginPath();
        roundRect(ctx, lx, ly, width, height, height / 2);
        ctx.fillStyle = "rgba(6, 14, 24, 0.86)";
        ctx.fill();
        ctx.strokeStyle = accent || "rgba(255, 255, 255, 0.4)";
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.fillStyle = "#ffffff";
        ctx.textBaseline = "middle";
        ctx.fillText(text, lx + padX, ly + height / 2 + 0.5);
    }

    drawPulses(now) {
        const ctx = this.ctx;
        for (const p of this.pulses) {
            if (!this.isVisible(p.lonlat)) continue;
            const [x, y] = this.projection(p.lonlat);
            for (const offset of [0, 0.28, 0.56]) {
                const t = (now - p.start) / p.duration - offset;
                if (t < 0 || t > 1) continue;
                const eased = 1 - Math.pow(1 - t, 3);
                ctx.beginPath();
                ctx.arc(x, y, 6 + eased * 54, 0, TAU);
                ctx.strokeStyle = withAlpha(p.color, (1 - t) * 0.9);
                ctx.lineWidth = 2.5 * (1 - t) + 0.5;
                ctx.stroke();
            }
        }
    }

    // ------------------------------------------------------------------ picking

    /** The guessed country or capital under a point on the canvas, or null. */
    pick(point, slop) {
        if (!this.ready) return null;
        const view = this.view;

        if (view.kind === "capital") {
            let best = null;
            let bestDistance = slop;
            for (const p of this.capitalPoints()) {
                if (!this.isVisible(p.lonlat)) continue;
                const [x, y] = this.projection(p.lonlat);
                const d = Math.hypot(x - point.x, y - point.y);
                if (d <= bestDistance) {
                    best = { code: p.code, what: "capital", x, y };
                    bestDistance = d;
                }
            }
            if (best) return best;
        }

        for (const m of this.markers) {
            if (Math.hypot(m.x - point.x, m.y - point.y) <= slop + 4) {
                return { code: m.code, what: "country", x: m.x, y: m.y };
            }
        }

        const [cx, cy] = this.projection.translate();
        if (Math.hypot(point.x - cx, point.y - cy) > this.projection.scale()) return null;
        const lonlat = this.projection.invert([point.x, point.y]);
        if (!lonlat || !Number.isFinite(lonlat[0])) return null;

        const codes = view.guesses.map((g) => g.code);
        if (view.status !== "PLAYING" && view.answer) codes.push(view.answer.code);
        for (const code of codes.reverse()) {
            const feature = this.features.get(code);
            if (feature && d3.geoContains(feature, lonlat)) {
                return { code, what: "country", x: point.x, y: point.y };
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    bindEvents() {
        const c = this.canvas;
        c.addEventListener("pointerdown", (e) => this.onPointerDown(e));
        c.addEventListener("pointermove", (e) => this.onPointerMove(e));
        c.addEventListener("pointerup", (e) => this.onPointerUp(e, false));
        c.addEventListener("pointercancel", (e) => this.onPointerUp(e, true));
        c.addEventListener("pointerleave", (e) => {
            if (e.pointerType === "mouse" && this.pointers.size === 0) this.onHover(null);
        });
        c.addEventListener("wheel", (e) => this.onWheel(e), { passive: false });
        c.addEventListener("keydown", (e) => this.onKey(e));
        document.addEventListener("visibilitychange", () => {
            this.lastFrame = 0;
            if (!document.hidden) this.requestDraw();
        });
        // A dialog closing may let the idle spin carry on. close does not bubble,
        // hence the capture phase.
        document.addEventListener("close", () => this.requestDraw(), true);
    }

    local(e) {
        const rect = this.canvas.getBoundingClientRect();
        return { x: e.clientX - rect.left, y: e.clientY - rect.top };
    }

    markInteracted() {
        if (!this.interacted) {
            this.interacted = true;
            this.onInteract();
        }
    }

    stopMotion() {
        this.fly = null;
        this.inertia = null;
    }

    onPointerDown(e) {
        if (e.pointerType === "mouse" && e.button !== 0) return;
        try {
            this.canvas.setPointerCapture(e.pointerId);
        } catch {
            // Capture is a nicety; dragging still works without it.
        }
        this.stopMotion();
        this.markInteracted();
        const p = this.local(e);
        this.pointers.set(e.pointerId, p);
        if (this.pointers.size === 1) {
            this.drag = { x: p.x, y: p.y, moved: 0, t0: performance.now(), samples: [], tap: true };
            this.pinch = null;
        } else if (this.pointers.size === 2) {
            const [a, b] = [...this.pointers.values()];
            this.pinch = { distance: Math.hypot(a.x - b.x, a.y - b.y) || 1, zoom: this.zoom };
            this.drag = null;
        }
        this.canvas.classList.add("is-dragging");
    }

    onPointerMove(e) {
        const p = this.local(e);
        if (!this.pointers.has(e.pointerId)) {
            if (e.pointerType === "mouse") this.scheduleHover(p);
            return;
        }
        this.pointers.set(e.pointerId, p);

        if (this.pinch && this.pointers.size >= 2) {
            const [a, b] = [...this.pointers.values()];
            this.setZoom(this.pinch.zoom * (Math.hypot(a.x - b.x, a.y - b.y) / this.pinch.distance));
            return;
        }
        if (!this.drag) return;

        const dx = p.x - this.drag.x;
        const dy = p.y - this.drag.y;
        this.drag.x = p.x;
        this.drag.y = p.y;
        this.drag.moved += Math.abs(dx) + Math.abs(dy);
        // At the centre one pixel moves the surface 1/r radians.
        const k = 180 / (Math.PI * this.projection.scale());
        const dLambda = dx * k;
        const dPhi = -dy * k;
        this.rotateBy(dLambda, dPhi);
        const now = performance.now();
        this.drag.samples.push({ t: now, dLambda, dPhi });
        while (this.drag.samples.length && now - this.drag.samples[0].t > 100) this.drag.samples.shift();
        if (this.drag.moved > 6) {
            this.drag.tap = false;
            this.onHover(null);
        }
        this.requestDraw();
    }

    onPointerUp(e, cancelled) {
        if (!this.pointers.has(e.pointerId)) return;
        const p = this.local(e);
        this.pointers.delete(e.pointerId);
        try {
            this.canvas.releasePointerCapture(e.pointerId);
        } catch {
            // Already released.
        }

        if (this.pointers.size === 1) {
            // One finger lifted from a pinch: carry on as a drag from the other.
            const [q] = [...this.pointers.values()];
            this.pinch = null;
            this.drag = { x: q.x, y: q.y, moved: 99, t0: performance.now(), samples: [], tap: false };
            return;
        }
        if (this.pointers.size > 0) return;

        this.canvas.classList.remove("is-dragging");
        const drag = this.drag;
        this.drag = null;
        this.pinch = null;
        this.requestDraw();
        if (!drag || cancelled) return;

        if (drag.tap && performance.now() - drag.t0 < 500) {
            const hit = this.pick(p, e.pointerType === "mouse" ? 10 : 18);
            this.onHover(hit ? { ...hit, tap: true } : null);
            return;
        }
        this.startInertia(drag.samples);
    }

    startInertia(samples) {
        if (this.reducedMotion || samples.length === 0) return;
        const now = performance.now();
        const recent = samples.filter((s) => now - s.t < 80);
        if (recent.length === 0) return;
        const span = Math.max(16, now - recent[0].t);
        const vLambda = recent.reduce((sum, s) => sum + s.dLambda, 0) / span;
        const vPhi = recent.reduce((sum, s) => sum + s.dPhi, 0) / span;
        if (Math.hypot(vLambda, vPhi) < 0.01) return;
        this.inertia = { vLambda, vPhi };
        this.requestDraw();
    }

    scheduleHover(point) {
        this.hoverPoint = point;
        if (this.hoverRaf) return;
        this.hoverRaf = requestAnimationFrame(() => {
            this.hoverRaf = 0;
            this.onHover(this.pick(this.hoverPoint, 10));
        });
    }

    onWheel(e) {
        e.preventDefault();
        this.markInteracted();
        this.stopMotion();
        // ctrlKey is how trackpad pinches arrive; their deltas are much smaller.
        const perUnit = e.ctrlKey ? 0.01 : e.deltaMode === 1 ? 0.05 : e.deltaMode === 2 ? 1 : 0.0015;
        this.setZoom(this.zoom * Math.exp(-e.deltaY * perUnit));
    }

    onKey(e) {
        const step = 15 / this.zoom;
        const [lon, lat] = this.centre();
        let handled = true;
        switch (e.key) {
            case "ArrowLeft": this.flyTo([lon - step, lat], { duration: 180 }); break;
            case "ArrowRight": this.flyTo([lon + step, lat], { duration: 180 }); break;
            case "ArrowUp": this.flyTo([lon, clamp(lat + step, -85, 85)], { duration: 180 }); break;
            case "ArrowDown": this.flyTo([lon, clamp(lat - step, -85, 85)], { duration: 180 }); break;
            case "+": case "=": this.zoomBy(1.4); break;
            case "-": case "_": this.zoomBy(1 / 1.4); break;
            case "0": case "Home": this.resetView(); break;
            default: handled = false;
        }
        if (handled) {
            e.preventDefault();
            this.markInteracted();
        }
    }
}

/** Arc coordinates as absolute [lon, lat], undoing TopoJSON's delta quantisation. */
function decodeArcs(topology) {
    const t = topology.transform;
    return topology.arcs.map((arc) => {
        if (!t) return arc.map((p) => [p[0], p[1]]);
        let x = 0;
        let y = 0;
        return arc.map((p) => {
            x += p[0];
            y += p[1];
            return [x * t.scale[0] + t.translate[0], y * t.scale[1] + t.translate[1]];
        });
    });
}

/**
 * Drops points closer than tol degrees to the last one kept. Endpoints always
 * stay, which is what keeps neighbouring countries meeting at the same border.
 */
function thinArc(arc, tol) {
    if (arc.length <= 4) return arc;
    const out = [arc[0]];
    let last = arc[0];
    for (let i = 1; i < arc.length - 1; i++) {
        const p = arc[i];
        if (Math.abs(p[0] - last[0]) >= tol || Math.abs(p[1] - last[1]) >= tol) {
            out.push(p);
            last = p;
        }
    }
    const n = arc.length;
    out.push(arc[n - 1]);
    const closed = arc[0][0] === arc[n - 1][0] && arc[0][1] === arc[n - 1][1];
    if (!closed || out.length >= 4) return out;
    // A small island drawn as one closed arc has to stay a ring with some area:
    // four points of its outline, in order, keep it a (tiny) quadrilateral.
    return [arc[0], arc[Math.floor(n / 3)], arc[Math.floor((2 * n) / 3)], arc[n - 1]];
}

/**
 * A ring that thinning (or bad source data) turned inside out measures as most
 * of the planet, and d3 would fill the whole globe with it. Drop those.
 */
function withoutInverted(multiPolygon) {
    return {
        type: "MultiPolygon",
        coordinates: multiPolygon.coordinates.filter((polygon) => d3.geoArea({ type: "Polygon", coordinates: polygon }) < TAU),
    };
}

/** The same guard for one country: null if nothing sensible is left. */
function withoutInvertedFeature(feature) {
    const g = feature.geometry;
    if (!g) return null;
    if (g.type === "Polygon") {
        return d3.geoArea(g) < TAU ? feature : null;
    }
    if (g.type === "MultiPolygon") {
        const kept = withoutInverted(g);
        return kept.coordinates.length ? { ...feature, geometry: kept } : null;
    }
    return feature;
}

function roundRect(ctx, x, y, w, h, r) {
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.arcTo(x + w, y, x + w, y + r, r);
    ctx.lineTo(x + w, y + h - r);
    ctx.arcTo(x + w, y + h, x + w - r, y + h, r);
    ctx.lineTo(x + r, y + h);
    ctx.arcTo(x, y + h, x, y + h - r, r);
    ctx.lineTo(x, y + r);
    ctx.arcTo(x, y, x + r, y, r);
    ctx.closePath();
}

#!/usr/bin/env node
// Builds the three data files the Globle game ships:
//
//   src/main/resources/globle/countries.json   names, aliases, regions, difficulty, small
//                                              island nations, capitals, label points
//   src/main/resources/globle/distances.json   minimum great-circle km between every pair
//   src/main/resources/static/globle/world.json  TopoJSON the browser draws the globe from
//
// Geometry is Natural Earth (public domain) via the world-atlas package: the
// 10m set for anything measured (distances, label points), the hand-generalised
// 50m set for what is drawn, topped up from 10m where 50m leaves a country or
// its capital's island out. Distances count only the 10m pieces of a country
// that the globe actually draws, so no colour comes from an islet the player
// cannot see. Names, regions, capitals and difficulty tiers are hand-curated
// in lib/catalog.mjs; which answers are small island nations is worked out
// here from the geometry. lib/sources.mjs says how Natural Earth features
// become countries. tools/globle/README.md explains the choices.
//
// Run with `npm run build` from tools/globle, then `npm run check`. The output
// is deterministic, so re-running on unchanged inputs produces no diff.

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { Worker } from 'node:worker_threads';
import { geoArea, geoContains } from 'd3-geo';
import countries from 'i18n-iso-countries';
import { topology } from 'topojson-server';
import { feature, mergeArcs } from 'topojson-client';

import { CATALOG, DIFFICULTY, EXTRA_ANSWERS, UN_MEMBERS } from './lib/catalog.mjs';
import { normalise, OUTPUTS, REGIONS, REPO } from './lib/common.mjs';
import { countryPolygons, fixWinding } from './lib/sources.mjs';
import {
  buildTree, chordToKm, closestChord, densifiedPoints, EARTH_RADIUS_KM, kmToPolygons, rootsOverlap,
} from './lib/sphere.mjs';
import { labelPoint } from './lib/label.mjs';

// 1e5 matches world-atlas's own quantisation: the 50m arcs land back on the
// grid they came from, so re-encoding loses nothing.
const QUANTIZATION = 1e5;

const log = (...args) => console.error('[globle]', ...args);

// ---------------------------------------------------------------- geometry

log('reading Natural Earth 10m and 50m');
const raw10 = countryPolygons('10m');
const raw50 = countryPolygons('50m');

const missingFromCatalog = [...raw10.keys()].filter((code) => !CATALOG[code]);
const missingGeometry = Object.keys(CATALOG).filter((code) => !raw10.has(code));
if (missingFromCatalog.length || missingGeometry.length) {
  throw new Error(`catalog/geometry mismatch: no catalog entry for ${missingFromCatalog}; no geometry for ${missingGeometry}`);
}
const extra50 = [...raw50.keys()].filter((code) => !raw10.has(code));
if (extra50.length) throw new Error(`50m has codes 10m lacks: ${extra50}`);
const answers = new Set([...UN_MEMBERS, ...EXTRA_ANSWERS]);
for (const code of answers) if (!CATALOG[code]) throw new Error(`answer ${code} has no catalog entry`);

// Every answer sits in exactly one difficulty tier, and nothing else does.
const tierOf = new Map();
for (const [tier, list] of Object.entries(DIFFICULTY)) {
  if (!['1', '2', '3', '4', '5'].includes(tier)) throw new Error(`difficulty tier ${tier} is not 1..5`);
  for (const code of list) {
    if (!answers.has(code)) throw new Error(`difficulty tier ${tier} lists ${code}, which is not an answer`);
    if (tierOf.has(code)) throw new Error(`${code} is in difficulty tiers ${tierOf.get(code)} and ${tier}`);
    tierOf.set(code, Number(tier));
  }
}
const untiered = [...answers].filter((code) => !tierOf.has(code));
if (untiered.length) throw new Error(`answers with no difficulty tier: ${untiered}`);

const fixed10 = new Map([...raw10].map(([code, polys]) => [code, fixWinding(polys)]));
const fixed50 = new Map([...raw50].map(([code, polys]) => [code, fixWinding(polys)]));

// ---------------------------------------------------------------- names

function i18nNames(code) {
  const alpha2 = CATALOG[code].iso2 !== undefined ? CATALOG[code].iso2 : countries.alpha3ToAlpha2(code);
  if (!alpha2) return [];
  const names = countries.getName(alpha2, 'en', { select: 'all' }) ?? [];
  // "Korea, Republic of" and "Netherlands (Kingdom of the)" are list sort keys,
  // not things anyone types.
  return names.filter((n) => !/[,(]/.test(n));
}

const codes = Object.keys(CATALOG).sort((a, b) =>
  CATALOG[a].name.localeCompare(CATALOG[b].name, 'en', { sensitivity: 'base' }));

// Names and hand-written aliases are authoritative: a clash among them is a
// mistake in the catalog. Aliases borrowed from i18n-iso-countries are only
// kept when they point at exactly one country (it calls both Congos "Congo").
const owner = new Map();
const claim = (alias, code, what) => {
  const key = normalise(alias);
  const prev = owner.get(key);
  if (prev && prev !== code) throw new Error(`${what} "${alias}" of ${code} clashes with ${prev}`);
  owner.set(key, code);
};
for (const code of codes) claim(CATALOG[code].name, code, 'name');
for (const code of codes) for (const a of CATALOG[code].aliases) claim(a, code, 'alias');
const borrowed = new Map();
for (const code of codes) {
  for (const a of i18nNames(code)) {
    const key = normalise(a);
    if (!borrowed.has(key)) borrowed.set(key, new Set());
    borrowed.get(key).add(code);
  }
}

function aliasesFor(code) {
  const seen = new Set([normalise(CATALOG[code].name)]);
  const out = [];
  const consider = (a, mustOwn) => {
    const key = normalise(a);
    if (!key || seen.has(key)) return;
    if (mustOwn && ((owner.get(key) ?? code) !== code || borrowed.get(key).size > 1)) return;
    seen.add(key);
    out.push(a);
  };
  CATALOG[code].aliases.forEach((a) => consider(a, false));
  i18nNames(code).forEach((a) => consider(a, true));
  return out;
}

// ---------------------------------------------------------------- countries.json

const round = (v, places) => Number(v.toFixed(places));

/** One entry per code, in countries.json's key order. */
function countryEntries(smallIslands) {
  log('computing label points');
  return codes.map((code) => {
    const cat = CATALOG[code];
    if (!REGIONS.has(cat.region)) throw new Error(`${code}: bad region ${cat.region}`);
    // Vatican City collapses to a line at 10m precision; fall back to 50m.
    const polygons = fixed10.get(code).length ? fixed10.get(code) : fixed50.get(code);
    const [lng, lat] = labelPoint(polygons);
    const iso2 = cat.iso2 !== undefined ? cat.iso2 : (countries.alpha3ToAlpha2(code) ?? null);
    let capital = null;
    if (cat.capital) {
      const [name, cLat, cLng, capAliases = []] = cat.capital;
      capital = { name, aliases: capAliases, lat: cLat, lng: cLng };
    }
    return {
      code,
      name: cat.name,
      iso2,
      aliases: aliasesFor(code),
      answer: answers.has(code),
      region: cat.region,
      difficulty: tierOf.get(code) ?? 5,
      smallIsland: smallIslands.has(code),
      lat: round(lat, 3),
      lng: round(lng, 3),
      capital,
    };
  });
}

// ---------------------------------------------------------------- distances.json

/**
 * The geometry a country is measured on: its 10m polygons, but only those the
 * globe draws. 50m leaves out many remote islets (Isla de Aves, San Andrés,
 * the Kermadecs, Minamitorishima, ...), and a distance measured from one of
 * them would colour a guess by land the player cannot see. A 10m polygon
 * counts when it lies within 25 km of the drawn shape; a drawn polygon with no
 * 10m counterpart that close is measured as drawn. Countries drawn from 10m
 * already are measured whole.
 */
function measuredPolygons(code) {
  const { polygons: disp, why } = displayPolygons(code);
  if (why) return raw10.get(code); // the drawn shape is already 10m
  const dTree = buildTree(densifiedPoints(disp));
  const near = (polys, tree) =>
    chordToKm(closestChord(buildTree(densifiedPoints(polys)), tree)) <= 25;
  const keep = raw10.get(code).filter((p) => near([p], dTree));
  const t10 = buildTree(densifiedPoints(raw10.get(code)));
  for (const p of disp) if (!near([p], t10)) keep.push(p); // drawn at 50m only, e.g. the Chesterfield Is. in NCL
  return keep;
}

async function distanceMatrix() {
  log('densifying the drawn parts of the 10m geometry and building k-d trees');
  const trees = codes.map((code) => buildTree(densifiedPoints(measuredPolygons(code))));
  log(`${trees.reduce((s, t) => s + t.pts.length / 3, 0).toLocaleString()} points`);

  const n = codes.length;
  const chord = Array.from({ length: n }, () => new Float64Array(n));
  const threads = Math.max(1, Math.min(os.availableParallelism?.() ?? os.cpus().length, 16));
  log(`measuring ${(n * (n - 1) / 2).toLocaleString()} pairs on ${threads} threads`);

  // Row i has n-1-i pairs, so handing rows out in order gives the longest
  // first; each goes to whichever worker frees up next.
  const queue = Array.from({ length: n }, (_, i) => i);
  let done = 0;
  await Promise.all(Array.from({ length: threads }, () => new Promise((resolve, reject) => {
    const worker = new Worker(new URL('./lib/distance-worker.mjs', import.meta.url), { workerData: { trees } });
    const next = () => {
      if (queue.length) worker.postMessage(queue.shift());
      else worker.terminate().then(resolve, reject);
    };
    worker.on('message', ({ i, row }) => {
      chord[i].set(row);
      if (++done % 25 === 0) log(`  ${done}/${n} rows`);
      next();
    });
    worker.on('error', reject);
    next();
  })));

  // Points can only see borders. A country entirely inside another without
  // sharing a vertex (an island in a lake) would otherwise read as nonzero, so
  // pairs whose bounding boxes overlap also get a point-in-polygon test.
  const firstVertex = (code) => (fixed10.get(code)[0] ?? raw10.get(code)[0])[0][0];
  const km = Array.from({ length: n }, () => new Array(n).fill(0));
  let contained = 0;
  for (let i = 0; i < n; i++) {
    for (let j = i + 1; j < n; j++) {
      let d = chord[i][j];
      if (d > 0 && rootsOverlap(trees[i], trees[j])) {
        const a = codes[i], b = codes[j];
        if (geoContains({ type: 'MultiPolygon', coordinates: fixed10.get(b) }, firstVertex(a))
          || geoContains({ type: 'MultiPolygon', coordinates: fixed10.get(a) }, firstVertex(b))) {
          d = 0;
          contained++;
        }
      }
      // 0 is reserved for countries that touch; anything else is at least 1 km.
      const v = d === 0 ? 0 : Math.max(1, Math.round(chordToKm(d)));
      km[i][j] = km[j][i] = v;
    }
  }
  if (contained) log(`${contained} pairs touch only by containment`);
  return km;
}

// ---------------------------------------------------------------- small island nations

/**
 * "Mainland only" games leave out the small island nations: Tonga goes, Japan
 * stays. An answer country is one when all three of these hold:
 *
 *   - none of its land is on a continental mainland (Afro-Eurasia, the
 *     Americas, Australia), so it is islands only;
 *   - its 10m land area is under SMALL_ISLAND_MAX_KM2. Solomon Islands
 *     (27,300 km²) is under; Taiwan (36,300 km²) is not;
 *   - it has no land border with another answer country. Haiti, Timor-Leste
 *     and Brunei share their islands, so they are found the way mainland
 *     countries are, by a neighbour lighting up.
 *
 * Microstates on a mainland (Andorra, Vatican City) are not islands, so they
 * stay. check-data.mjs asserts the resulting list.
 */
const SMALL_ISLAND_MAX_KM2 = 30_000;

/**
 * Countries whose largest 10m polygon lies on each mainland. They pick the
 * mainland out of the other landmasses. They also check that land borders
 * joined up: if Egypt's polygon did not reach Russia's, Africa would read as
 * an island.
 */
const MAINLANDS = {
  'Afro-Eurasia': ['RUS', 'CHN', 'IND', 'FRA', 'EGY', 'ZAF'],
  'the Americas': ['USA', 'CAN', 'MEX', 'BRA', 'ARG'],
  'Australia': ['AUS'],
};

const polygonKm2 = (polygon) => geoArea({ type: 'Polygon', coordinates: polygon }) * EARTH_RADIUS_KM ** 2;

/**
 * The codes with land on a continental mainland. Every 10m polygon is a node,
 * and two polygons are one landmass when they share a vertex: Natural Earth
 * shares arcs along land borders, so the countries of a continent join up
 * vertex for vertex, while an island shares none with anything. The raw
 * polygons are used, not the repaired ones, so Vatican City's (collapsed to
 * a line at 10m) still joins Italy's.
 */
function mainlandCodes() {
  const polys = [];
  for (const code of codes) for (const polygon of raw10.get(code)) polys.push({ code, polygon });
  const parent = polys.map((_, i) => i);
  const find = (i) => {
    while (parent[i] !== i) i = parent[i] = parent[parent[i]];
    return i;
  };
  const firstAt = new Map();
  polys.forEach(({ polygon }, i) => {
    for (const ring of polygon) {
      for (const [lng, lat] of ring) {
        const key = `${lng},${lat}`;
        if (firstAt.has(key)) parent[find(i)] = find(firstAt.get(key));
        else firstAt.set(key, i);
      }
    }
  });

  const km2 = (polygon) => fixWinding([polygon]).reduce((sum, p) => sum + polygonKm2(p), 0);
  const largest = (code) => {
    let best = -1, at = -1;
    polys.forEach((p, i) => {
      if (p.code !== code) return;
      const a = km2(p.polygon);
      if (a > best) [best, at] = [a, i];
    });
    return find(at);
  };
  const mainland = new Set();
  for (const [name, anchors] of Object.entries(MAINLANDS)) {
    const roots = new Set(anchors.map(largest));
    if (roots.size !== 1) throw new Error(`${name} does not join up: ${anchors} lie on ${roots.size} landmasses`);
    const [root] = roots;
    if (mainland.has(root)) throw new Error(`${name} joins up with another mainland`);
    mainland.add(root);
  }
  return new Set(polys.filter((_, i) => mainland.has(find(i))).map((p) => p.code));
}

/** The answer codes that are small island nations, by the rule above. */
function smallIslandNations(km) {
  log('finding small island nations');
  const onMainland = mainlandCodes();
  const at = new Map(codes.map((code, i) => [code, i]));
  const small = new Set();
  const kept = [];
  for (const code of codes) {
    if (!answers.has(code) || onMainland.has(code)) continue;
    const area = fixed10.get(code).reduce((sum, p) => sum + polygonKm2(p), 0);
    const neighbour = codes.find((other) => other !== code && answers.has(other) && km[at.get(code)][at.get(other)] === 0);
    if (area >= SMALL_ISLAND_MAX_KM2) kept.push(`${code} (${Math.round(area).toLocaleString('en')} km²)`);
    else if (neighbour) kept.push(`${code} (borders ${neighbour})`);
    else small.add(code);
  }
  log(`  ${small.size} small island nations; islands only but kept: ${kept.join(', ')}`);
  return small;
}

// ---------------------------------------------------------------- world.json

/**
 * Which geometry to draw. 50m is hand-generalised and a fraction of the size,
 * so it is the default. 10m is used where 50m has no shape at all (Tuvalu,
 * Gibraltar) and where 50m leaves out the island the capital stands on
 * (Pitcairn Island, Grand Turk) — the capitals mode would otherwise put a dot
 * in empty sea. Those are all islands or enclaves too small for the extra
 * detail to matter for size.
 */
function displayPolygons(code) {
  const coarse = fixed50.get(code) ?? [];
  if (!coarse.length) return { polygons: fixed10.get(code), why: 'missing from 50m' };
  const cap = CATALOG[code].capital;
  if (cap && kmToPolygons(coarse, cap[2], cap[1]) > 25 && kmToPolygons(fixed10.get(code), cap[2], cap[1]) <= 25) {
    return { polygons: fixed10.get(code), why: `50m lacks ${cap[0]}'s island` };
  }
  return { polygons: coarse };
}

/** Rewrites arc references so arcs nothing uses any more are dropped. */
function pruneArcs(topo) {
  const used = new Map();
  const walk = (arcs) => arcs.map((a) => (Array.isArray(a) ? walk(a) : (() => {
    const i = a < 0 ? ~a : a;
    if (!used.has(i)) used.set(i, used.size);
    return a < 0 ? ~used.get(i) : used.get(i);
  })()));
  for (const g of topo.objects.countries.geometries) g.arcs = walk(g.arcs);
  const arcs = new Array(used.size);
  for (const [old, now] of used) arcs[now] = topo.arcs[old];
  topo.arcs = arcs;
}

/**
 * Puts a vertex into every ring segment that another piece's vertex lies on.
 *
 * The globe draws its coastline as the arcs only one country uses, so a land
 * border must come out as one arc shared by both sides. topojson only shares
 * runs of identical points. Where three countries meet and one side lacks the
 * junction vertex (Algeria's straight border has no point where Morocco and
 * Western Sahara meet across it), the two sides become separate one-country
 * arcs, and the globe draws a stretch of border as if it were coast.
 *
 * The test runs on the grid topojson is about to quantise to (the same bbox,
 * the same rounding), so it judges the points topojson will actually store. A
 * vertex of another piece that falls strictly inside a segment, within one
 * grid unit of it, is inserted in order along the segment. The inserted points
 * are copies of that piece's own coordinates, so they round to the same grid
 * point as the original and the bbox does not change. Pieces of one country
 * count as different pieces, so mergeArcs can still join them seamlessly.
 * check-data.mjs asserts the result: no border left drawn as coast.
 */
function splitTJunctions(pieces) {
  let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
  for (const piece of pieces) {
    for (const ring of piece.geometry.coordinates) {
      for (const [x, y] of ring) {
        if (x < x0) x0 = x;
        if (x > x1) x1 = x;
        if (y < y0) y0 = y;
        if (y > y1) y1 = y;
      }
    }
  }
  // topojson-server's prequantize, to the letter.
  const kx = x1 - x0 ? (QUANTIZATION - 1) / (x1 - x0) : 1;
  const ky = y1 - y0 ? (QUANTIZATION - 1) / (y1 - y0) : 1;
  const gx = (p) => Math.round((p[0] - x0) * kx);
  const gy = (p) => Math.round((p[1] - y0) * ky);

  // Every vertex, bucketed by grid cell.
  const CELL = 256;
  const cellKey = (cx, cy) => cx * 1024 + cy;
  const cells = new Map();
  pieces.forEach((piece, owner) => {
    for (const ring of piece.geometry.coordinates) {
      for (const p of ring) {
        const v = { owner, x: gx(p), y: gy(p), p };
        const key = cellKey(Math.floor(v.x / CELL), Math.floor(v.y / CELL));
        if (!cells.has(key)) cells.set(key, []);
        cells.get(key).push(v);
      }
    }
  });

  let inserted = 0;
  const touched = new Set();
  pieces.forEach((piece, owner) => {
    piece.geometry.coordinates = piece.geometry.coordinates.map((ring) => {
      const out = [ring[0]];
      for (let i = 1; i < ring.length; i++) {
        const a = ring[i - 1];
        const b = ring[i];
        const ax = gx(a), ay = gy(a), bx = gx(b), by = gy(b);
        const dx = bx - ax, dy = by - ay;
        const len2 = dx * dx + dy * dy;
        // A segment more than half the map wide is a short hop across the
        // antimeridian (Fiji, Chukotka), not a line across the whole grid.
        if (len2 > 1 && Math.abs(dx) * 2 < QUANTIZATION) {
          const len = Math.sqrt(len2);
          const found = new Map();
          for (let cx = Math.floor((Math.min(ax, bx) - 1) / CELL); cx <= Math.floor((Math.max(ax, bx) + 1) / CELL); cx++) {
            for (let cy = Math.floor((Math.min(ay, by) - 1) / CELL); cy <= Math.floor((Math.max(ay, by) + 1) / CELL); cy++) {
              for (const v of cells.get(cellKey(cx, cy)) ?? []) {
                if (v.owner === owner || (v.x === ax && v.y === ay) || (v.x === bx && v.y === by)) continue;
                const t = ((v.x - ax) * dx + (v.y - ay) * dy) / len2;
                if (t <= 0 || t >= 1) continue;
                if (Math.abs(dx * (v.y - ay) - dy * (v.x - ax)) / len > 1) continue;
                const key = `${v.x},${v.y}`;
                if (!found.has(key)) found.set(key, { t, p: v.p });
              }
            }
          }
          if (found.size) {
            const extra = [...found.values()].sort((u, w) => u.t - w.t);
            for (const { p } of extra) out.push([p[0], p[1]]);
            inserted += extra.length;
            touched.add(piece.id);
          }
        }
        out.push(b);
      }
      return out;
    });
  });
  if (inserted) log(`  ${inserted} border points shared with a neighbour that lacked them (${[...touched].sort().join(', ')})`);
}

function worldTopology() {
  log('building display topology');
  // One feature per polygon first, so that pieces folded into a country (the
  // Cyprus bases, Crimea, southern Western Sahara) can then be merged with
  // mergeArcs — which drops the border between them instead of drawing a
  // line through the middle of the country.
  const pieces = [];
  for (const code of codes) {
    const { polygons, why } = displayPolygons(code);
    if (!polygons.length) throw new Error(`${code}: nothing to draw`);
    if (why) log(`  ${code} drawn from 10m (${why})`);
    for (const polygon of polygons) {
      pieces.push({ type: 'Feature', id: code, properties: {}, geometry: { type: 'Polygon', coordinates: polygon } });
    }
  }
  splitTJunctions(pieces);
  const topo = topology({ countries: { type: 'FeatureCollection', features: pieces } }, QUANTIZATION);
  const byId = new Map(codes.map((code) => [code, []]));
  for (const g of topo.objects.countries.geometries) byId.get(g.id).push(g);
  topo.objects.countries.geometries = codes.map((code) => {
    const merged = mergeArcs(topo, byId.get(code));
    const geometry = merged.arcs.length === 1
      ? { type: 'Polygon', arcs: merged.arcs[0] }
      : { type: 'MultiPolygon', arcs: merged.arcs };
    return { ...geometry, id: code, properties: { name: CATALOG[code].name } };
  });
  pruneArcs(topo);
  delete topo.bbox; // optional in TopoJSON, and the client never reads it

  // Quantisation and merging must not have collapsed or inverted anything.
  for (const f of feature(topo, topo.objects.countries).features) {
    const a = geoArea(f);
    if (!(a > 0 && a < 2 * Math.PI)) throw new Error(`${f.id}: area ${a} after quantisation`);
  }
  return topo;
}

// ---------------------------------------------------------------- write

function writeFile(file, text) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text);
  log(`wrote ${path.relative(REPO, file)} (${(Buffer.byteLength(text) / 1024).toFixed(0)} KiB)`);
}

const km = await distanceMatrix();
const entries = countryEntries(smallIslandNations(km));
const world = worldTopology();

writeFile(OUTPUTS.countries, `[\n${entries.map((e) => JSON.stringify(e)).join(',\n')}\n]\n`);
writeFile(OUTPUTS.distances,
  `{"codes":${JSON.stringify(codes)},\n"km":[\n${km.map((row) => JSON.stringify(row)).join(',\n')}\n]}\n`);
writeFile(OUTPUTS.world, JSON.stringify(world));
log(`${entries.length} countries, ${entries.filter((e) => e.answer).length} answers, `
  + `${entries.filter((e) => e.smallIsland).length} of them small island nations`);

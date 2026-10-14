// Name them all, round 2: "Study what you missed" (#/all/study, #/all/<region>/study).
//
// The page studies the player's newest finished run in a region, as the server
// works it out (GET /rollcall/study): the countries missed, most worth learning
// first. It shows the first STUDY_PAGE of them, then "Show all N". Each card says
// WHERE the country is (a compass point in its continent, and its borders or its
// nearest neighbour), its capital, the closest country on the player's list, how
// often people get it, and the player's own record with it, with a button to
// request a name change. Tapping a card flies the globe to it: the country solid
// and labelled, its bordering countries in a soft colour, the rest of the missed
// ones hatched as on the end card.
//
// The words are built by study-logic.js (pure) in the page's language, so a change
// of language redraws everything from the same data. main.js routes here and draws
// it from render(), like Name them all.

import { api } from "./api.js";
import { t, tn, has, getLang, locale, translateText } from "./i18n.js";
import { placeName, nameInSentence, capitalName } from "./names.js";
import { flagEmoji, formatDistance, formatInteger, regionLabel } from "./format.js";
import { ANSWER_COLOR } from "./scale.js";
import { el, icon } from "./ui.js";
import { isStudy } from "./modes.js";
import {
    STUDY_PAGE, visibleMissed, whereSentence, nearestNamedText, howOftenText, yourRecordText,
} from "./study-logic.js";

/** The missed countries on the globe: the end card's hatch. */
const MISSED_COLOR = "#86efac";
/** The bordering countries of the one being studied: soft, so the country itself stands out. */
const NEIGHBOUR_COLOR = "#fde68a";
/** How close the globe comes to a country being studied. */
const STUDY_ZOOM = 2.4;

function isAuthError(error) {
    return !!error && (error.code === "signed-out" || error.code === "forbidden");
}

export class StudyView {
    /**
     * hooks, from main.js:
     *   globe                             the globe (setGame, flyTo, focusRegion, pulse, ready)
     *   route()                           the route on screen
     *   byCode()                          the country list by code
     *   units()                           "km" or "mi"
     *   sessionExpired(code)
     *   requestButton(code, source, ctx)  the pencil button (requests.js), ctx { region, runId }
     *   playAgain(region)                 a new Name them all run there: the setup card
     *   hideStageHint()
     */
    constructor(hooks) {
        this.h = hooks;
        this.slots = {};        // region -> { data: undefined (not yet) | null (none, 204) | study, error, loading }
        this.selected = {};     // region -> the code on the globe, or null
        this.expanded = {};     // region -> "Show all" was pressed (for that run)
        const id = (name) => document.getElementById(name);
        this.dom = {
            panel: id("study-panel"),
            status: id("study-status"),
            head: id("study-head"),
            title: id("study-title"),
            summary: id("study-summary"),
            hint: id("study-hint"),
            list: id("study-list"),
            more: id("study-more"),
            again: id("study-again"),
            againLabel: id("study-again-label"),
        };
        this.dom.more.addEventListener("click", () => this.showAll());
        this.dom.again.addEventListener("click", () => {
            const region = this.region();
            if (region) this.h.playAgain(region);
        });
    }

    /** The region on screen, or null when another view is. */
    region() {
        const route = this.h.route();
        return isStudy(route) ? route.region : null;
    }

    onScreen(region) {
        return !!region && this.region() === region;
    }

    data(region) {
        const slot = this.slots[region];
        return slot ? slot.data : undefined;
    }

    nameOf(code) {
        const country = this.h.byCode().get(code);
        return country ? placeName(country) : code;
    }

    /** A name for inside a sentence: "bordering the United States" (headings and labels use nameOf). */
    inSentence(code) {
        const country = this.h.byCode().get(code);
        return country ? nameInSentence(country) : code;
    }

    distance(km) {
        return formatDistance(km, this.h.units());
    }

    // ------------------------------------------------------------------ loading

    /** Called after main.js has drawn the route: fetches the study (what is on hand shows meanwhile). */
    async enter(route) {
        await this.load(route.region);
    }

    load(region) {
        const slot = this.slots[region] || (this.slots[region] = { data: undefined, error: null, loading: null });
        if (slot.loading) return slot.loading;
        slot.error = null;
        slot.loading = (async () => {
            try {
                const data = await api.rollCallStudy(region);
                const before = slot.data;
                slot.data = data && Array.isArray(data.missed) ? data : null;
                // Another run since: what was picked and opened belonged to the old one.
                if (!before || !slot.data || before.runId !== slot.data.runId) {
                    this.selected[region] = null;
                    this.expanded[region] = false;
                }
            } catch (error) {
                slot.error = error;
                if (isAuthError(error)) this.h.sessionExpired(error.code);
            } finally {
                slot.loading = null;
            }
            if (this.onScreen(region)) this.redraw({ aim: true });
        })();
        if (this.onScreen(region)) this.render();
        return slot.loading;
    }

    /** A run in that region just ended: a study on hand is of an older one. */
    forget(region) {
        delete this.slots[region];
        this.selected[region] = null;
        this.expanded[region] = false;
    }

    /** Start again from nothing (the page came back from the back/forward cache). */
    reset() {
        this.slots = {};
        this.selected = {};
        this.expanded = {};
    }

    // ------------------------------------------------------------------ drawing

    redraw({ aim = false } = {}) {
        if (!this.region()) return;
        this.render();
        this.syncGlobe();
        if (aim) this.aimGlobe();
    }

    render() {
        const region = this.region();
        if (!region) return;
        const d = this.dom;
        const slot = this.slots[region] || null;
        const data = slot ? slot.data : undefined;
        const failed = data === undefined && !!slot && !!slot.error;
        const loading = data === undefined && !failed;

        d.status.hidden = !(loading || failed);
        if (failed) {
            const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, t("common.tryAgain"));
            retry.addEventListener("click", () => this.load(region));
            d.status.replaceChildren(el("span", { text: `${t("study.failed")} ` }), retry);
        } else if (loading) {
            d.status.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }), ` ${t("common.loading")}`);
        }

        d.head.hidden = data === undefined;
        const missed = data ? data.missed : [];
        if (data === null) {
            d.summary.textContent = t("study.none");
        } else if (data) {
            d.summary.textContent = this.summaryLine(region, data);
        }
        d.hint.hidden = !missed.length;
        // "Play" when there is no game yet to play again.
        translateText(d.againLabel, data === null ? "common.play" : "rollcall.end.again");
        d.again.hidden = data === undefined;

        const shown = visibleMissed(missed, !!this.expanded[region]);
        const selected = this.selected[region] || null;
        const ctx = { region, runId: data ? data.runId : null };
        d.list.hidden = !shown.length;
        d.list.replaceChildren(...shown.map((entry) => this.card(entry, entry.code === selected, ctx)));
        d.more.hidden = missed.length <= shown.length;
        if (!d.more.hidden) d.more.textContent = tn("study.showAll", missed.length);
    }

    /** "Your last game in Europe: you named 30 of 46 and missed 16 (Oct 14)." */
    summaryLine(region, data) {
        if (!data.missedCount) return t("study.allNamed", { total: formatInteger(data.total) });
        const params = {
            named: formatInteger(data.namedCount),
            total: formatInteger(data.total),
            missed: formatInteger(data.missedCount),
            when: this.when(data.finishedAt),
        };
        // Plurals by how many were missed: Spanish says "te faltó 1", "te faltaron 16".
        if (region === "world") return tn("study.summary.world", data.missedCount, params);
        const where = has(`rollcall.in.${region}`) ? t(`rollcall.in.${region}`) : regionLabel(region);
        return tn("study.summary.region", data.missedCount, { ...params, region: where });
    }

    when(instant) {
        const date = new Date(instant);
        if (!instant || Number.isNaN(date.getTime())) return "—";
        return date.toLocaleDateString(locale(getLang()), { month: "short", day: "numeric" });
    }

    /** One missed country. */
    card(entry, selected, ctx) {
        const country = this.h.byCode().get(entry.code);
        const name = this.nameOf(entry.code);
        const words = { nameOf: (code) => this.inSentence(code), distance: (km) => this.distance(km) };
        const capital = country ? capitalName(country) : "";
        const facts = [
            ["bi-signpost-split", "gl-study-where", whereSentence(entry, words)],
            ["bi-bank2", "gl-study-capital", capital ? t("end.capital", { name: capital }) : null],
            ["bi-pin-map", "gl-study-near", nearestNamedText(entry, words)],
            ["bi-people", "gl-study-often", howOftenText(entry)],
            ["bi-person", "gl-study-yours", yourRecordText(entry)],
        ].filter(([, , text]) => !!text);
        const show = el("button", {
            type: "button",
            class: "gl-study-show",
            "aria-pressed": selected ? "true" : "false",
            title: t("study.show", { name: this.inSentence(entry.code) }),
        },
            el("span", { class: "gl-study-flag", "aria-hidden": "true", text: flagEmoji(country ? country.iso2 : null) }),
            el("span", { class: "gl-study-name", text: name }),
            el("span", { class: "sr-only", text: ` ${t("study.showSr")}` }),
            el("i", { class: "bi bi-crosshair gl-study-aim", "aria-hidden": "true" }));
        show.addEventListener("click", () => this.select(entry.code));
        const li = el("li", { class: selected ? "gl-study-card is-selected" : "gl-study-card", "data-code": entry.code },
            el("div", { class: "gl-study-top" },
                el("h3", { class: "gl-study-heading" }, show),
                this.h.requestButton(entry.code, "study", ctx)),
            el("ul", { class: "gl-study-facts" }, facts.map(([iconName, cls, text]) =>
                el("li", { class: cls }, icon(`${iconName} gl-study-icon`), el("span", { text })))));
        // A tap anywhere on the card does what its heading's button does.
        li.addEventListener("click", (e) => {
            const target = e.target;
            if (target && typeof target.closest === "function" && target.closest("button, a")) return;
            this.select(entry.code);
        });
        return li;
    }

    /** "Show all N": the rest of the list, with focus on the first one it adds. */
    showAll() {
        const region = this.region();
        if (!region) return;
        this.expanded[region] = true;
        this.render();
        const cards = this.dom.list.children;
        const first = cards[STUDY_PAGE] || cards[cards.length - 1];
        const button = first ? first.querySelector(".gl-study-show") : null;
        if (button) button.focus({ preventScroll: true });
    }

    /** A card tapped: the globe flies to the country and shows it with its neighbours. */
    select(code) {
        const region = this.region();
        const data = region ? this.data(region) : null;
        const entry = data ? data.missed.find((m) => m.code === code) : null;
        if (!entry) return;
        this.selected[region] = code;
        // In place, so the button pressed keeps focus.
        for (const li of this.dom.list.children) {
            const on = li.getAttribute("data-code") === code;
            li.classList.toggle("is-selected", on);
            const b = li.querySelector(".gl-study-show");
            if (b) b.setAttribute("aria-pressed", on ? "true" : "false");
        }
        this.syncGlobe();
        this.aimGlobe();
        const point = this.pointOf(entry);
        if (point) this.h.globe.pulse(point, ANSWER_COLOR);
    }

    pointOf(entry) {
        if (entry && Number.isFinite(entry.lng) && Number.isFinite(entry.lat)) return [entry.lng, entry.lat];
        return entry ? this.h.globe.centreOf(entry.code) : null;
    }

    selectedEntry(region) {
        const data = this.data(region);
        const code = this.selected[region];
        return data && code ? data.missed.find((m) => m.code === code) || null : null;
    }

    // ------------------------------------------------------------------ the globe

    /**
     * The missed countries hatched; the one being studied solid and labelled (drawn
     * as a found answer), and its bordering countries in a soft colour.
     */
    syncGlobe() {
        const region = this.region();
        if (!region) return;
        const data = this.data(region);
        const missed = data ? data.missed : [];
        const entry = this.selectedEntry(region);
        const borders = new Set(entry ? (entry.nearby || []).filter((n) => n && n.km === 0).map((n) => n.code) : []);
        const guesses = [];
        for (const m of missed) {
            if ((entry && m.code === entry.code) || borders.has(m.code)) continue;
            guesses.push({ code: m.code, color: MISSED_COLOR, capital: null, style: "missed" });
        }
        for (const code of borders) guesses.push({ code, color: NEIGHBOUR_COLOR, capital: null });
        this.h.globe.setGame({
            kind: "country",
            region,
            guesses,
            latestCode: null,
            answer: entry ? { code: entry.code, name: this.nameOf(entry.code), lat: entry.lat, lng: entry.lng } : null,
            status: entry ? "WON" : "FINISHED",
        });
        if (guesses.length || entry) this.h.hideStageHint();
    }

    aimGlobe() {
        const region = this.region();
        const globe = this.h.globe;
        if (!region || !globe.ready) return;
        const entry = this.selectedEntry(region);
        const point = entry ? this.pointOf(entry) : null;
        if (point) globe.flyTo(point, { zoom: STUDY_ZOOM });
        else globe.focusRegion(region);
    }

    /** The globe's tooltip: the country studied, its neighbours, and the missed ones (the run is over). */
    tipFor(code) {
        const region = this.region();
        const data = region ? this.data(region) : null;
        if (!data) return null;
        const country = this.h.byCode().get(code);
        const base = { iso2: country ? country.iso2 : null, title: this.nameOf(code) };
        const entry = this.selectedEntry(region);
        if (entry && entry.code === code) return { ...base, detail: t("study.tip.selected") };
        if (entry && (entry.nearby || []).some((n) => n && n.km === 0 && n.code === code)) {
            return { ...base, detail: t("study.tip.borders", { name: this.inSentence(entry.code) }) };
        }
        if (data.missed.some((m) => m.code === code)) return { ...base, detail: t("rollcall.tip.missed") };
        return null;
    }
}

// The page's screens and game slots, as plain data: the hash routes, the practice
// presets on the home screen, and the words each one shows (from i18n.js, in the
// page's language at the moment they are read).
//
// Pure: no DOM, no fetch, so the node harness tests the routing rules directly.
//
//   #/                 home: pick a game
//   #/classic          today's classic daily
//   #/hard             today's hard daily (blind globe, no suggestions)
//   #/play/<preset>    a practice game; preset is one of PRESET_NAMES
//   #/all              Name them all, the whole world
//   #/all/<region>     Name them all in one continent (africa, americas, asia,
//                      europe, oceania)
//   #/all/study        study what you missed in your last whole-world run, and
//   #/all/<region>/study  in your last run in that continent (study.js)
//   #/requests         the admin's name-change requests; anyone else goes home (requests.js)
//
// A practice "slot" is (kind, region, variant). Each preset is one slot, so every
// preset keeps its own game in progress, and starting a new one only abandons the
// game in that same slot. Name them all keeps one run per region the same way.

import { regionLabel } from "./format.js";
import { t, tn } from "./i18n.js";
import { ROLLCALL_REGIONS } from "./rollcall.js";

export const VARIANTS = ["classic", "hard"];

export const HOME = Object.freeze({ name: "home", key: null });

/**
 * A preset whose words are read in the page's language whenever they are shown:
 * PRESETS.europe.label is "Europe only" or "Solo Europa" (i18n keys
 * preset.<name>.label and preset.<name>.sub).
 */
function preset(name, fields) {
    return Object.defineProperties({ ...fields }, {
        label: { enumerable: true, get: () => t(`preset.${name}.label`) },
        sub: { enumerable: true, get: () => t(`preset.${name}.sub`) },
    });
}

/**
 * The practice presets. anyRegion: the slot resumes a game of its kind and variant
 * in whatever region it was started (Capitals games from before presets existed
 * could be regional), while new games use region.
 */
export const PRESETS = {
    world: preset("world", { kind: "country", region: "world", variant: "classic", icon: "bi-infinity", accent: "unlimited" }),
    europe: preset("europe", { kind: "country", region: "europe", variant: "classic", icon: "bi-geo-alt-fill", accent: "unlimited" }),
    mainland: preset("mainland", { kind: "country", region: "mainland", variant: "classic", icon: "bi-map-fill", accent: "unlimited" }),
    capitals: preset("capitals", { kind: "capital", region: "world", variant: "classic", icon: "bi-bank2", accent: "capitals", anyRegion: true }),
    hard: preset("hard", { kind: "country", region: "world", variant: "hard", icon: "bi-eye-slash-fill", accent: "hard" }),
    africa: preset("africa", { kind: "country", region: "africa", variant: "classic", icon: "bi-geo-alt-fill", accent: "unlimited" }),
    americas: preset("americas", { kind: "country", region: "americas", variant: "classic", icon: "bi-geo-alt-fill", accent: "unlimited" }),
    asia: preset("asia", { kind: "country", region: "asia", variant: "classic", icon: "bi-geo-alt-fill", accent: "unlimited" }),
    oceania: preset("oceania", { kind: "country", region: "oceania", variant: "classic", icon: "bi-geo-alt-fill", accent: "unlimited" }),
};

export const PRESET_NAMES = Object.keys(PRESETS);

/** The big rows on the home screen, then the compact row of other regions. */
export const FEATURED = ["world", "europe", "mainland", "capitals", "hard"];
export const MORE_REGIONS = ["africa", "americas", "asia", "oceania"];

/** The dailies' names, in the page's language: DAILY_LABELS.hard is "Hard" or "Difícil". */
export const DAILY_LABELS = Object.freeze({
    get classic() { return t("variant.classic"); },
    get hard() { return t("variant.hard"); },
});

/** What a practice board (and its line in the stats sheet) is called: kind is country, capital or hard. */
export function practiceName(kind) {
    return t(`practice.${kind === "capital" || kind === "hard" ? kind : "country"}`);
}

/** Today's daily of one variant. key names its game in the page's state. */
export function dailyRoute(variant) {
    return Object.freeze({ name: "daily", variant, key: variant });
}

/** A practice preset. */
export function playRoute(preset) {
    return Object.freeze({ name: "play", preset, key: `play/${preset}` });
}

/** Name them all in a region ("world" or a continent; anything else is the world). */
export function rollCallRoute(region = "world") {
    const where = ROLLCALL_REGIONS.includes(region) ? region : "world";
    return Object.freeze({ name: "all", region: where, key: `all/${where}` });
}

/** Whether a route is Name them all. */
export function isRollCall(route) {
    return !!route && route.name === "all";
}

/** Study what you missed in the last finished Name them all run in a region. */
export function studyRoute(region = "world") {
    const where = ROLLCALL_REGIONS.includes(region) ? region : "world";
    return Object.freeze({ name: "study", region: where, key: `study/${where}` });
}

/** The admin's name-change requests. */
export const REQUESTS = Object.freeze({ name: "requests", key: "requests" });

export function isStudy(route) {
    return !!route && route.name === "study";
}

export function isRequests(route) {
    return !!route && route.name === "requests";
}

/** The pages that are not a game: the study page and the requests list. */
export function isPage(route) {
    return isStudy(route) || isRequests(route);
}

/**
 * A location hash -> a route, or null for one this page does not know (the caller
 * goes home). Trailing slashes and case are forgiven; "#", "#/" and "" are home.
 */
export function parseRoute(hash) {
    const path = String(hash == null ? "" : hash)
        .replace(/^#/, "")
        .replace(/^\/+|\/+$/g, "")
        .toLowerCase();
    if (path === "") return HOME;
    if (VARIANTS.includes(path)) return dailyRoute(path);
    const m = /^play\/([a-z]+)$/.exec(path);
    if (m && Object.prototype.hasOwnProperty.call(PRESETS, m[1])) return playRoute(m[1]);
    // "#/all/world" is forgiven too, though "#/all" is how the world is written.
    const all = /^all(?:\/([a-z]+))?$/.exec(path);
    if (all && all[1] === "study") return studyRoute("world");
    if (all && (!all[1] || ROLLCALL_REGIONS.includes(all[1]))) return rollCallRoute(all[1] || "world");
    const study = /^all\/([a-z]+)\/study$/.exec(path);
    if (study && ROLLCALL_REGIONS.includes(study[1])) return studyRoute(study[1]);
    if (path === "requests") return REQUESTS;
    return null;
}

/** The hash for a route: parseRoute(routeHash(r)) is r again. */
export function routeHash(route) {
    if (!route || route.name === "home") return "#/";
    if (route.name === "daily") return `#/${route.variant}`;
    if (route.name === "all") return route.region === "world" ? "#/all" : `#/all/${route.region}`;
    if (route.name === "study") return route.region === "world" ? "#/all/study" : `#/all/${route.region}/study`;
    if (route.name === "requests") return "#/requests";
    return `#/play/${route.preset}`;
}

export function sameRoute(a, b) {
    return !!a && !!b && a.name === b.name && a.key === b.key;
}

/**
 * What a game route plays: { daily, rollcall, kind, region, variant, preset,
 * anyRegion }. Home, the study page and the requests list have none. rollcall is
 * true for Name them all, which is not a guessing game at all: main.js hands those
 * routes to rollcall-view.js.
 */
export function routeInfo(route) {
    if (!route || route.name === "home" || isPage(route)) return null;
    if (route.name === "daily") {
        return { daily: true, rollcall: false, kind: "country", region: "world", variant: route.variant, preset: null, anyRegion: false };
    }
    if (route.name === "all") {
        return { daily: false, rollcall: true, kind: "country", region: route.region, variant: "classic", preset: null, anyRegion: false };
    }
    const p = PRESETS[route.preset];
    return { daily: false, rollcall: false, kind: p.kind, region: p.region, variant: p.variant, preset: route.preset, anyRegion: !!p.anyRegion };
}

/** Hard is blind and has no suggestions, daily or practice. */
export function isHard(route) {
    const info = routeInfo(route);
    return !!info && info.variant === "hard";
}

/** The colour family a route uses (body[data-accent]). */
export function accentFor(route) {
    if (!route || route.name === "home") return "home";
    if (route.name === "daily") return route.variant === "hard" ? "hard" : "classic";
    if (route.name === "all" || isPage(route)) return "rollcall";
    return PRESETS[route.preset].accent;
}

/**
 * Which preset an in-progress game belongs to, from its kind, region and variant,
 * or null. A missing variant is classic (games from before Hard existed).
 */
export function presetFor(game) {
    if (!game) return null;
    const variant = game.variant || "classic";
    const kind = game.kind || "country";
    const region = game.region || "world";
    // Hard practice resumes the world slot only; a Hard game in another region can
    // only come from the API, and this page has no preset that would resume it.
    if (variant === "hard") return kind === "country" && region === "world" ? "hard" : null;
    if (kind === "capital") return "capitals";
    const preset = PRESET_NAMES.find((name) => {
        const p = PRESETS[name];
        return p.kind === "country" && p.variant === "classic" && p.region === region;
    });
    return preset || null;
}

/**
 * The badge in the game view: "Classic · Daily #12", "Hard · Daily #3",
 * "Europe only", "Capitals · Asia" (a regional Capitals game resumed), "Name them
 * all · Europe"; in Spanish "Clásico · Diario #12", "Solo Europa", "Capitales ·
 * Asia", "Nómbralos todos · Europa".
 */
export function modeLabel(route, { puzzle = null, region = null } = {}) {
    if (!route || route.name === "home") return "";
    if (route.name === "all") {
        const name = t("rollcall.name");
        return route.region === "world" ? name : t("mode.regional", { label: name, region: regionLabel(route.region) });
    }
    if (route.name === "study") {
        const name = t("study.badge");
        return route.region === "world" ? name : t("mode.regional", { label: name, region: regionLabel(route.region) });
    }
    if (route.name === "requests") return t("requests.badge");
    if (route.name === "daily") {
        const name = DAILY_LABELS[route.variant];
        return puzzle != null ? t("mode.daily", { name, n: puzzle }) : t("mode.dailyPlain", { name });
    }
    const p = PRESETS[route.preset];
    if (p.anyRegion && region && region !== p.region) return t("mode.regional", { label: p.label, region: regionLabel(region) });
    return p.label;
}

/** The icon for a route's badge. */
export function modeIcon(route) {
    if (!route || route.name === "home") return "bi-globe-americas";
    if (route.name === "daily") return route.variant === "hard" ? "bi-eye-slash-fill" : "bi-calendar2-check";
    if (route.name === "all") return ROLLCALL_ICON;
    if (route.name === "study") return STUDY_ICON;
    if (route.name === "requests") return "bi-pencil-square";
    return PRESETS[route.preset].icon;
}

/** Name them all's icon: its home row, badge and help line. */
export const ROLLCALL_ICON = "bi-list-check";

/** The study page's icon: its badge and the buttons that lead to it. */
export const STUDY_ICON = "bi-mortarboard";

/**
 * A daily card's status line from a home summary ({ status, guessCount }):
 * "Play", "In progress · 3 guesses", "Solved in 4 ✓", "Gave up" ("Jugar",
 * "En curso · 3 intentos", "Resuelto en 4 ✓", "Te rendiste").
 * A game that is PLAYING with no guesses yet is still "Play".
 */
export function statusText(summary) {
    const status = summary && summary.status;
    const n = summary && Number.isFinite(summary.guessCount) ? summary.guessCount : 0;
    if (status === "WON") return t("status.won", { n });
    if (status === "GAVE_UP") return t("status.gaveUp");
    if (status === "PLAYING" && n > 0) return tn("status.playing", n);
    return t("status.play");
}

/** The CSS state a daily card's chip takes: new | playing | won | gaveup. */
export function statusKind(summary) {
    const status = summary && summary.status;
    if (status === "WON") return "won";
    if (status === "GAVE_UP") return "gaveup";
    if (status === "PLAYING" && summary.guessCount > 0) return "playing";
    return "new";
}

/** A preset's chip when a game is in progress: "Resume · 3 guesses" (just "Resume" before a guess). */
export function resumeText(guessCount) {
    const n = Number(guessCount) || 0;
    return n > 0 ? tn("status.resumeCount", n) : t("status.resume");
}

/**
 * Name them all's chip on the home row: "Resume · 57 named" for a world run in
 * progress (just "Resume" while nothing is named), else "Best 162/197", else
 * nothing. resume is the run's namedCount or null; best is { named, total } or null.
 */
export function rollCallChipText({ resume = null, best = null } = {}) {
    if (resume != null) {
        const n = Number(resume) || 0;
        return n > 0 ? tn("rollcall.resume", n) : t("status.resume");
    }
    if (best) return t("rollcall.best", { named: best.named, total: best.total });
    return "";
}

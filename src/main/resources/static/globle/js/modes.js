// The page's screens and game slots, as plain data: the hash routes, the practice
// presets on the home screen, and the words each one shows.
//
// Pure: no DOM, no fetch, so the node harness tests the routing rules directly.
//
//   #/                 home: pick a game
//   #/classic          today's classic daily
//   #/hard             today's hard daily (blind globe, no suggestions)
//   #/play/<preset>    a practice game; preset is one of PRESET_NAMES
//
// A practice "slot" is (kind, region, variant). Each preset is one slot, so every
// preset keeps its own game in progress, and starting a new one only abandons the
// game in that same slot.

import { regionLabel, plural } from "./format.js";

export const VARIANTS = ["classic", "hard"];

export const HOME = Object.freeze({ name: "home", key: null });

/**
 * The practice presets. anyRegion: the slot resumes a game of its kind and variant
 * in whatever region it was started (Capitals games from before presets existed
 * could be regional), while new games use region.
 */
export const PRESETS = {
    world: { kind: "country", region: "world", variant: "classic", label: "Unlimited", sub: "Any country, any time", icon: "bi-infinity", accent: "unlimited" },
    europe: { kind: "country", region: "europe", variant: "classic", label: "Europe only", sub: "Countries in Europe", icon: "bi-geo-alt-fill", accent: "unlimited" },
    mainland: { kind: "country", region: "mainland", variant: "classic", label: "Mainland only", sub: "No tiny island nations", icon: "bi-map-fill", accent: "unlimited" },
    capitals: { kind: "capital", region: "world", variant: "classic", label: "Capitals", sub: "Find the capital city", icon: "bi-bank2", accent: "capitals", anyRegion: true },
    hard: { kind: "country", region: "world", variant: "hard", label: "Hard practice", sub: "Blind globe, no suggestions", icon: "bi-eye-slash-fill", accent: "hard" },
    africa: { kind: "country", region: "africa", variant: "classic", label: "Africa only", sub: "Countries in Africa", icon: "bi-geo-alt-fill", accent: "unlimited" },
    americas: { kind: "country", region: "americas", variant: "classic", label: "Americas only", sub: "Countries in the Americas", icon: "bi-geo-alt-fill", accent: "unlimited" },
    asia: { kind: "country", region: "asia", variant: "classic", label: "Asia only", sub: "Countries in Asia", icon: "bi-geo-alt-fill", accent: "unlimited" },
    oceania: { kind: "country", region: "oceania", variant: "classic", label: "Oceania only", sub: "Countries in Oceania", icon: "bi-geo-alt-fill", accent: "unlimited" },
};

export const PRESET_NAMES = Object.keys(PRESETS);

/** The big rows on the home screen, then the compact row of other regions. */
export const FEATURED = ["world", "europe", "mainland", "capitals", "hard"];
export const MORE_REGIONS = ["africa", "americas", "asia", "oceania"];

export const DAILY_LABELS = { classic: "Classic", hard: "Hard" };

/** Today's daily of one variant. key names its game in the page's state. */
export function dailyRoute(variant) {
    return Object.freeze({ name: "daily", variant, key: variant });
}

/** A practice preset. */
export function playRoute(preset) {
    return Object.freeze({ name: "play", preset, key: `play/${preset}` });
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
    return null;
}

/** The hash for a route: parseRoute(routeHash(r)) is r again. */
export function routeHash(route) {
    if (!route || route.name === "home") return "#/";
    if (route.name === "daily") return `#/${route.variant}`;
    return `#/play/${route.preset}`;
}

export function sameRoute(a, b) {
    return !!a && !!b && a.name === b.name && a.key === b.key;
}

/**
 * What a game route plays: { daily, kind, region, variant, preset, anyRegion }.
 * Home has none.
 */
export function routeInfo(route) {
    if (!route || route.name === "home") return null;
    if (route.name === "daily") {
        return { daily: true, kind: "country", region: "world", variant: route.variant, preset: null, anyRegion: false };
    }
    const p = PRESETS[route.preset];
    return { daily: false, kind: p.kind, region: p.region, variant: p.variant, preset: route.preset, anyRegion: !!p.anyRegion };
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
 * "Europe only", "Capitals · Asia" (a regional Capitals game resumed).
 */
export function modeLabel(route, { puzzle = null, region = null } = {}) {
    if (!route || route.name === "home") return "";
    if (route.name === "daily") {
        return `${DAILY_LABELS[route.variant]} · Daily${puzzle != null ? ` #${puzzle}` : ""}`;
    }
    const p = PRESETS[route.preset];
    if (p.anyRegion && region && region !== p.region) return `${p.label} · ${regionLabel(region)}`;
    return p.label;
}

/** The icon for a route's badge. */
export function modeIcon(route) {
    if (!route || route.name === "home") return "bi-globe-americas";
    if (route.name === "daily") return route.variant === "hard" ? "bi-eye-slash-fill" : "bi-calendar2-check";
    return PRESETS[route.preset].icon;
}

/**
 * A daily card's status line from a home summary ({ status, guessCount }):
 * "Play", "In progress · 3 guesses", "Solved in 4 ✓", "Gave up".
 * A game that is PLAYING with no guesses yet is still "Play".
 */
export function statusText(summary) {
    const status = summary && summary.status;
    const n = summary && Number.isFinite(summary.guessCount) ? summary.guessCount : 0;
    if (status === "WON") return `Solved in ${n} ✓`;
    if (status === "GAVE_UP") return "Gave up";
    if (status === "PLAYING" && n > 0) return `In progress · ${plural(n, "guess", "guesses")}`;
    return "Play";
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
    return n > 0 ? `Resume · ${plural(n, "guess", "guesses")}` : "Resume";
}

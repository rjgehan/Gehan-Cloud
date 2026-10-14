// Name them all, round 2: the rules and words behind "Study what you missed" and
// "Request a name change", as plain functions.
//
// Pure: no DOM, no fetch, no storage. study.js and requests.js draw with these; the
// node harness tests them directly, in both languages. Words come from i18n.js in
// the page's language at the moment they are asked for.
//
// WHERE a country is: a compass point inside its continent ("In the north-west of
// Africa"), worked out from the country's label point (the study response's lat and
// lng) against a rough box around the continent's mainland, kept here. The Americas
// are four boxes (North America, Central America, the Caribbean, South America),
// since "the north of the Americas" says little. Islands outside a box count as its
// nearest edge, so Cabo Verde is in the north-west of Africa and Mauritius in the
// south-east. Where the grid would still mislead, COMPASS_OVERRIDES gives the usual name.

import { t, tn, locale, getLang } from "./i18n.js";
import { formatInteger } from "./format.js";

/** The study page shows this many missed countries before "Show all N". */
export const STUDY_PAGE = 10;

/** The request sheet offers at most this many recent "not recognised" texts. */
export const RECENT_TEXTS = 5;

/** The longest suggestion the server takes, in characters (code points). */
export const SUGGESTION_MAX = 80;

/**
 * Each continent's mainland, roughly, tuned so the usual names come out (Spain is
 * in the south-west of Europe, Senegal in the north-west of Africa, India and
 * Pakistan in the south of Asia, Thailand in the south-east, Kazakhstan and
 * Uzbekistan in the middle, Mongolia and Japan in the east); the far north of Europe
 * and Patagonia lie past an edge. { west, east, south, north } in degrees. A box may
 * run east past 180° (Oceania's ends at 205°, i.e. 155° W, for Kiribati, Samoa and
 * Tonga); longitudes are read around the box's own middle.
 */
export const AREA_BOXES = Object.freeze({
    africa: { west: -17.5, east: 51.5, south: -35, north: 37.5 },
    europe: { west: -10.5, east: 40, south: 34, north: 71 },
    asia: { west: 23, east: 128, south: 13, north: 67 },
    oceania: { west: 112, east: 205, south: -47, north: 10 },
    northAmerica: { west: -168, east: -52, south: 14.5, north: 72 },
    centralAmerica: { west: -92.3, east: -77.2, south: 7.2, north: 18.5 },
    caribbean: { west: -85, east: -59.4, south: 10, north: 25 },
    southAmerica: { west: -81.3, east: -34.8, south: -45, north: 12.5 },
    americas: { west: -168, east: -34.8, south: -55.9, north: 72 },
});

/** Where the 3 × 3 grid would mislead whatever the box: the usual name wins. */
const COMPASS_OVERRIDES = Object.freeze({
    NGA: "west",    // West Africa; Benin and Togo are already "west"
    DNK: "north",   // Scandinavia, with Norway and Sweden
    TWN: "east",    // East Asia, not South-East Asia
});

/** The 35 countries of the Americas by part (any other code there uses the whole box). */
const AMERICAS_PARTS = (() => {
    const parts = {
        northAmerica: ["CAN", "USA", "MEX"],
        centralAmerica: ["BLZ", "GTM", "SLV", "HND", "NIC", "CRI", "PAN"],
        caribbean: ["CUB", "JAM", "HTI", "DOM", "BHS", "BRB", "TTO", "GRD", "LCA", "VCT", "ATG", "DMA", "KNA"],
        southAmerica: ["ARG", "BOL", "BRA", "CHL", "COL", "ECU", "GUY", "PRY", "PER", "SUR", "URY", "VEN"],
    };
    const out = new Map();
    for (const [part, codes] of Object.entries(parts)) for (const code of codes) out.set(code, part);
    return out;
})();

/** Which box a country is described in: its continent, or (the Americas) its part of them. */
export function areaOf(code, region) {
    if (region === "americas") return AMERICAS_PARTS.get(code) || "americas";
    return Object.prototype.hasOwnProperty.call(AREA_BOXES, region) ? region : null;
}

export const COMPASS_POINTS = Object.freeze([
    "north-west", "north", "north-east", "west", "centre", "east", "south-west", "south", "south-east",
]);

/**
 * Where a point sits in a box, as a 3 × 3 grid: "north-west", "north", ...,
 * "centre", ..., "south-east"; null without a box or a point. A point outside the
 * box counts as its nearest edge.
 */
export function compassOf(lat, lng, box) {
    if (!box || !Number.isFinite(lat) || !Number.isFinite(lng)) return null;
    const middle = (box.west + box.east) / 2;
    let x = lng;
    while (x < middle - 180) x += 360;
    while (x >= middle + 180) x -= 360;
    const clamp01 = (v) => Math.max(0, Math.min(1, v));
    const u = clamp01((x - box.west) / (box.east - box.west));
    const v = clamp01((lat - box.south) / (box.north - box.south));
    const EPS = 1e-9;
    const col = u < 1 / 3 - EPS ? "west" : u >= 2 / 3 - EPS ? "east" : null;
    const row = v >= 2 / 3 - EPS ? "north" : v < 1 / 3 - EPS ? "south" : null;
    if (row && col) return `${row}-${col}`;
    return row || col || "centre";
}

// i18n keys, written out so `npm run check:i18n` sees every one.
const COMPASS_KEYS = {
    "north-west": "study.compass.northWest",
    north: "study.compass.north",
    "north-east": "study.compass.northEast",
    west: "study.compass.west",
    centre: "study.compass.centre",
    east: "study.compass.east",
    "south-west": "study.compass.southWest",
    south: "study.compass.south",
    "south-east": "study.compass.southEast",
};
const AREA_KEYS = {
    africa: "study.area.africa",
    europe: "study.area.europe",
    asia: "study.area.asia",
    oceania: "study.area.oceania",
    northAmerica: "study.area.northAmerica",
    centralAmerica: "study.area.centralAmerica",
    caribbean: "study.area.caribbean",
    southAmerica: "study.area.southAmerica",
    americas: "study.area.americas",
};
const FAMILIARITY_KEYS = {
    "well-known": "study.often.wellKnown",
    familiar: "study.often.familiar",
    obscure: "study.often.obscure",
};

/** "Senegal, Mali and Guinea" ("Senegal, Malí y Guinea"), in the page's language. */
export function joinNames(names, lang = getLang()) {
    const list = (names || []).filter(Boolean);
    try {
        if (typeof Intl.ListFormat === "function") {
            return new Intl.ListFormat(locale(lang), { style: "long", type: "conjunction" }).format(list);
        }
    } catch {
        // fall through
    }
    return list.join(", ");
}

/** "In the north-west of Africa", or null when the entry has no usable point. */
export function wherePhrase(entry) {
    if (!entry) return null;
    const area = areaOf(entry.code, entry.region);
    // A missing coordinate is no place at all (Number(null) would be 0°).
    const num = (v) => (typeof v === "number" ? v : NaN);
    const box = area ? AREA_BOXES[area] : null;
    const point = compassOf(num(entry.lat), num(entry.lng), box);
    // An entry with no point is still no place at all, override or not.
    const override = Object.prototype.hasOwnProperty.call(COMPASS_OVERRIDES, entry.code) ? COMPASS_OVERRIDES[entry.code] : null;
    const compass = point && (override || point);
    if (!compass) return null;
    return t(COMPASS_KEYS[compass], { area: t(AREA_KEYS[area]) });
}

/**
 * The WHERE sentence of a study card: "In the north-west of Africa, bordering
 * Senegal, Mali and Guinea." Borders are the nearby countries 0 km away; with none,
 * the nearest one: "In the east of Asia. Nearest: Japan, 200 km."
 *
 *   nameOf(code)   a country's name in the page's language
 *   distance(km)   "200 km" (or miles), format.js formatDistance with the page's units
 */
export function whereSentence(entry, { nameOf, distance }) {
    if (!entry) return "";
    const where = wherePhrase(entry);
    const nearby = Array.isArray(entry.nearby) ? entry.nearby : [];
    const borders = nearby.filter((n) => n && n.km === 0).map((n) => nameOf(n.code));
    if (borders.length) {
        const list = joinNames(borders);
        return where ? t("study.where.borders", { where, list }) : t("study.where.bordersOnly", { list });
    }
    const near = nearby.find((n) => n && n.code);
    if (near) {
        const params = { name: nameOf(near.code), km: distance(near.km) };
        return where ? t("study.where.nearest", { where, ...params }) : t("study.where.nearestOnly", params);
    }
    return where ? t("study.where.only", { where }) : "";
}

/**
 * The closest country on the player's list in that run, worded so it holds for a
 * filled-in country too: "Next to Senegal, which is on your list." / "Senegal, on
 * your list, is 300 km away." null when the run named none.
 */
/** Past this, the closest country on the player's list is too far away to help place one. */
export const NEAREST_NAMED_MAX_KM = 2500;

export function nearestNamedText(entry, { nameOf, distance }) {
    const near = entry && entry.nearestNamed;
    if (!near || !near.code) return null;
    // Only a landmark if it is near: "Nauru, 12,001 km away" places nothing.
    if (!(near.km <= NEAREST_NAMED_MAX_KM)) return null;
    const name = nameOf(near.code);
    return near.km === 0 ? t("study.yourNearest.border", { name }) : t("study.yourNearest.km", { name, km: distance(near.km) });
}

/**
 * How often it is named: from namedRate over sampleRuns when there are enough games,
 * in terms of games (runs), since one player's runs, filled-in countries and all, can
 * make the whole sample: "Usually named: in 7 of 9 games here." (more than half),
 * "Named in half the games here: 2 of 4.", "Usually missed: named in only 1 of 4
 * games here." or "Nobody has named it yet in 9 games."; the sentence is chosen from
 * the count shown, so the two always agree. Else from the coarse familiarity ("One
 * of the well-known ones."). null when there is neither.
 */
export function howOftenText(entry) {
    if (!entry) return null;
    const rate = entry.namedRate;
    const sample = Number(entry.sampleRuns) || 0;
    if (typeof rate === "number" && Number.isFinite(rate) && sample > 0) {
        const named = Math.max(0, Math.min(sample, Math.round(rate * sample)));
        if (named === 0) return tn("study.often.nobody", sample);
        const key = named * 2 > sample ? "study.often.most"
            : named * 2 === sample ? "study.often.half"
            : "study.often.few";
        return tn(key, sample, { named: formatInteger(named) });
    }
    const key = FAMILIARITY_KEYS[entry.familiarity];
    return key ? t(key) : null;
}

/**
 * The player's own record with it in this region's games that count: "You've missed
 * it in 3 of your 4 games here." null with no such games (the run studied did not
 * count, and it was the first).
 */
export function yourRecordText(entry) {
    if (!entry) return null;
    const runs = Math.max(0, Math.floor(Number(entry.yourRuns) || 0));
    const misses = Math.max(0, Math.floor(Number(entry.yourMisses) || 0));
    if (!runs) return null;
    if (misses === 0) return runs === 1 ? t("study.yours.neverMissedOne") : tn("study.yours.neverMissed", runs);
    if (misses >= runs) return runs === 1 ? t("study.yours.alwaysOne") : tn("study.yours.always", runs);
    return tn("study.yours.some", runs, { missed: formatInteger(misses) });
}

/** The missed countries a study page shows: the first STUDY_PAGE in the server's order, or all. */
export function visibleMissed(missed, showAll = false) {
    const list = Array.isArray(missed) ? missed : [];
    return showAll ? list : list.slice(0, STUDY_PAGE);
}

// ------------------------------------------------------------------ name-change requests

/**
 * A text with the characters that print nothing taken out: control and format
 * characters (zero-width spaces, bidirectional overrides, invisible tags), private
 * use and unassigned code points; line breaks and tabs become spaces. A suggestion is
 * anyone's text, and Copy all hands it to a chat: what is pasted must be what shows.
 * Returns { text, removed }.
 */
export function visibleText(value) {
    // Breaks and tabs are gaps, as the server reads them; anything else invisible goes.
    const spaced = String(value == null ? "" : value).replace(/[\t\n\v\f\r\u0085\u2028\u2029]/g, " ");
    const text = spaced.replace(/[\p{Cc}\p{Cf}\p{Co}\p{Cn}]/gu, "");
    return { text, removed: text.length !== spaced.length };
}

/** A suggestion as typed, cleaned the way the server will (gaps to one space, trimmed). */
export function cleanSuggestion(value) {
    return visibleText(value).text.replace(/\s+/gu, " ").trim();
}

/**
 * Whether a suggestion can be sent: { ok, text, key } where key is the i18n key of
 * the reason when it cannot ("request.error.empty", "request.error.tooLong").
 * The server's rule: 1 to 80 characters once cleaned, with at least one letter or digit.
 */
export function checkSuggestion(value) {
    const text = cleanSuggestion(value);
    if (!text || !/[\p{L}\p{N}]/u.test(text)) return { ok: false, text, key: "request.error.empty" };
    if ([...text].length > SUGGESTION_MAX) return { ok: false, text, key: "request.error.tooLong" };
    return { ok: true, text, key: null };
}

/**
 * The list of recent "not recognised" texts after one more: most recent first, no
 * repeats (ignoring case), at most RECENT_TEXTS, each cleaned and cut to
 * SUGGESTION_MAX characters. Never mutates the list given.
 */
export function rememberText(list, text, max = RECENT_TEXTS) {
    const clean = [...cleanSuggestion(text)].slice(0, SUGGESTION_MAX).join("");
    const before = Array.isArray(list) ? list : [];
    if (!clean) return before.slice(0, max);
    const key = clean.toLocaleLowerCase();
    return [clean, ...before.filter((x) => String(x).toLocaleLowerCase() !== key)].slice(0, max);
}

/** The i18n key explaining why sending a request failed, from the ApiError code. */
export function requestErrorKey(code) {
    switch (code) {
        case "too-many": return "request.error.tooMany";
        case "bad-request": return "request.error.bad";
        case "signed-out": return "request.error.signedOut";
        case "forbidden": return "request.error.stale";
        case "offline": return "request.error.offline";
        case "timeout": return "request.error.timeout";
        default: return "request.error.generic";
    }
}

const SOURCE_KEYS = {
    named: "request.source.named",
    missed: "request.source.missed",
    study: "request.source.study",
    other: "request.source.other",
};
const LANG_KEYS = { en: "request.lang.en", es: "request.lang.es" };
const STATUS_KEYS = { DONE: "requests.copy.done", DISMISSED: "requests.copy.dismissed" };

/** Where a request was typed, as words: "typed on the missed list". */
export function sourceText(source) {
    return t(SOURCE_KEYS[source] || SOURCE_KEYS.other);
}

/** Which page language it was sent from: "English page". */
export function langText(lang) {
    return t(LANG_KEYS[lang] || LANG_KEYS.en);
}

/** "2026-10-14" from an ISO instant (its UTC date), or "" for nothing usable. */
export function isoDay(instant) {
    const m = /^(\d{4}-\d{2}-\d{2})/.exec(String(instant || ""));
    return m ? m[1] : "";
}

/** A quoted string that cannot break out of its quotes: "Great Britain and NI". */
export function quoted(text) {
    return `"${String(text).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

/**
 * One request as a plain line for Copy all, in the page's language:
 *   GBR United Kingdom ← "Great Britain and NI" (by rjgehan, 2026-10-14, typed on the missed list, English page)
 * A resolved one says so at the end; a suggestion that had invisible characters
 * says they were taken out.
 */
export function requestLine(request, { nameOf }) {
    const r = request || {};
    const code = visibleText(r.code || "").text;
    const suggestion = visibleText(r.suggestion || "");
    const user = visibleText(r.username || "").text;
    let line = t("requests.copy.line", {
        code,
        name: nameOf(code),
        suggestion: quoted(suggestion.text),
        user,
        date: isoDay(r.createdAt),
        source: sourceText(r.source),
        lang: langText(r.lang),
    });
    if (suggestion.removed) line += ` ${t("requests.copy.cleaned")}`;
    if (STATUS_KEYS[r.status]) line += ` ${t(STATUS_KEYS[r.status])}`;
    return line;
}

/** Copy all: a heading line, then one line per request, ready to paste into a chat. */
export function copyAllText(requests, { nameOf }) {
    const list = Array.isArray(requests) ? requests : [];
    return [tn("requests.copy.heading", list.length), ...list.map((r) => requestLine(r, { nameOf }))].join("\n");
}

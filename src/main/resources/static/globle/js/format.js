// Small formatting helpers shared by the list, the end card and the leaderboard.
//
// Pure: nothing here touches the DOM, fetch or storage, so the node harness can
// import it directly and the rules (what a mile is, how a countdown reads) are
// tested rather than eyeballed. Numbers, dates and percentages follow the page's
// language through i18n.js's locale(): "1,234 km" and "83%" in English, "1.234 km"
// and "83 %" for a reader in Spain, "Fri, Oct 9" or "vie, 9 oct".

import { t, has, locale } from "./i18n.js";

const KM_PER_MILE = 1.609344;

/** Every region a practice game can be limited to. "mainland" is the world without small island nations. */
export const REGIONS = ["world", "africa", "americas", "asia", "europe", "oceania", "mainland"];

/** "europe" -> "Europe" (or "Europa"). Unknown values come back as-is rather than blank. */
export function regionLabel(region) {
    if (!region) return t("region.world");
    const key = `region.${region}`;
    return has(key) ? t(key) : String(region);
}

/**
 * A flag emoji from an ISO 3166-1 alpha-2 code. Flags are two "regional indicator"
 * letters, so FR is U+1F1EB U+1F1F7. A place without a code (or with a made-up one
 * no platform draws) gets a plain white flag instead of two stray letters.
 */
export function flagEmoji(iso2) {
    if (typeof iso2 !== "string" || !/^[A-Za-z]{2}$/.test(iso2)) {
        return "\u{1F3F3}️";
    }
    const base = 0x1f1e6;
    const upper = iso2.toUpperCase();
    return String.fromCodePoint(base + upper.charCodeAt(0) - 65, base + upper.charCodeAt(1) - 65);
}

// One formatter per locale and style: building them is the slow part.
const formats = new Map();

function numberFormat(options) {
    const tag = locale();
    const id = `${tag}|${JSON.stringify(options)}`;
    if (!formats.has(id)) formats.set(id, new Intl.NumberFormat(tag, options));
    return formats.get(id);
}

/** km is what the server sends; miles are only ever a display choice. */
export function toUnits(km, units) {
    return units === "mi" ? km / KM_PER_MILE : km;
}

/** A count for a table or tile: 1234 -> "1,234" (or "1.234"). Not for "#12": those are names. */
export function formatInteger(n) {
    if (typeof n !== "number" || !Number.isFinite(n)) {
        return "0";
    }
    return numberFormat({ maximumFractionDigits: 0 }).format(n);
}

/** 1234 -> "1,234 km" (or "767 mi"). Separators follow the page's locale; the units never change. */
export function formatDistance(km, units = "km") {
    if (typeof km !== "number" || !Number.isFinite(km)) {
        return "—";
    }
    const value = Math.round(toUnits(Math.max(0, km), units));
    return `${numberFormat({ maximumFractionDigits: 0 }).format(value)} ${units === "mi" ? "mi" : "km"}`;
}

/** Milliseconds -> "HH:MM:SS", clamped at zero so a late tick never shows "-00:00:01". */
export function formatCountdown(ms) {
    const total = Math.max(0, Math.floor((Number(ms) || 0) / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    return [h, m, s].map((n) => String(n).padStart(2, "0")).join(":");
}

/**
 * A stopwatch reading: 7 -> "0:07", 843 -> "14:03", 3729 -> "1:02:09". The same in
 * every language, like the countdown.
 */
export function formatClock(seconds) {
    const total = Math.max(0, Math.floor(Number(seconds) || 0));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const ss = String(total % 60).padStart(2, "0");
    return h > 0 ? `${h}:${String(m).padStart(2, "0")}:${ss}` : `${m}:${ss}`;
}

/**
 * "2026-10-09" -> "Fri, Oct 9" ("vie, 9 oct"). Parsed as a local calendar date on
 * purpose: new Date("2026-10-09") is UTC midnight, which is the evening before in
 * America.
 */
export function formatPuzzleDate(isoDate, tag = locale()) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(isoDate || ""));
    if (!m) {
        return "";
    }
    const date = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    return date.toLocaleDateString(tag, { weekday: "short", month: "short", day: "numeric" });
}

/** 0.8333 -> "83%" ("83 %" in Spain). null (nothing played) -> an em dash rather than "0%". */
export function formatPercent(rate) {
    if (typeof rate !== "number" || !Number.isFinite(rate)) {
        return "—";
    }
    return numberFormat({ style: "percent", maximumFractionDigits: 0 }).format(Math.round(rate * 100) / 100);
}

/** 4.25 -> "4.3" ("4,3" with a decimal comma); null (no wins yet) -> an em dash. */
export function formatAverage(avg) {
    if (typeof avg !== "number" || !Number.isFinite(avg)) {
        return "—";
    }
    return numberFormat({ minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(Math.round(avg * 10) / 10);
}

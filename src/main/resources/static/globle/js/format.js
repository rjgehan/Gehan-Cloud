// Small formatting helpers shared by the list, the end card and the leaderboard.
//
// Pure: nothing here touches the DOM, fetch or storage, so the node harness can
// import it directly and the rules (what a mile is, how a countdown reads) are
// tested rather than eyeballed.

const KM_PER_MILE = 1.609344;

export const REGIONS = ["world", "africa", "americas", "asia", "europe", "oceania"];

const REGION_LABELS = {
    world: "World",
    africa: "Africa",
    americas: "Americas",
    asia: "Asia",
    europe: "Europe",
    oceania: "Oceania",
    antarctica: "Antarctica",
};

/** "europe" -> "Europe". Unknown values come back as-is rather than blank. */
export function regionLabel(region) {
    return REGION_LABELS[region] || String(region || "World");
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

const numberFormat = new Intl.NumberFormat(undefined, { maximumFractionDigits: 0 });

/** km is what the server sends; miles are only ever a display choice. */
export function toUnits(km, units) {
    return units === "mi" ? km / KM_PER_MILE : km;
}

/** 1234 -> "1,234 km" (or "767 mi"). Separators follow the viewer's locale. */
export function formatDistance(km, units = "km") {
    if (typeof km !== "number" || !Number.isFinite(km)) {
        return "—";
    }
    const value = Math.round(toUnits(Math.max(0, km), units));
    return `${numberFormat.format(value)} ${units === "mi" ? "mi" : "km"}`;
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
 * "2026-10-09" -> "Fri, Oct 9". Parsed as a local calendar date on purpose:
 * new Date("2026-10-09") is UTC midnight, which is the evening before in America.
 */
export function formatPuzzleDate(isoDate, locale) {
    const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(String(isoDate || ""));
    if (!m) {
        return "";
    }
    const date = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
    return date.toLocaleDateString(locale, { weekday: "short", month: "short", day: "numeric" });
}

/** 1 -> "1 guess", 3 -> "3 guesses". */
export function plural(n, one, many = `${one}s`) {
    return `${n} ${n === 1 ? one : many}`;
}

/** 0.8333 -> "83%". null (nothing played) -> an em dash rather than "0%". */
export function formatPercent(rate) {
    if (typeof rate !== "number" || !Number.isFinite(rate)) {
        return "—";
    }
    return `${Math.round(rate * 100)}%`;
}

/** 4.25 -> "4.3"; null (no wins yet) -> an em dash. */
export function formatAverage(avg) {
    if (typeof avg !== "number" || !Number.isFinite(avg)) {
        return "—";
    }
    return (Math.round(avg * 10) / 10).toFixed(1);
}

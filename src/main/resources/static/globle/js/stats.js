// Reading a game state and the stats block: ordering guesses, the distribution
// chart, which bucket a win lands in.
//
// Pure: no DOM. The server owns the numbers; this only decides how they are shown.

/** The guess-distribution buckets, in display order. Must match the server's keys. */
export const BUCKETS = ["1", "2", "3", "4", "5", "6", "7-9", "10-14", "15+"];

/** Which distribution bucket a win in n guesses falls into. */
export function bucketFor(n) {
    const count = Math.max(1, Math.floor(Number(n) || 0));
    if (count <= 6) return String(count);
    if (count <= 9) return "7-9";
    if (count <= 14) return "10-14";
    return "15+";
}

/**
 * Rows for the bar chart. width is relative to the tallest bar so the chart always
 * uses its space; a zero bucket still gets a sliver so the row does not look broken.
 */
export function distributionRows(distribution, highlightBucket) {
    const counts = BUCKETS.map((b) => Math.max(0, Number(distribution && distribution[b]) || 0));
    const max = Math.max(1, ...counts);
    return BUCKETS.map((bucket, i) => ({
        bucket,
        count: counts[i],
        width: counts[i] === 0 ? 0 : counts[i] / max,
        highlight: bucket === highlightBucket,
    }));
}

/** Guesses as the player made them: order 1 first. Never mutates the input. */
export function inGuessOrder(guesses) {
    return [...(guesses || [])].sort((a, b) => a.order - b.order);
}

/**
 * Guesses closest first, which is how Globle lists them. The answer goes above
 * everything: a neighbour is also 0 km away, and the winning row should not sit
 * second behind it. Ties keep guess order so the list does not shuffle.
 */
export function closestFirst(guesses, answerCode) {
    const rank = (g) => (answerCode && g.code === answerCode ? -1 : g.km);
    return [...(guesses || [])].sort((a, b) => rank(a) - rank(b) || a.order - b.order);
}

/** The most recent guess, or null. */
export function latestGuess(guesses) {
    let latest = null;
    for (const g of guesses || []) {
        if (!latest || g.order > latest.order) latest = g;
    }
    return latest;
}

/** The closest guess (answer first, then earliest of equals), or null. */
export function closestGuess(guesses, answerCode) {
    return closestFirst(guesses, answerCode)[0] || null;
}

/** The guess the player just made: in next but not in prev. */
export function newGuess(prev, next) {
    const seen = new Set((prev && prev.guesses || []).map((g) => g.code));
    const added = (next && next.guesses || []).filter((g) => !seen.has(g.code));
    return latestGuess(added);
}

/** The stats bucket to highlight for a finished daily: only a win has one. */
export function highlightFor(state) {
    return state && state.status === "WON" ? bucketFor(state.guessCount) : null;
}

/** The viewer's own row in a leaderboard list, or null. */
export function ownRow(rows) {
    return (rows || []).find((r) => r && r.you) || null;
}

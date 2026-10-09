// How hot or cold a guess is, as a colour and as a share square.
//
// Pure: the globe, the guess list swatches and the share text all read from here,
// so a country can never be one colour on the globe and another in the list.

/** The mystery country, once found. Distinct in hue from every step of the scale. */
export const ANSWER_COLOR = "#22c55e";

/**
 * ColorBrewer OrRd, nearest first: the same family globle-game.com uses, running
 * from a deep red for "touching it" to a pale cream for "other side of the world".
 */
const STOPS = ["#7f0000", "#b30000", "#d7301f", "#ef6548", "#fc8d59",
               "#fdbb84", "#fdd49e", "#fee8c8", "#fff7ec"];

/**
 * Beyond this many km everything is the palest step. Country distances are
 * border-to-border and top out near 20,000 km, but nearly every useful signal is
 * inside 12,000. Capital distances are centre-to-centre, so nothing is ever 0 and
 * a tighter range keeps 100 km and 1,000 km visibly different.
 */
export const MAX_KM = { country: 12000, capital: 10000 };

const RGB_STOPS = STOPS.map(hexToRgb);

export function hexToRgb(hex) {
    const h = hex.replace("#", "");
    return [0, 2, 4].map((i) => parseInt(h.slice(i, i + 2), 16));
}

function toHex(rgb) {
    return "#" + rgb.map((v) => Math.round(Math.max(0, Math.min(255, v))).toString(16).padStart(2, "0")).join("");
}

/** "#ff0000", 0.5 -> "rgba(255, 0, 0, 0.5)". */
export function withAlpha(hex, alpha) {
    const [r, g, b] = hexToRgb(hex);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

/**
 * Position on the scale, 0 (nearest) to 1 (farthest). Square-root spacing, as the
 * original does: the difference between 200 km and 800 km matters far more to a
 * player than the difference between 9,000 and 9,600.
 */
export function proximity(km, kind = "country") {
    const max = MAX_KM[kind] || MAX_KM.country;
    const d = Math.max(0, Number(km) || 0);
    return Math.min(1, Math.sqrt(d / max));
}

/** The colour for a (wrong) guess this far from the answer. */
export function colorFor(km, kind = "country") {
    const t = proximity(km, kind) * (RGB_STOPS.length - 1);
    const i = Math.min(RGB_STOPS.length - 2, Math.floor(t));
    const f = t - i;
    const a = RGB_STOPS[i];
    const b = RGB_STOPS[i + 1];
    return toHex([0, 1, 2].map((c) => a[c] + (b[c] - a[c]) * f));
}

/** The colour for a guess in a game state: green when it is the answer. */
export function guessColor(guess, answerCode, kind = "country") {
    if (answerCode && guess.code === answerCode) {
        return ANSWER_COLOR;
    }
    return colorFor(guess.km, kind);
}

/** A CSS linear-gradient for the legend, nearest on the right like a thermometer. */
export function legendGradient() {
    return `linear-gradient(90deg, ${[...STOPS].reverse().join(", ")})`;
}

/**
 * One share square per guess. The bands match globle-game.com's (getColourEmoji:
 * under 10%, 25% and 50% of 15,000 km), so a result shared here reads the same as
 * one shared there.
 */
export function squareFor(km, isAnswer) {
    if (isAnswer) return "\u{1F7E9}";        // green
    if (km < 1500) return "\u{1F7E5}";       // red
    if (km < 3750) return "\u{1F7E7}";       // orange
    if (km < 7500) return "\u{1F7E8}";       // yellow
    return "⬜";                         // white
}

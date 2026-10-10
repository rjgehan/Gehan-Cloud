// The text a finished game shares: a grid of coloured squares, never a country
// name, so posting it in the family chat spoils nothing.
//
// Pure: takes a GameState, returns a string. The button that copies it lives in main.js.

import { squareFor } from "./scale.js";
import { inGuessOrder } from "./stats.js";
import { regionLabel } from "./format.js";

const PER_LINE = 8;

/** A globe that faces the region played; daily keeps the Americas one it always had. */
const GLOBES = {
    world: "\u{1F30E}",
    mainland: "\u{1F30E}",
    americas: "\u{1F30E}",
    europe: "\u{1F30D}",
    africa: "\u{1F30D}",
    asia: "\u{1F30F}",
    oceania: "\u{1F30F}",
};

/** One square per guess, in the order guessed. */
export function shareSquares(state) {
    const answer = state && state.answer ? state.answer.code : null;
    return inGuessOrder(state && state.guesses).map((g) => squareFor(g.km, answer !== null && g.code === answer));
}

/** A missing variant is classic: games from before Hard existed. */
function isHard(state) {
    return !!state && state.variant === "hard";
}

/** The first line: which game this was. */
export function shareTitle(state) {
    if (!state || state.mode === "daily") {
        const puzzle = state && state.puzzle != null ? ` #${state.puzzle}` : "";
        if (isHard(state)) return `\u{1F30E} Globle Hard${puzzle} \u{1F30E}`;
        return `\u{1F30E} Globle (Gehan)${puzzle} \u{1F30E}`;
    }
    const regionCode = state.region || "world";
    const region = regionLabel(regionCode);
    const globe = GLOBES[regionCode] || GLOBES.world;
    if (isHard(state)) {
        return regionCode === "world"
            ? `${globe} Globle Hard Practice ${globe}`
            : `${globe} Globle Hard Practice (${region}) ${globe}`;
    }
    if (state.kind === "capital") {
        return `\u{1F3DB}️ Globle Capitals (${region}) \u{1F3DB}️`;
    }
    return `${globe} Globle Unlimited (${region}) ${globe}`;
}

/**
 * The whole share text.
 *
 *   streak  the viewer's current streak for this daily (classic or hard), shown only
 *           for a daily win
 *   site    where to play, without the scheme so chat apps do not unfurl a preview
 */
export function shareText(state, { streak = 0, site = "gehan.cloud/globle" } = {}) {
    const squares = shareSquares(state);
    const won = state && state.status === "WON";
    const result = won ? String(squares.length) : "X";

    const lines = [shareTitle(state)];
    if (squares.length === 0) {
        lines.push("\u{1F3F3}️ Gave up before guessing");
    } else {
        for (let i = 0; i < squares.length; i += PER_LINE) {
            lines.push(squares.slice(i, i + PER_LINE).join(""));
        }
        lines[lines.length - 1] += ` = ${result}`;
    }
    if (won && state.mode === "daily" && streak > 0) {
        lines.push(`\u{1F525} ${streak}-day streak`);
    }
    if (site) {
        lines.push(site);
    }
    return lines.join("\n");
}

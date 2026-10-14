// The text a finished game shares: a grid of coloured squares, never a country
// name, so posting it in the family chat spoils nothing.
//
// Pure: takes a GameState (or, for Name them all, a RollCallState), returns a
// string. The buttons that copy it live in main.js and rollcall-view.js.
// The words are in the page's language ("Globle Difícil #3", "Racha de 4 días");
// "Globle" and "(Gehan)" are names and stay as they are.

import { squareFor } from "./scale.js";
import { inGuessOrder } from "./stats.js";
import { regionLabel, formatClock } from "./format.js";
import { t, tn } from "./i18n.js";

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
        if (isHard(state)) return `\u{1F30E} Globle ${t("share.hard")}${puzzle} \u{1F30E}`;
        return `\u{1F30E} Globle (Gehan)${puzzle} \u{1F30E}`;
    }
    const regionCode = state.region || "world";
    const region = regionLabel(regionCode);
    const globe = GLOBES[regionCode] || GLOBES.world;
    if (isHard(state)) {
        return regionCode === "world"
            ? `${globe} Globle ${t("share.hardPractice")} ${globe}`
            : `${globe} Globle ${t("share.hardPractice")} (${region}) ${globe}`;
    }
    if (state.kind === "capital") {
        return `\u{1F3DB}️ Globle ${t("share.capitals")} (${region}) \u{1F3DB}️`;
    }
    return `${globe} Globle ${t("share.unlimited")} (${region}) ${globe}`;
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
        lines.push(`\u{1F3F3}️ ${t("share.gaveUpEarly")}`);
    } else {
        for (let i = 0; i < squares.length; i += PER_LINE) {
            lines.push(squares.slice(i, i + PER_LINE).join(""));
        }
        lines[lines.length - 1] += ` = ${result}`;
    }
    if (won && state.mode === "daily" && streak > 0) {
        lines.push(`\u{1F525} ${tn("share.streak", streak)}`);
    }
    if (site) {
        lines.push(site);
    }
    return lines.join("\n");
}

/** Name them all faces its region; the whole world gets the Europe-Africa globe. */
const ROLLCALL_GLOBES = { ...GLOBES, world: "\u{1F30D}" };

/**
 * A finished "Name them all" run, one line and where to play:
 *
 *   Globle · Name them all 🌍 162/197 · 14:03
 *   Globle · Nómbralos todos (Europa) 🌍 40/46 · 5:12
 *
 * Counts and time only, never which countries were named or missed.
 */
export function rollCallShareText(state, { site = "gehan.cloud/globle" } = {}) {
    const region = (state && state.region) || "world";
    const where = region === "world" ? "" : ` (${regionLabel(region)})`;
    const globe = ROLLCALL_GLOBES[region] || ROLLCALL_GLOBES.world;
    const named = state && Number.isFinite(state.namedCount) ? state.namedCount : 0;
    const total = state && Number.isFinite(state.total) ? state.total : 0;
    const lines = [`Globle · ${t("rollcall.name")}${where} ${globe} ${named}/${total} · ${formatClock(state && state.elapsedSeconds)}`];
    if (site) lines.push(site);
    return lines.join("\n");
}

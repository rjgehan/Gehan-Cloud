// DOM building blocks: an element helper, dialogs, toasts, screen-reader
// announcements, copying to the clipboard, and the renderers for the guess list,
// stats and leaderboard (Name them all's board and stats line included).
//
// Everything that shows server text (usernames, country names) goes through
// textContent, never innerHTML, so a username can only ever be a username. The
// words around it come from i18n.js, in the page's language; place names arrive
// already localised (main.js passes a state through names.js first).

import { flagEmoji, formatDistance, formatPercent, formatAverage, formatPuzzleDate, formatInteger, formatClock } from "./format.js";
import { guessColor } from "./scale.js";
import { closestFirst, distributionRows } from "./stats.js";
import { t, tn } from "./i18n.js";
import { practiceName } from "./modes.js";

// ---------------------------------------------------------------------- basics

/**
 * el("li", { class: "x", text: "hi", vars: { "--c": "#f00" }, "aria-label": "..." }, ...children)
 * vars are set through CSSOM (style.setProperty), which no CSP blocks.
 */
export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props || {})) {
        if (value == null || value === false) continue;
        if (key === "class") node.className = value;
        else if (key === "text") node.textContent = String(value);
        else if (key === "vars") {
            for (const [name, v] of Object.entries(value)) node.style.setProperty(name, String(v));
        } else node.setAttribute(key, value === true ? "" : String(value));
    }
    for (const child of children.flat(Infinity)) {
        if (child == null || child === false) continue;
        node.append(child instanceof Node ? child : String(child));
    }
    return node;
}

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

export function icon(name) {
    return el("i", { class: `bi ${name}`, "aria-hidden": "true" });
}

// ---------------------------------------------------------------------- dialogs

/**
 * Wires a <dialog>: [data-close] buttons close it, a click on the backdrop closes
 * it, and focus goes back to whatever opened it. Esc and the focus trap come from
 * showModal() itself, which also makes the page behind inert.
 */
export function setupDialog(dialog, { onClose } = {}) {
    dialog.addEventListener("click", (e) => {
        if (e.target === dialog) dialog.close("dismiss");
    });
    for (const button of dialog.querySelectorAll("[data-close]")) {
        button.addEventListener("click", () => dialog.close("dismiss"));
    }
    dialog.addEventListener("close", () => {
        const opener = dialog.globleOpener;
        dialog.globleOpener = null;
        if (onClose) onClose(dialog.returnValue);
        if (opener && opener.isConnected && typeof opener.focus === "function") {
            opener.focus({ preventScroll: true });
        }
    });
}

export function openDialog(dialog, focusSelector) {
    if (dialog.open) return;
    dialog.globleOpener = document.activeElement;
    dialog.returnValue = "";
    if (typeof dialog.showModal === "function") {
        dialog.showModal();
    } else {
        dialog.setAttribute("open", "");
    }
    const target = (focusSelector && dialog.querySelector(focusSelector)) || dialog.querySelector("[autofocus]");
    if (target) target.focus({ preventScroll: true });
    // Sheets scroll; always start at the top when reopened.
    const body = dialog.querySelector(".gl-sheet-body");
    if (body) body.scrollTop = 0;
}

/**
 * An in-page confirmation. Resolves true for the confirm button, false for
 * anything else (cancel, Esc, backdrop). Never window.confirm.
 */
export function confirmSheet(dialog, { title, body, confirm, cancel = t("common.cancel"), danger = false }) {
    $("#confirm-title", dialog).textContent = title;
    $("#confirm-body", dialog).textContent = body;
    const ok = $("#confirm-ok", dialog);
    ok.textContent = confirm;
    ok.classList.toggle("gl-btn-danger", danger);
    ok.classList.toggle("gl-btn-primary", !danger);
    $("#confirm-cancel", dialog).textContent = cancel;
    return new Promise((resolve) => {
        const done = () => {
            dialog.removeEventListener("close", done);
            resolve(dialog.returnValue === "ok");
        };
        dialog.addEventListener("close", done);
        // The safe choice has focus, so a stray Enter does not give anything up.
        openDialog(dialog, "#confirm-cancel");
    });
}

// ---------------------------------------------------------------------- feedback

let toastTimer = 0;

export function toast(text, ms = 2400) {
    const node = $("#toast");
    if (!node) return;
    node.textContent = text;
    node.hidden = false;
    node.classList.remove("is-leaving");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        node.classList.add("is-leaving");
        toastTimer = setTimeout(() => { node.hidden = true; }, 250);
    }, ms);
}

/** A polite screen-reader announcement. Cleared first so a repeat is still read. */
export function announce(text) {
    const node = $("#announcer");
    if (!node) return;
    node.textContent = "";
    setTimeout(() => { node.textContent = text; }, 60);
}

/**
 * Puts text on the clipboard: the Clipboard API where the page may use it, else
 * the old select-and-copy. Resolves false when neither worked, so the caller can
 * show the text to copy by hand (never window.prompt).
 */
export async function copyText(text) {
    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
            return true;
        }
    } catch {
        // fall back below
    }
    try {
        const area = el("textarea", { class: "gl-offscreen", readonly: true, "aria-hidden": "true" });
        area.value = text;
        document.body.append(area);
        area.select();
        const ok = document.execCommand("copy");
        area.remove();
        return ok;
    } catch {
        return false;
    }
}

// ---------------------------------------------------------------------- game

/** "Paris, France": a capital and its country, as a guess in capitals mode is named. */
export function capitalWithCountry(place) {
    return t("place.capitalOf", { capital: place.capital.name, country: place.name });
}

/** The guess list, closest first. state's names are already in the page's language. */
export function renderGuesses(list, { state, units, latestCode, fresh }) {
    const answerCode = state.answer ? state.answer.code : null;
    const kind = state.kind;
    const rows = closestFirst(state.guesses, answerCode).map((g) => {
        const isAnswer = answerCode !== null && g.code === answerCode;
        const color = guessColor(g, answerCode, kind);
        const capital = kind === "capital" && g.capital;
        const name = capital
            ? el("span", { class: "gl-g-name" },
                el("span", { class: "gl-g-main", text: g.capital.name }),
                el("span", { class: "gl-g-sub", text: g.name }))
            : el("span", { class: "gl-g-name" }, el("span", { class: "gl-g-main", text: g.name }));
        const distance = isAnswer ? t("list.found") : formatDistance(g.km, units);
        const classes = ["gl-g"];
        if (isAnswer) classes.push("is-answer");
        if (g.code === latestCode) classes.push("is-latest");
        if (fresh && g.code === latestCode) classes.push("is-new");
        return el("li", { class: classes.join(" "), vars: { "--c": color }, "data-code": g.code },
            el("span", { class: "gl-g-swatch", "aria-hidden": "true" }),
            el("span", { class: "gl-g-flag", "aria-hidden": "true", text: flagEmoji(g.iso2) }),
            name,
            el("span", { class: "gl-g-km", text: distance }),
            el("span", { class: "gl-g-order", title: t("list.order", { n: g.order }), text: `#${g.order}` }),
            g.code === latestCode ? el("span", { class: "sr-only", text: ` ${t("list.latest")}` }) : null);
    });
    list.replaceChildren(...rows);
}

/** "Closest border: Spain, 0 km". */
export function renderClosest(node, { state, units, closest }) {
    const capitals = state.kind === "capital";
    const heading = el("span", { class: "gl-closest-label", text: t(capitals ? "closest.capital" : "closest.border") });
    if (!closest) {
        node.replaceChildren(heading,
            el("span", { class: "gl-closest-empty", text: t(capitals ? "closest.beginCapital" : "closest.begin") }));
        return;
    }
    const answerCode = state.answer ? state.answer.code : null;
    const found = answerCode && closest.code === answerCode;
    const label = capitals && closest.capital ? capitalWithCountry(closest) : closest.name;
    node.replaceChildren(heading,
        el("span", { class: "gl-closest-value", vars: { "--c": guessColor(closest, answerCode, state.kind) } },
            el("span", { class: "gl-closest-dot", "aria-hidden": "true" }),
            el("span", { "aria-hidden": "true", text: flagEmoji(closest.iso2) + " " }),
            el("span", { class: "gl-closest-name", text: label }),
            el("strong", { class: "gl-closest-km", text: found ? t("list.found") : formatDistance(closest.km, units) })));
}

// ---------------------------------------------------------------------- stats

/** A row of big-number tiles: [{ value, label }]. */
export function renderTiles(node, tiles) {
    node.replaceChildren(...tiles.map((tile) =>
        el("div", { class: "gl-tile" },
            el("span", { class: "gl-tile-value", text: tile.value }),
            el("span", { class: "gl-tile-label", text: tile.label }))));
}

/** The daily stats tiles from leaderboard.me. */
export function dailyTiles(me) {
    const m = me || {};
    return [
        { value: m.played != null ? formatInteger(m.played) : "0", label: t("tiles.played") },
        { value: formatPercent(m.played ? m.winRate : null), label: t("tiles.winRate") },
        { value: m.currentStreak ? `${formatInteger(m.currentStreak)}\u{1F525}` : "0", label: t("tiles.streak") },
        { value: m.maxStreak != null ? formatInteger(m.maxStreak) : "0", label: t("tiles.maxStreak") },
        { value: formatAverage(m.avgGuesses), label: t("tiles.avg") },
    ];
}

/** Guess distribution as horizontal bars; the bucket of today's win is lit. */
export function renderDistribution(node, distribution, highlight, { empty: emptyText = t("dist.empty") } = {}) {
    const rows = distributionRows(distribution, highlight);
    const empty = rows.every((r) => r.count === 0);
    if (empty) {
        node.replaceChildren(el("p", { class: "gl-muted", text: emptyText }));
        return;
    }
    // Read as "7-9 guesses: 2 wins (today)". A bucket is one guess only when it is "1".
    const spoken = (r) => `${tn("dist.bucket", r.bucket === "1" ? 1 : 2, { bucket: r.bucket })}: ${tn("dist.wins", r.count)}`
        + (r.highlight ? ` ${t("dist.today")}` : "");
    node.replaceChildren(el("ol", { class: "gl-dist", "aria-label": t("dist.label") },
        rows.map((r) => el("li", { class: r.highlight ? "gl-dist-row is-today" : "gl-dist-row" },
            el("span", { class: "gl-dist-label", "aria-hidden": "true", text: r.bucket }),
            el("span", { class: "gl-dist-track", "aria-hidden": "true" },
                el("span", { class: "gl-dist-bar", vars: { "--w": r.width } },
                    el("span", { class: "gl-dist-n", text: formatInteger(r.count) }))),
            el("span", { class: "sr-only", text: spoken(r) })))));
}

// ---------------------------------------------------------------------- leaderboard

const MEDALS = { 1: "\u{1F947}", 2: "\u{1F948}", 3: "\u{1F949}" };
const STATUS = {
    WON: { key: "board.won", cls: "is-won" },
    PLAYING: { key: "board.playing", cls: "is-playing" },
    GAVE_UP: { key: "board.gaveUp", cls: "is-gaveup" },
};

/** "hard" or "classic": the daily a board belongs to (anything else is classic). */
function dailyVariant(variant) {
    return variant === "hard" ? "hard" : "classic";
}

function who(row) {
    return el("span", { class: "gl-who" },
        el("span", { class: "gl-who-name", text: row.username }),
        row.you ? el("span", { class: "gl-you", text: t("board.you") }) : null);
}

function table(caption, head, rows, numericFrom = 1) {
    return el("table", { class: "gl-table" },
        el("caption", { class: "sr-only", text: caption }),
        el("thead", {}, el("tr", {}, head.map((h, i) => el("th", {
            scope: "col",
            class: [i >= numericFrom ? "num" : "", h.hideNarrow ? "hide-narrow" : ""].join(" ").trim() || null,
            title: h.title || null,
        }, h.label)))),
        el("tbody", {}, rows));
}

function emptyNote(text) {
    return el("p", { class: "gl-muted gl-board-empty", text });
}

/** The column headings the boards share. */
const COLUMNS = {
    player: () => ({ label: t("board.col.player") }),
    played: () => ({ label: t("board.col.played"), title: t("board.col.playedTitle") }),
    wins: () => ({ label: t("board.col.wins"), title: t("board.col.winsTitle") }),
    winRate: () => ({ label: t("board.col.winRate") }),
    avg: () => ({ label: t("board.col.avg"), title: t("board.col.avgTitle") }),
    streak: () => ({ label: t("board.col.streak"), title: t("board.col.streakTitle") }),
    best: (hideNarrow = false) => ({ label: t("board.col.best"), title: t("board.col.bestTitle"), hideNarrow }),
};

/** variant says which daily: "classic" (headed "Puzzle #3") or "hard" ("Hard #3"). */
export function renderToday(node, today, { variant = "classic" } = {}) {
    if (!today) {
        node.replaceChildren(emptyNote(t("board.noPuzzle")));
        return;
    }
    const parts = [el("p", { class: "gl-board-meta",
        text: t(`board.todayMeta.${dailyVariant(variant)}`, { n: today.puzzle, date: formatPuzzleDate(today.date) }) })];
    // The race is visible while you play: entries carry only a status and a guess
    // count, never a country, so there is nothing here to spoil.
    if (!today.viewerFinished) {
        parts.push(el("p", { class: "gl-board-lock" }, icon("bi-eye-slash"), ` ${t("board.lock")}`));
    }
    const entries = today.entries || [];
    if (entries.length === 0) {
        parts.push(emptyNote(t("board.nobody")));
    } else {
        parts.push(el("ol", { class: "gl-today" }, entries.map((e) => {
            const status = STATUS[e.status] || STATUS.PLAYING;
            const place = e.rank ? (MEDALS[e.rank] || `#${e.rank}`) : "–";
            return el("li", { class: e.you ? "gl-today-row is-you" : "gl-today-row" },
                el("span", { class: e.rank && e.rank <= 3 ? "gl-place is-medal" : "gl-place", text: place,
                    "aria-label": e.rank ? t("board.rank", { n: e.rank }) : null }),
                who(e),
                el("span", { class: `gl-status ${status.cls}`, text: t(status.key) }),
                el("span", { class: "gl-today-n", text: tn("count.guesses", e.guessCount || 0) }));
        })));
    }
    node.replaceChildren(...parts);
}

/** variant names the dailies: "classic" (daily puzzles) or "hard" (Hard dailies). */
export function renderWeek(node, week, { variant = "classic" } = {}) {
    const v = dailyVariant(variant);
    const rows = week || [];
    if (!rows.length) {
        node.replaceChildren(emptyNote(t(`board.week.empty.${v}`)));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: t(`board.week.meta.${v}`) }),
        table(t("board.week.caption"),
            [COLUMNS.player(), COLUMNS.played(), COLUMNS.wins(), COLUMNS.avg()],
            rows.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: formatInteger(r.played) }),
                el("td", { class: "num", text: formatInteger(r.wins) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) })))));
}

export function renderAllTime(node, allTime, { variant = "classic" } = {}) {
    const v = dailyVariant(variant);
    const rows = allTime || [];
    if (!rows.length) {
        node.replaceChildren(emptyNote(t(`board.all.empty.${v}`)));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: t(`board.all.meta.${v}`) }),
        table(t("board.all.caption"),
            [COLUMNS.player(), COLUMNS.wins(), COLUMNS.winRate(), COLUMNS.avg(), COLUMNS.streak(), COLUMNS.best(true)],
            rows.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: formatInteger(r.wins) }),
                el("td", { class: "num", text: formatPercent(r.played ? r.winRate : null) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) }),
                el("td", { class: "num gl-streak" },
                    r.currentStreak ? `${formatInteger(r.currentStreak)}\u{1F525}` : "0",
                    el("span", { class: "gl-streak-max", text: ` (${formatInteger(r.maxStreak || 0)})` })),
                el("td", { class: "num hide-narrow", text: r.best != null ? formatInteger(r.best) : "—" })))));
}

/** kind is the practice board: "country" (every classic region), "capital" or "hard". */
export function renderUnlimited(node, rows, kind) {
    const list = rows || [];
    const name = practiceName(kind);
    if (!list.length) {
        node.replaceChildren(emptyNote(t("board.practice.empty", { name })));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: t("board.practice.meta", { name }) }),
        table(t("board.practice.caption", { name }),
            [COLUMNS.player(), COLUMNS.played(), COLUMNS.wins(), COLUMNS.avg(), COLUMNS.best()],
            list.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: formatInteger(r.played) }),
                el("td", { class: "num", text: formatInteger(r.wins) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) }),
                el("td", { class: "num", text: r.best != null ? formatInteger(r.best) : "—" })))));
}

/** One line summarising a player's unlimited record: "12 wins from 15 · avg 5.2 · best 2". */
export function unlimitedSummary(row) {
    if (!row || !row.played) return t("summary.none");
    const parts = [tn("summary.wins", row.wins, { played: formatInteger(row.played) })];
    if (row.avgGuesses != null) parts.push(t("summary.avg", { avg: formatAverage(row.avgGuesses) }));
    if (row.best != null) parts.push(t("summary.best", { n: formatInteger(row.best) }));
    return parts.join(" · ");
}

/**
 * Name them all's board (leaderboard.rollcall): finished whole-world runs, one row
 * per player with their best run, as the server sorted them (most named, then the
 * faster). Filled-in countries count, so the board also says how many there were.
 */
export function renderRollCallBoard(node, rows) {
    const list = rows || [];
    if (!list.length) {
        node.replaceChildren(emptyNote(t("rollcall.board.empty")));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: t("rollcall.board.meta") }),
        table(t("rollcall.board.caption"),
            [
                COLUMNS.player(),
                COLUMNS.played(),
                { label: t("rollcall.board.col.best"), title: t("rollcall.board.col.bestTitle") },
                { label: t("rollcall.board.col.time"), title: t("rollcall.board.col.timeTitle") },
                { label: t("rollcall.board.col.auto"), title: t("rollcall.board.col.autoTitle"), hideNarrow: true },
            ],
            list.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: formatInteger(r.played) }),
                el("td", { class: "num", text: `${formatInteger(r.best)}/${formatInteger(r.total)}` }),
                el("td", { class: "num", text: formatClock(r.bestSeconds) }),
                el("td", { class: "num hide-narrow", text: formatInteger(r.bestAutoCount || 0) })))));
}

/** The stats sheet's line for Name them all, from the viewer's board row: "best 162/197 · 14:03". */
export function rollCallSummary(row) {
    if (!row || !row.played) return t("rollcall.stats.none");
    return t("rollcall.stats", {
        named: formatInteger(row.best),
        total: formatInteger(row.total),
        time: formatClock(row.bestSeconds),
    });
}

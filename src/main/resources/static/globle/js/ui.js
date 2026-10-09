// DOM building blocks: an element helper, dialogs, toasts, screen-reader
// announcements, and the renderers for the guess list, stats and leaderboard.
//
// Everything that shows server text (usernames, country names) goes through
// textContent, never innerHTML, so a username can only ever be a username.

import { flagEmoji, formatDistance, formatPercent, formatAverage, formatPuzzleDate, plural } from "./format.js";
import { guessColor } from "./scale.js";
import { closestFirst, distributionRows } from "./stats.js";

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
export function confirmSheet(dialog, { title, body, confirm, cancel = "Cancel", danger = false }) {
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

// ---------------------------------------------------------------------- game

/** The guess list, closest first. */
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
        const distance = isAnswer ? "Found!" : formatDistance(g.km, units);
        const classes = ["gl-g"];
        if (isAnswer) classes.push("is-answer");
        if (g.code === latestCode) classes.push("is-latest");
        if (fresh && g.code === latestCode) classes.push("is-new");
        return el("li", { class: classes.join(" "), vars: { "--c": color }, "data-code": g.code },
            el("span", { class: "gl-g-swatch", "aria-hidden": "true" }),
            el("span", { class: "gl-g-flag", "aria-hidden": "true", text: flagEmoji(g.iso2) }),
            name,
            el("span", { class: "gl-g-km", text: distance }),
            el("span", { class: "gl-g-order", title: `Guess ${g.order}`, text: `#${g.order}` }),
            g.code === latestCode ? el("span", { class: "sr-only", text: " (latest guess)" }) : null);
    });
    list.replaceChildren(...rows);
}

/** "Closest border: Spain, 0 km". */
export function renderClosest(node, { state, units, closest }) {
    if (!closest) {
        node.replaceChildren(
            el("span", { class: "gl-closest-label", text: state.kind === "capital" ? "Closest capital" : "Closest border" }),
            el("span", { class: "gl-closest-empty", text: state.kind === "capital"
                ? "Guess any capital to begin"
                : "Guess any country to begin" }));
        return;
    }
    const answerCode = state.answer ? state.answer.code : null;
    const found = answerCode && closest.code === answerCode;
    const label = state.kind === "capital" && closest.capital ? `${closest.capital.name}, ${closest.name}` : closest.name;
    node.replaceChildren(
        el("span", { class: "gl-closest-label", text: state.kind === "capital" ? "Closest capital" : "Closest border" }),
        el("span", { class: "gl-closest-value", vars: { "--c": guessColor(closest, answerCode, state.kind) } },
            el("span", { class: "gl-closest-dot", "aria-hidden": "true" }),
            el("span", { "aria-hidden": "true", text: flagEmoji(closest.iso2) + " " }),
            el("span", { class: "gl-closest-name", text: label }),
            el("strong", { class: "gl-closest-km", text: found ? "Found!" : formatDistance(closest.km, units) })));
}

// ---------------------------------------------------------------------- stats

/** A row of big-number tiles: [{ value, label }]. */
export function renderTiles(node, tiles) {
    node.replaceChildren(...tiles.map((t) =>
        el("div", { class: "gl-tile" },
            el("span", { class: "gl-tile-value", text: t.value }),
            el("span", { class: "gl-tile-label", text: t.label }))));
}

/** The daily stats tiles from leaderboard.me. */
export function dailyTiles(me) {
    const m = me || {};
    return [
        { value: m.played != null ? String(m.played) : "0", label: "Played" },
        { value: formatPercent(m.played ? m.winRate : null), label: "Win %" },
        { value: m.currentStreak ? `${m.currentStreak}\u{1F525}` : "0", label: "Streak" },
        { value: m.maxStreak != null ? String(m.maxStreak) : "0", label: "Best streak" },
        { value: formatAverage(m.avgGuesses), label: "Avg guesses" },
    ];
}

/** Guess distribution as horizontal bars; the bucket of today's win is lit. */
export function renderDistribution(node, distribution, highlight) {
    const rows = distributionRows(distribution, highlight);
    const empty = rows.every((r) => r.count === 0);
    if (empty) {
        node.replaceChildren(el("p", { class: "gl-muted", text: "Win a daily puzzle to start your chart." }));
        return;
    }
    node.replaceChildren(el("ol", { class: "gl-dist", "aria-label": "Daily wins by number of guesses" },
        rows.map((r) => el("li", { class: r.highlight ? "gl-dist-row is-today" : "gl-dist-row" },
            el("span", { class: "gl-dist-label", "aria-hidden": "true", text: r.bucket }),
            el("span", { class: "gl-dist-track", "aria-hidden": "true" },
                el("span", { class: "gl-dist-bar", vars: { "--w": r.width } },
                    el("span", { class: "gl-dist-n", text: String(r.count) }))),
            el("span", { class: "sr-only", text: `${r.bucket} ${r.bucket === "1" ? "guess" : "guesses"}: ${plural(r.count, "win")}${r.highlight ? " (today)" : ""}` })))));
}

// ---------------------------------------------------------------------- leaderboard

const MEDALS = { 1: "\u{1F947}", 2: "\u{1F948}", 3: "\u{1F949}" };
const STATUS = {
    WON: { label: "Won", cls: "is-won" },
    PLAYING: { label: "Playing", cls: "is-playing" },
    GAVE_UP: { label: "Gave up", cls: "is-gaveup" },
};

function who(row) {
    return el("span", { class: "gl-who" },
        el("span", { class: "gl-who-name", text: row.username }),
        row.you ? el("span", { class: "gl-you", text: "you" }) : null);
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

export function renderToday(node, today) {
    if (!today) {
        node.replaceChildren(emptyNote("No puzzle today."));
        return;
    }
    const parts = [el("p", { class: "gl-board-meta", text: `Puzzle #${today.puzzle} · ${formatPuzzleDate(today.date)}` })];
    // The race is visible while you play: entries carry only a status and a guess
    // count, never a country, so there is nothing here to spoil.
    if (!today.viewerFinished) {
        parts.push(el("p", { class: "gl-board-lock" }, icon("bi-eye-slash"), " Still playing? Only guess counts show here, never the answer."));
    }
    const entries = today.entries || [];
    if (entries.length === 0) {
        parts.push(emptyNote("Nobody has guessed yet today. Be the first!"));
    } else {
        parts.push(el("ol", { class: "gl-today" }, entries.map((e) => {
            const status = STATUS[e.status] || STATUS.PLAYING;
            const place = e.rank ? (MEDALS[e.rank] || `#${e.rank}`) : "–";
            return el("li", { class: e.you ? "gl-today-row is-you" : "gl-today-row" },
                el("span", { class: e.rank && e.rank <= 3 ? "gl-place is-medal" : "gl-place", text: place,
                    "aria-label": e.rank ? `Rank ${e.rank}` : null }),
                who(e),
                el("span", { class: `gl-status ${status.cls}`, text: status.label }),
                el("span", { class: "gl-today-n", text: plural(e.guessCount || 0, "guess", "guesses") }));
        })));
    }
    node.replaceChildren(...parts);
}

export function renderWeek(node, week) {
    const rows = week || [];
    if (!rows.length) {
        node.replaceChildren(emptyNote("No daily games in the last seven days."));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: "Daily puzzles, last 7 days" }),
        table("Daily results for the last seven days",
            [{ label: "Player" }, { label: "Played" }, { label: "Wins" }, { label: "Avg", title: "Average guesses per win" }],
            rows.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: String(r.played) }),
                el("td", { class: "num", text: String(r.wins) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) })))));
}

export function renderAllTime(node, allTime) {
    const rows = allTime || [];
    if (!rows.length) {
        node.replaceChildren(emptyNote("No daily games yet."));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: "Daily puzzles, all time" }),
        table("Daily results, all time",
            [{ label: "Player" }, { label: "Wins" }, { label: "Win %" }, { label: "Avg", title: "Average guesses per win" },
             { label: "Streak", title: "Current streak (best)" }, { label: "Best", title: "Fewest guesses", hideNarrow: true }],
            rows.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: String(r.wins) }),
                el("td", { class: "num", text: formatPercent(r.played ? r.winRate : null) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) }),
                el("td", { class: "num gl-streak" },
                    r.currentStreak ? `${r.currentStreak}\u{1F525}` : "0",
                    el("span", { class: "gl-streak-max", text: ` (${r.maxStreak || 0})` })),
                el("td", { class: "num hide-narrow", text: r.best != null ? String(r.best) : "—" })))));
}

export function renderUnlimited(node, rows, kind) {
    const list = rows || [];
    const what = kind === "capital" ? "Capitals" : "Unlimited";
    if (!list.length) {
        node.replaceChildren(emptyNote(`No finished ${what} games yet.`));
        return;
    }
    node.replaceChildren(
        el("p", { class: "gl-board-meta", text: `${what}, all finished games` }),
        table(`${what} results`,
            [{ label: "Player" }, { label: "Played" }, { label: "Wins" }, { label: "Avg", title: "Average guesses per win" },
             { label: "Best", title: "Fewest guesses" }],
            list.map((r) => el("tr", { class: r.you ? "is-you" : null },
                el("th", { scope: "row" }, who(r)),
                el("td", { class: "num", text: String(r.played) }),
                el("td", { class: "num", text: String(r.wins) }),
                el("td", { class: "num", text: formatAverage(r.avgGuesses) }),
                el("td", { class: "num", text: r.best != null ? String(r.best) : "—" })))));
}

/** One line summarising a player's unlimited record: "12 wins from 15 · avg 5.2 · best 2". */
export function unlimitedSummary(row) {
    if (!row || !row.played) return "No finished games yet.";
    const parts = [`${plural(row.wins, "win")} from ${row.played}`];
    if (row.avgGuesses != null) parts.push(`avg ${formatAverage(row.avgGuesses)}`);
    if (row.best != null) parts.push(`best ${row.best}`);
    return parts.join(" · ");
}

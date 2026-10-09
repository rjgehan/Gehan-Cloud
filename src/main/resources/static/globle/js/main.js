// Globle: the page controller.
//
// Owns the game state for each mode and keeps the panel and the globe in step
// with it. The server is the source of truth: every guess, give-up and new game
// returns a full GameState, and the page re-renders from that rather than
// patching itself, so a reload, a second device or a midnight rollover can never
// leave the screen disagreeing with the server.
//
// Three modes, each with its own state kept while you switch between them:
//   daily      today's puzzle, the same for everyone; streaks and leaderboard
//   unlimited  a random country whenever you like, optionally within a region
//   capitals   the same, but the mystery is a capital city
// Blind is a modifier on top of any of them, and purely cosmetic.

import { api } from "./api.js";
import { Globe } from "./globe.js";
import { Autocomplete } from "./autocomplete.js";
import { buildIndex } from "./match.js";
import { guessColor, legendGradient } from "./scale.js";
import { shareText } from "./share.js";
import { closestGuess, latestGuess, newGuess, highlightFor, ownRow } from "./stats.js";
import { flagEmoji, formatDistance, formatCountdown, regionLabel, plural, REGIONS } from "./format.js";
import { getPref, setPref, getChoice } from "./prefs.js";
import { celebrate } from "./celebrate.js";
import {
    $, $$, el, setupDialog, openDialog, confirmSheet, toast, announce,
    renderGuesses, renderClosest, renderTiles, dailyTiles, renderDistribution,
    renderToday, renderWeek, renderAllTime, renderUnlimited, unlimitedSummary,
} from "./ui.js";

const MODES = {
    daily: { kind: "country", label: "Daily", icon: "bi-calendar2-check" },
    unlimited: { kind: "country", label: "Unlimited", icon: "bi-infinity" },
    capitals: { kind: "capital", label: "Capitals", icon: "bi-bank2" },
};
const MODE_NAMES = Object.keys(MODES);
const BOARD_TABS = ["today", "week", "all", "unlimited"];

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const coarsePointer = window.matchMedia("(pointer: coarse)").matches;

const body = document.body;

const app = {
    mode: "daily",
    // undefined: not loaded yet; null: no game (unlimited 204); else a GameState
    games: { daily: undefined, unlimited: undefined, capitals: undefined },
    loadErrors: { daily: null, unlimited: null, capitals: null },
    regions: {
        unlimited: getChoice("region.unlimited", REGIONS, "world"),
        capitals: getChoice("region.capitals", REGIONS, "world"),
    },
    countries: [],
    byCode: new Map(),
    indexes: { country: null, capital: null },
    board: null,
    boardError: null,
    boardLoading: null,
    boardWanted: false,
    boardTab: getChoice("boardTab", BOARD_TABS, "today"),
    boardKind: getChoice("boardKind", ["country", "capital"], "country"),
    units: getChoice("units", ["km", "mi"], "km"),
    blind: getPref("blind", "0") === "1",
    busy: false,
    signedOut: false,
    puzzle: Number(body.dataset.puzzle) || null,
    copiedTimer: 0,
    tipTimer: 0,
};

// ------------------------------------------------------------------ elements

const dom = {
    titleTag: $("#title-tag"),
    // Scoped to the switcher: <body> carries data-mode too, for the CSS accent.
    modeButtons: $$(".gl-seg-btn[data-mode]"),
    regionWrap: $("#region-wrap"),
    region: $("#region"),
    blind: $("#btn-blind"),
    stage: $("#stage"),
    canvas: $("#globe"),
    stageBadge: $("#stage-badge"),
    stageBlind: $("#stage-blind"),
    stageHint: $("#stage-hint"),
    stageStatus: $("#stage-status"),
    tip: $("#tip"),
    practice: $("#practice-note"),
    panelStatus: $("#panel-status"),
    start: $("#start-card"),
    startTitle: $("#start-title"),
    startCopy: $("#start-copy"),
    startBtn: $("#btn-start"),
    end: $("#end-card"),
    form: $("#guess-form"),
    input: $("#guess-input"),
    go: $("#guess-go"),
    options: $("#guess-options"),
    error: $("#guess-error"),
    closest: $("#closest"),
    listWrap: $("#guess-section"),
    list: $("#guesses"),
    count: $("#guess-count"),
    empty: $("#guesses-empty"),
    unitButtons: $$("[data-units]"),
    actions: $("#play-actions"),
    giveUp: $("#btn-giveup"),
    skip: $("#btn-skip"),
    session: $("#session-banner"),
    help: $("#dlg-help"),
    stats: $("#dlg-stats"),
    board: $("#dlg-board"),
    confirm: $("#dlg-confirm"),
};

// ------------------------------------------------------------------ globe

const globe = new Globe(dom.canvas, {
    reducedMotion,
    onHover: showTip,
    onInteract: () => { dom.stageHint.hidden = true; },
});
globe.setBlind(app.blind);

// ------------------------------------------------------------------ guess box

const autocomplete = new Autocomplete({
    input: dom.input,
    listbox: dom.options,
    onPick: (code) => guess(code),
    onType: clearError,
    onProblem: (kind, result) => {
        if (kind === "already-guessed") showError(`You already guessed ${result.label}.`);
        else if (kind === "choose") showError(`Did you mean ${result.label}? Pick it from the list, or press Enter again.`);
        else showError(currentKind() === "capital"
            ? "No capital matches that. Try another spelling."
            : "No country matches that. Try another spelling.");
    },
});

// ------------------------------------------------------------------ helpers

function currentState() {
    return app.games[app.mode];
}

function currentKind() {
    return MODES[app.mode].kind;
}

function puzzleNumber() {
    const daily = app.games.daily;
    return daily && daily.puzzle != null ? daily.puzzle : app.puzzle;
}

function countryName(code) {
    const c = app.byCode.get(code);
    return c ? c.name : code;
}

/** Where a guess sits on the globe: its capital in capitals mode, else its middle. */
function pointForGuess(guess, kind) {
    if (kind === "capital" && guess.capital) return [guess.capital.lng, guess.capital.lat];
    return globe.centreOf(guess.code);
}

function pointForAnswer(state) {
    const a = state.answer;
    if (!a) return null;
    if (state.kind === "capital" && a.capital) return [a.capital.lng, a.capital.lat];
    if (Number.isFinite(a.lng) && Number.isFinite(a.lat)) return [a.lng, a.lat];
    return globe.centreOf(a.code);
}

function regionForNewGame(mode) {
    return app.regions[mode] || "world";
}

// ------------------------------------------------------------------ errors

function showError(text) {
    if (dom.form.hidden) {
        toast(text, 3600);
        return;
    }
    // The error paragraph is a live region that is always present (just empty),
    // so changing its text is announced; un-hiding a live region often is not.
    dom.error.textContent = text;
    dom.input.setAttribute("aria-invalid", "true");
}

function clearError() {
    dom.error.textContent = "";
    dom.input.removeAttribute("aria-invalid");
}

const TRANSPORT_MESSAGES = {
    offline: "You seem to be offline, so nothing was sent. Try again once you're connected.",
    timeout: "The server took too long to answer. Your game has been reloaded to be sure.",
    server: "The server hit a problem. Try again in a moment.",
    "bad-response": "The server sent something unexpected. Try reloading the page.",
};

/** Problems any request can have. Returns true if it was handled. */
function isAuthError(error) {
    return !!error && (error.code === "signed-out" || error.code === "forbidden");
}

/** mode is the one the request was for, which may no longer be on screen. */
function handleCommonError(error, mode = app.mode) {
    if (isAuthError(error)) {
        showSessionExpired(error.code);
        return true;
    }
    showError(TRANSPORT_MESSAGES[error && error.code] || "Something went wrong. Try again.");
    if (error && error.code === "timeout") reloadMode(mode);
    return true;
}

function handleGameError(error, code, mode = app.mode) {
    const kind = MODES[mode].kind;
    switch (error && error.code) {
        case "unknown-country":
            showError(kind === "capital" ? "That isn't a capital we know. Pick one from the list." : "That isn't a country we know. Pick one from the list.");
            break;
        case "already-guessed":
            showError(`You already guessed ${countryName(code)}.`);
            break;
        case "no-capital":
            showError(`${countryName(code)} has no capital to guess. Try another.`);
            break;
        case "finished":
            // Not showError: the reload is about to hide the form it would land in.
            // The typed guess was for that game, so it goes too.
            clearError();
            if (mode === app.mode) autocomplete.clear();
            toast("That game already ended (maybe on another device). Loading your current one.", 3600);
            reloadMode(mode);
            break;
        case "not-found":
            toast("That game could not be found. Loading your current one.", 3600);
            reloadMode(mode);
            break;
        case "new-day":
            // The page was still showing yesterday's puzzle; the server refused
            // rather than count this against today's.
            clearError();
            if (app.mode === "daily") autocomplete.clear();
            toast("A new puzzle started at midnight. Nothing was counted.", 4000);
            reloadMode("daily");
            break;
        case "bad-request":
            showError("That didn't go through. Try again.");
            break;
        default:
            handleCommonError(error, mode);
    }
}

/** Stops play and shows the banner: sign in again, or (stale token) just reload. */
function showSessionExpired(reason = "signed-out") {
    if (app.signedOut) return;
    app.signedOut = true;
    if (reason === "forbidden") {
        $("#session-text").textContent = "This page is out of date, so guesses can't be saved.";
        const link = $("#session-link");
        link.textContent = "Reload";
        link.setAttribute("href", "/globle");
    }
    dom.session.hidden = false;
    autocomplete.setLocked(true);
    for (const b of [dom.go, dom.giveUp, dom.skip, dom.startBtn]) b.disabled = true;
    announce("Your session has expired. Sign in again to keep playing.");
}

function setBusy(busy) {
    app.busy = busy;
    if (app.signedOut) return;
    autocomplete.setLocked(busy);
    dom.go.disabled = busy;
    dom.go.classList.toggle("is-busy", busy);
    for (const b of [dom.giveUp, dom.skip, dom.startBtn, $("#btn-new-after")]) {
        if (b) b.disabled = busy;
    }
    dom.panelStatus.setAttribute("aria-busy", busy ? "true" : "false");
}

// ------------------------------------------------------------------ rendering

function render({ fresh = null } = {}) {
    const mode = app.mode;
    const meta = MODES[mode];
    const state = app.games[mode];
    const loading = state === undefined && !app.loadErrors[mode];
    const failed = state === undefined && !!app.loadErrors[mode];
    const none = state === null;
    const playing = !!state && state.status === "PLAYING";
    const finished = !!state && !playing;

    body.dataset.mode = mode;
    for (const b of dom.modeButtons) b.setAttribute("aria-pressed", b.dataset.mode === mode ? "true" : "false");

    // Header and badges: never let practice look like today's puzzle.
    const number = puzzleNumber();
    dom.titleTag.textContent = mode === "daily" ? (number ? `#${number}` : "") : meta.label;
    const region = state && state.region ? state.region : regionForNewGame(mode);
    dom.stageBadge.replaceChildren(
        el("i", { class: `bi ${meta.icon}`, "aria-hidden": "true" }),
        mode === "daily" ? ` Daily${number ? ` #${number}` : ""}` : ` ${meta.label} · ${regionLabel(region)}`);
    dom.practice.hidden = mode === "daily";

    dom.regionWrap.hidden = mode === "daily";
    if (mode !== "daily") dom.region.value = playing ? state.region : regionForNewGame(mode);

    // Which panel parts show.
    dom.panelStatus.hidden = !(loading || failed);
    if (loading) {
        dom.panelStatus.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }), " Loading your game…");
    } else if (failed) {
        const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        retry.addEventListener("click", () => reloadMode(mode));
        dom.panelStatus.replaceChildren(el("span", { text: "Couldn't load this game. " }), retry);
    }

    dom.start.hidden = !none;
    if (none) renderStart(mode);
    dom.form.hidden = !playing;
    dom.end.hidden = !finished;
    if (finished) renderEnd(state);
    dom.closest.hidden = !playing;
    dom.listWrap.hidden = !state;
    dom.actions.hidden = !playing;
    dom.skip.hidden = mode === "daily";

    if (state) {
        const answerCode = state.answer ? state.answer.code : null;
        const latest = latestGuess(state.guesses);
        renderClosest(dom.closest, { state, units: app.units, closest: closestGuess(state.guesses, answerCode) });
        renderGuesses(dom.list, { state, units: app.units, latestCode: latest ? latest.code : null, fresh: !!fresh });
        dom.count.textContent = String(state.guesses.length);
        dom.empty.hidden = state.guesses.length > 0;
        dom.empty.textContent = state.kind === "capital"
            ? "No guesses yet. Any capital is a fine place to start."
            : "No guesses yet. Any country is a fine place to start.";
        dom.input.placeholder = state.kind === "capital" ? "Guess a capital…" : "Guess a country…";
        $("#guess-label").textContent = state.kind === "capital" ? "Guess a capital city" : "Guess a country";
        autocomplete.setGuessed(state.guesses.map((g) => g.code));
    }

    syncGlobe();
}

function syncGlobe() {
    const mode = app.mode;
    const state = app.games[mode];
    if (!state) {
        globe.setGame({ kind: MODES[mode].kind, region: mode === "daily" ? "world" : regionForNewGame(mode), guesses: [], status: "PLAYING" });
        return;
    }
    const answerCode = state.status !== "PLAYING" && state.answer ? state.answer.code : null;
    const latest = latestGuess(state.guesses);
    globe.setGame({
        kind: state.kind,
        region: state.region || "world",
        guesses: state.guesses.map((g) => ({
            code: g.code,
            color: guessColor(g, answerCode, state.kind),
            capital: g.capital || null,
        })),
        latestCode: latest ? latest.code : null,
        answer: answerCode ? state.answer : null,
        status: state.status,
    });
    if (state.guesses.length) dom.stageHint.hidden = true;
}

/** Points the camera at whatever matters now: the answer, the last guess, or the region. */
function aimGlobe() {
    if (!globe.ready) return;
    const state = currentState();
    if (state && state.status !== "PLAYING") {
        globe.flyTo(pointForAnswer(state));
        return;
    }
    const latest = state ? latestGuess(state.guesses) : null;
    if (latest) {
        globe.flyTo(pointForGuess(latest, state.kind));
        return;
    }
    const region = state ? state.region : (app.mode === "daily" ? "world" : regionForNewGame(app.mode));
    globe.focusRegion(region || "world");
}

function renderStart(mode) {
    const meta = MODES[mode];
    const region = regionForNewGame(mode);
    dom.startTitle.textContent = meta.label;
    dom.startCopy.textContent = mode === "capitals"
        ? "Find a mystery capital city. Each guess is a capital, and the distance is measured city to city. Doesn't touch your daily streak."
        : "A fresh mystery country whenever you want one. Pick a region above to narrow it down. Doesn't touch your daily streak.";
    $("#btn-start-label").textContent = region === "world" ? "Start a game" : `Start a game in ${regionLabel(region)}`;
}

function renderEnd(state) {
    const won = state.status === "WON";
    const answer = state.answer || {};
    const capitalMode = state.kind === "capital";
    dom.end.classList.toggle("is-won", won);
    dom.end.classList.toggle("is-gaveup", !won);

    $("#end-kicker").textContent = won
        ? (state.guessCount === 1 ? "First try. Unreal!" : "You found it!")
        : "The answer was";
    $("#end-flag").textContent = flagEmoji(answer.iso2);
    if (capitalMode && answer.capital) {
        $("#end-name").textContent = answer.capital.name;
        $("#end-sub").textContent = `Capital of ${answer.name}`;
    } else {
        $("#end-name").textContent = answer.name || "";
        $("#end-sub").textContent = answer.capital ? `Capital: ${answer.capital.name}` : "";
    }
    $("#end-count").textContent = won
        ? `Found in ${plural(state.guessCount, "guess", "guesses")}`
        : `Revealed after ${plural(state.guessCount, "guess", "guesses")}`;

    const daily = state.mode === "daily";
    $("#end-daily").hidden = !daily;
    $("#end-unlimited").hidden = daily;
    $("#btn-new-after").hidden = daily;
    $("#btn-try-unlimited").hidden = !daily;

    const me = app.board ? app.board.me : null;
    if (daily) {
        renderTiles($("#end-tiles"), dailyTiles(me));
        $("#end-tiles").classList.toggle("is-loading", !app.board || !!app.boardLoading);
        renderDistribution($("#end-dist"), me ? me.distribution : null, highlightFor(state));
        tickCountdown();
    } else {
        const rows = app.board && app.board.unlimited ? app.board.unlimited[state.kind] : null;
        $("#end-record").textContent = app.board
            ? `Your ${capitalMode ? "Capitals" : "Unlimited"} record: ${unlimitedSummary(ownRow(rows))}`
            : "";
        const region = regionForNewGame(app.mode);
        $("#btn-new-after-label").textContent = region === "world" ? "New game" : `New game in ${regionLabel(region)}`;
    }
}

function tickCountdown() {
    const daily = app.games.daily;
    const node = $("#countdown");
    const ready = $("#btn-next-daily");
    if (!daily || !daily.nextPuzzleAt) {
        $("#end-next").hidden = true;
        return;
    }
    const ms = Date.parse(daily.nextPuzzleAt) - Date.now();
    if (Number.isNaN(ms)) {
        $("#end-next").hidden = true;
        return;
    }
    $("#end-next").hidden = ms <= 0;
    ready.hidden = ms > 0;
    node.textContent = formatCountdown(ms);
}

// ------------------------------------------------------------------ loading

async function loadMode(mode) {
    app.loadErrors[mode] = null;
    try {
        app.games[mode] = mode === "daily" ? await api.daily() : await api.current(MODES[mode].kind);
        if (mode === "daily" && app.games.daily && app.games.daily.puzzle != null) app.puzzle = app.games.daily.puzzle;
    } catch (error) {
        app.loadErrors[mode] = error;
        if (isAuthError(error)) showSessionExpired(error.code);
    }
}

async function reloadMode(mode) {
    app.games[mode] = undefined;
    if (mode === app.mode) render();
    await loadMode(mode);
    if (mode === app.mode) {
        render();
        aimGlobe();
    }
}

/** Swaps a daily left open past midnight for today's, and says so. */
async function refreshDaily() {
    const before = app.games.daily;
    await reloadMode("daily");
    const after = app.games.daily;
    if (app.mode === "daily" && before && after && before.puzzle != null && after.puzzle != null
            && before.puzzle !== after.puzzle) {
        toast(`A new day has started. This is puzzle #${after.puzzle}.`, 4000);
    }
}

async function switchMode(mode) {
    if (!MODES[mode]) return;
    app.mode = mode;
    setPref("mode", mode);
    clearError();
    hideTip();
    autocomplete.setIndex(app.indexes[MODES[mode].kind]);
    render();
    aimGlobe();
    if (app.games[mode] === undefined) {
        await loadMode(mode);
        if (app.mode !== mode) return;
        render();
        aimGlobe();
    }
    if (currentState() && currentState().status !== "PLAYING" && !app.board) loadBoard();
}

// ------------------------------------------------------------------ playing

async function guess(code) {
    const mode = app.mode;
    const state = app.games[mode];
    if (app.busy || app.signedOut || !state || state.status !== "PLAYING") return;
    clearError();
    setBusy(true);
    try {
        const next = mode === "daily" ? await api.guessDaily(code, state.date) : await api.guessUnlimited(state.id, code);
        autocomplete.clear();
        applyState(mode, next, state);
        if (coarsePointer) dom.input.blur();
    } catch (error) {
        handleGameError(error, code, mode);
    } finally {
        setBusy(false);
        if (!coarsePointer && !dom.form.hidden && app.mode === mode) dom.input.focus({ preventScroll: true });
    }
}

/** Takes a fresh GameState from the server and shows what changed. */
function applyState(mode, next, prev) {
    app.games[mode] = next;
    if (mode === "daily" && next.puzzle != null) {
        if (prev && prev.puzzle != null && prev.puzzle !== next.puzzle) {
            toast(`A new day has started. This is puzzle #${next.puzzle}.`, 4000);
        }
        app.puzzle = next.puzzle;
    }
    if (mode !== app.mode) return;

    const added = newGuess(prev, next);
    const justFinished = (!prev || prev.status === "PLAYING") && next.status !== "PLAYING";
    render({ fresh: added ? added.code : null });

    if (justFinished) {
        finishGame(next);
        return;
    }
    if (added) {
        globe.flyTo(pointForGuess(added, next.kind));
        const closest = closestGuess(next.guesses, null);
        // In capitals mode the distance is the capital's, so name the capital.
        const label = (g) => (next.kind === "capital" && g.capital ? `${g.capital.name}, ${g.name}` : g.name);
        let message = `${label(added)}: ${formatDistance(added.km, app.units)} away.`;
        if (added.km === 0 && next.kind !== "capital") message += " It borders the mystery country.";
        if (closest && closest.code !== added.code) message += ` Closest so far: ${label(closest)}, ${formatDistance(closest.km, app.units)}.`;
        announce(message);
    }
}

function finishGame(state) {
    const won = state.status === "WON";
    const point = pointForAnswer(state);
    globe.flyTo(point);
    const answerName = state.kind === "capital" && state.answer && state.answer.capital
        ? `${state.answer.capital.name}, ${state.answer.name}`
        : (state.answer ? state.answer.name : "");
    if (won) {
        globe.pulse(point);
        celebrate({ reducedMotion });
        announce(`Correct! It was ${answerName}. Found in ${plural(state.guessCount, "guess", "guesses")}.`);
    } else {
        announce(`The answer was ${answerName}.`);
    }
    // Stats and the leaderboard just changed.
    loadBoard();
    if (!coarsePointer) {
        const target = $("#btn-share");
        if (target) target.focus({ preventScroll: true });
    }
    // On a phone the globe is above the card. Keep the reveal in view and just
    // make sure the top of the card peeks up from the bottom of the screen.
    const top = dom.end.getBoundingClientRect().top;
    const peek = window.innerHeight - 140;
    if (top > peek) {
        window.scrollBy({ top: top - peek, behavior: reducedMotion ? "auto" : "smooth" });
    }
}

async function giveUp() {
    const mode = app.mode;
    const state = app.games[mode];
    if (app.busy || app.signedOut || !state || state.status !== "PLAYING") return;
    const streak = app.board && app.board.me ? app.board.me.currentStreak : 0;
    const ok = await confirmSheet(dom.confirm, {
        title: "Give up?",
        body: mode === "daily"
            ? `The answer will be revealed and today's puzzle counts as a loss${streak ? `, ending your ${streak}-day streak` : ""}.`
            : "The answer will be revealed and this game counts as a loss.",
        confirm: "Reveal the answer",
        cancel: "Keep playing",
        danger: true,
    });
    if (!ok || app.mode !== mode) return;
    setBusy(true);
    try {
        // state.date is the puzzle the sheet was about. If midnight passed while it
        // was open, the server refuses ("new-day") instead of forfeiting today's.
        const next = mode === "daily" ? await api.giveUpDaily(state.date) : await api.giveUpUnlimited(state.id);
        applyState(mode, next, state);
    } catch (error) {
        handleGameError(error, null, mode);
    } finally {
        setBusy(false);
    }
}

/**
 * Starts a new unlimited/capitals game. If one is in progress with guesses, the
 * server records it as given up, so the player is told that first, in the page.
 * One with no guesses yet is simply dropped, so that needs no warning.
 * Resolves false if they backed out, or if it turned out a game with guesses was
 * already in progress (played on another device since this page loaded), which is
 * then shown.
 */
async function newGame({ region = regionForNewGame(app.mode) } = {}) {
    const mode = app.mode;
    if (mode === "daily" || app.busy || app.signedOut) return false;
    const state = app.games[mode];
    const abandoning = !!state && state.status === "PLAYING" && state.guessCount > 0;
    if (!abandoning) {
        // This page thinks there is nothing to lose, but another device may have
        // guessed since. Starting over would silently give that game up.
        let fresh;
        setBusy(true);
        try {
            fresh = await api.current(MODES[mode].kind);
        } catch (error) {
            handleCommonError(error, mode);
            return false;
        } finally {
            setBusy(false);
        }
        if (fresh && fresh.status === "PLAYING" && fresh.guessCount > 0) {
            app.games[mode] = fresh;
            app.loadErrors[mode] = null;
            if (app.mode === mode) {
                clearError();
                render();
                aimGlobe();
                toast("You have a game in progress from another device.", 3600);
            }
            return false;
        }
        if (app.mode !== mode) return false;
    }
    if (abandoning) {
        const ok = await confirmSheet(dom.confirm, {
            title: region === state.region ? "Start a new game?" : `Switch to ${regionLabel(region)}?`,
            body: `Your current ${regionLabel(state.region)} game will count as given up`
                + ` after ${plural(state.guessCount, "guess", "guesses")}. The answer won't be shown.`,
            confirm: "Start new game",
            cancel: "Keep playing",
        });
        if (!ok || app.mode !== mode) return false;
    }
    setBusy(true);
    try {
        const next = await api.newGame(MODES[mode].kind, region);
        app.games[mode] = next;
        app.loadErrors[mode] = null;
        if (app.mode === mode) {
            clearError();
            render();
            globe.focusRegion(next.region || region);
            announce(`New ${MODES[mode].label} game${next.region && next.region !== "world" ? ` in ${regionLabel(next.region)}` : ""}. Make your first guess.`);
            if (!coarsePointer) dom.input.focus({ preventScroll: true });
        }
        if (abandoning) {
            toast("Your last game counted as given up.");
            loadBoard();
        }
        return true;
    } catch (error) {
        handleCommonError(error, mode);
        return false;
    } finally {
        setBusy(false);
    }
}

// ------------------------------------------------------------------ share

async function shareResult() {
    const state = currentState();
    if (!state || state.status === "PLAYING") return;
    // A win refreshes the stats; give that a moment so the streak shared is the
    // new one. Short, because the share sheet needs the tap to still be "recent".
    if (state.mode === "daily" && app.boardLoading) {
        await Promise.race([app.boardLoading, new Promise((resolve) => setTimeout(resolve, 800))]);
    }
    const streak = state.mode === "daily" && app.board && app.board.me ? app.board.me.currentStreak : 0;
    const text = shareText(state, { streak, site: `${window.location.host}/globle` });

    if (coarsePointer && typeof navigator.share === "function") {
        try {
            await navigator.share({ text });
            return;
        } catch (error) {
            if (error && error.name === "AbortError") return;
            // Otherwise fall through to copying.
        }
    }
    if (await copyText(text)) {
        const button = $("#btn-share-label");
        button.textContent = "Copied!";
        clearTimeout(app.copiedTimer);
        app.copiedTimer = setTimeout(() => { button.textContent = "Share"; }, 2000);
        toast("Copied! Paste it in the family chat.");
    } else {
        // No window.prompt: show the text in the page, selected, ready to copy.
        toast("Couldn't copy automatically. Select the text and copy it.", 4000);
        showShareFallback(text);
    }
}

async function copyText(text) {
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

function showShareFallback(text) {
    const box = $("#share-fallback");
    box.value = text;
    box.hidden = false;
    box.focus();
    box.select();
}

// ------------------------------------------------------------------ leaderboard & stats

/**
 * Fetches the leaderboard (which also carries the viewer's own stats). A call
 * made while a fetch is already in flight queues one more afterwards: that
 * earlier response may predate the game that just ended.
 */
function loadBoard() {
    app.boardWanted = true;
    if (app.boardLoading) return app.boardLoading;
    app.boardLoading = (async () => {
        while (app.boardWanted) {
            app.boardWanted = false;
            try {
                app.board = await api.leaderboard();
                app.boardError = null;
            } catch (error) {
                app.boardError = error;
                if (isAuthError(error)) showSessionExpired(error.code);
                break;
            }
        }
        app.boardLoading = null;
        renderBoard();
        renderStats();
        const state = currentState();
        if (state && state.status !== "PLAYING") renderEnd(state);
    })();
    renderBoard();
    renderStats();
    return app.boardLoading;
}

function boardPlaceholder(node, retry) {
    if (app.boardError) {
        const button = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        button.addEventListener("click", retry);
        node.replaceChildren(el("p", { class: "gl-muted gl-board-empty" }, "Couldn't load the leaderboard. ", button));
    } else {
        node.replaceChildren(el("p", { class: "gl-muted gl-board-empty" },
            el("span", { class: "gl-spinner", "aria-hidden": "true" }), " Loading…"));
    }
}

function renderBoard() {
    const panel = $("#board-panel");
    for (const tab of $$("[role=tab]", dom.board)) {
        const selected = tab.dataset.tab === app.boardTab;
        tab.setAttribute("aria-selected", selected ? "true" : "false");
        tab.tabIndex = selected ? 0 : -1;
        if (selected) panel.setAttribute("aria-labelledby", tab.id);
    }
    const sub = $("#board-sub");
    sub.hidden = app.boardTab !== "unlimited";
    for (const b of $$("[data-kind]", sub)) b.setAttribute("aria-pressed", b.dataset.kind === app.boardKind ? "true" : "false");

    const board = app.board;
    if (!board) {
        boardPlaceholder(panel, loadBoard);
        return;
    }
    switch (app.boardTab) {
        case "week": renderWeek(panel, board.week); break;
        case "all": renderAllTime(panel, board.allTime); break;
        case "unlimited": renderUnlimited(panel, board.unlimited ? board.unlimited[app.boardKind] : [], app.boardKind); break;
        default: renderToday(panel, board.today);
    }
}

function renderStats() {
    const board = app.board;
    const tiles = $("#stats-tiles");
    if (!board) {
        boardPlaceholder($("#stats-dist"), loadBoard);
        renderTiles(tiles, dailyTiles(null));
        tiles.classList.add("is-loading");
        $("#stats-unlimited").textContent = "";
        $("#stats-capitals").textContent = "";
        return;
    }
    tiles.classList.remove("is-loading");
    renderTiles(tiles, dailyTiles(board.me));
    const daily = app.games.daily;
    renderDistribution($("#stats-dist"), board.me ? board.me.distribution : null, highlightFor(daily));
    const unlimited = board.unlimited || {};
    $("#stats-unlimited").textContent = unlimitedSummary(ownRow(unlimited.country));
    $("#stats-capitals").textContent = unlimitedSummary(ownRow(unlimited.capital));
}

function selectBoardTab(tab, focus) {
    if (!BOARD_TABS.includes(tab)) return;
    app.boardTab = tab;
    setPref("boardTab", tab);
    renderBoard();
    if (focus) $(`[role=tab][data-tab="${tab}"]`, dom.board).focus();
}

// ------------------------------------------------------------------ globe tooltip

function hideTip() {
    clearTimeout(app.tipTimer);
    dom.tip.hidden = true;
}

function showTip(hit) {
    clearTimeout(app.tipTimer);
    const state = currentState();
    if (!hit || !state) {
        dom.tip.hidden = true;
        return;
    }
    const answer = state.status !== "PLAYING" ? state.answer : null;
    const g = state.guesses.find((x) => x.code === hit.code);
    const isAnswer = !!answer && hit.code === answer.code;
    let title;
    let iso2;
    if (g) {
        iso2 = g.iso2;
        title = state.kind === "capital" && g.capital ? `${g.capital.name}, ${g.name}` : g.name;
    } else if (isAnswer) {
        iso2 = answer.iso2;
        title = state.kind === "capital" && answer.capital ? `${answer.capital.name}, ${answer.name}` : answer.name;
    } else {
        dom.tip.hidden = true;
        return;
    }
    const detail = isAnswer ? (state.status === "WON" ? "The answer!" : "The answer") : formatDistance(g.km, app.units);
    dom.tip.replaceChildren(
        el("span", { "aria-hidden": "true", text: flagEmoji(iso2) }),
        el("span", { class: "gl-tip-name", text: title }),
        el("span", { class: "gl-tip-km", text: detail }));
    dom.tip.hidden = false;
    // Keep it inside the stage.
    const stageRect = dom.stage.getBoundingClientRect();
    const tipRect = dom.tip.getBoundingClientRect();
    const x = Math.max(8 + tipRect.width / 2, Math.min(stageRect.width - 8 - tipRect.width / 2, hit.x));
    const above = hit.y - tipRect.height - 14 > 4;
    dom.tip.style.left = `${x}px`;
    dom.tip.style.top = `${above ? hit.y - tipRect.height - 14 : hit.y + 16}px`;
    if (hit.tap) app.tipTimer = setTimeout(hideTip, 2600);
}

// ------------------------------------------------------------------ wiring

function wire() {
    dom.stageHint.textContent = coarsePointer ? "Drag to spin · pinch to zoom" : "Drag to spin · scroll to zoom";

    for (const b of dom.modeButtons) {
        b.addEventListener("click", () => {
            if (b.dataset.mode !== app.mode) switchMode(b.dataset.mode);
        });
    }

    dom.region.replaceChildren(...REGIONS.map((r) => el("option", { value: r, text: regionLabel(r) })));
    dom.region.addEventListener("change", async () => {
        const mode = app.mode;
        if (mode === "daily") return;
        const region = dom.region.value;
        const previous = app.regions[mode];
        app.regions[mode] = region;
        setPref(`region.${mode}`, region);
        const state = app.games[mode];
        if (state && state.status === "PLAYING") {
            if (state.region === region) return;
            const started = await newGame({ region });
            if (!started) {
                app.regions[mode] = previous;
                setPref(`region.${mode}`, previous);
                render();
            }
        } else {
            render();
            if (!state) globe.focusRegion(region);
        }
    });

    dom.blind.setAttribute("aria-pressed", app.blind ? "true" : "false");
    dom.stageBlind.hidden = !app.blind;
    dom.blind.addEventListener("click", () => {
        app.blind = !app.blind;
        setPref("blind", app.blind ? "1" : "0");
        dom.blind.setAttribute("aria-pressed", app.blind ? "true" : "false");
        dom.stageBlind.hidden = !app.blind;
        globe.setBlind(app.blind);
        announce(app.blind ? "Blind mode on. Only your guesses are drawn." : "Blind mode off.");
    });

    for (const b of dom.unitButtons) {
        b.setAttribute("aria-pressed", b.dataset.units === app.units ? "true" : "false");
        b.addEventListener("click", () => {
            app.units = b.dataset.units;
            setPref("units", app.units);
            for (const x of dom.unitButtons) x.setAttribute("aria-pressed", x === b ? "true" : "false");
            render();
        });
    }

    dom.form.addEventListener("submit", (e) => {
        e.preventDefault();
        if (document.activeElement !== dom.input) dom.input.focus({ preventScroll: true });
        autocomplete.submit();
    });
    // Tapping the arrow should not blur the input and drop the phone keyboard.
    dom.go.addEventListener("mousedown", (e) => e.preventDefault());

    dom.giveUp.addEventListener("click", giveUp);
    dom.skip.addEventListener("click", () => newGame());
    dom.startBtn.addEventListener("click", () => newGame());
    $("#btn-new-after").addEventListener("click", () => newGame());
    $("#btn-try-unlimited").addEventListener("click", () => switchMode("unlimited"));
    $("#btn-share").addEventListener("click", shareResult);
    $("#btn-board-end").addEventListener("click", openBoard);
    $("#btn-next-daily").addEventListener("click", async () => {
        await reloadMode("daily");
        if (app.mode !== "daily") switchMode("daily");
    });

    $("#zoom-in").addEventListener("click", () => globe.zoomBy(1.5));
    $("#zoom-out").addEventListener("click", () => globe.zoomBy(1 / 1.5));
    $("#zoom-reset").addEventListener("click", () => {
        const state = currentState();
        const latest = state ? latestGuess(state.guesses) : null;
        globe.resetView(state && state.status !== "PLAYING" ? pointForAnswer(state)
            : latest ? pointForGuess(latest, state.kind) : null);
    });

    $("#btn-help").addEventListener("click", () => openDialog(dom.help));
    $("#btn-stats").addEventListener("click", () => {
        openDialog(dom.stats);
        loadBoard();
    });
    $("#btn-board").addEventListener("click", openBoard);

    for (const dialog of [dom.help, dom.stats, dom.board]) setupDialog(dialog);
    setupDialog(dom.confirm);
    $("#confirm-ok").addEventListener("click", () => dom.confirm.close("ok"));

    for (const tab of $$("[role=tab]", dom.board)) {
        tab.addEventListener("click", () => selectBoardTab(tab.dataset.tab, false));
        tab.addEventListener("keydown", (e) => {
            const i = BOARD_TABS.indexOf(app.boardTab);
            let next = null;
            if (e.key === "ArrowRight") next = BOARD_TABS[(i + 1) % BOARD_TABS.length];
            else if (e.key === "ArrowLeft") next = BOARD_TABS[(i - 1 + BOARD_TABS.length) % BOARD_TABS.length];
            else if (e.key === "Home") next = BOARD_TABS[0];
            else if (e.key === "End") next = BOARD_TABS[BOARD_TABS.length - 1];
            if (next) {
                e.preventDefault();
                selectBoardTab(next, true);
            }
        });
    }
    for (const b of $$("[data-kind]", $("#board-sub"))) {
        b.addEventListener("click", () => {
            app.boardKind = b.dataset.kind;
            setPref("boardKind", app.boardKind);
            renderBoard();
        });
    }

    // The colour legend in the rules is the real scale, not a picture of it.
    for (const bar of $$(".gl-legend-bar")) bar.style.setProperty("--grad", legendGradient());

    // "/" jumps to the guess box, as on most search-driven pages.
    document.addEventListener("keydown", (e) => {
        if (e.key !== "/" || e.ctrlKey || e.metaKey || e.altKey) return;
        const target = e.target;
        if (target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
        if (document.querySelector("dialog[open]") || dom.form.hidden) return;
        e.preventDefault();
        dom.input.focus();
    });

    // Coming back to the tab after midnight, or from the back/forward cache,
    // should not show yesterday's puzzle as today's.
    const isStale = (daily) => !!daily && !!daily.nextPuzzleAt && Date.parse(daily.nextPuzzleAt) <= Date.now();
    const refreshIfStale = () => {
        if (isStale(app.games.daily)) refreshDaily();
    };
    // A visible tab gets no visibilitychange at midnight, so an unfinished daily
    // also checks every second. Once per nextPuzzleAt: a client clock running
    // ahead of the server's would otherwise reload the same game every second.
    // The server is the real guard: a guess or give-up still aimed at yesterday
    // is refused with "new-day" rather than counted against today.
    let refreshedFor = null;
    setInterval(() => {
        tickCountdown();
        const daily = app.games.daily;
        if (!daily || daily.status !== "PLAYING" || app.busy || app.signedOut) return;
        if (daily.nextPuzzleAt === refreshedFor || !isStale(daily)) return;
        refreshedFor = daily.nextPuzzleAt;
        refreshDaily();
    }, 1000);
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden) refreshIfStale();
    });
    window.addEventListener("pageshow", (e) => {
        if (e.persisted) {
            for (const mode of MODE_NAMES) app.games[mode] = undefined;
            switchMode(app.mode);
        }
    });
}

function openBoard() {
    openDialog(dom.board, `[role=tab][data-tab="${app.boardTab}"]`);
    loadBoard();
}

// ------------------------------------------------------------------ start

async function loadWorld(regionOfPromise) {
    dom.stageStatus.hidden = false;
    dom.stageStatus.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }), " Loading the world…");
    try {
        const [topology, regionOf] = await Promise.all([api.world(), regionOfPromise]);
        globe.setWorld(topology, regionOf);
        dom.stageStatus.hidden = true;
        syncGlobe();
        aimGlobe();
    } catch (error) {
        if (isAuthError(error)) {
            showSessionExpired(error.code);
            return;
        }
        const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        retry.addEventListener("click", () => loadWorld(regionOfPromise));
        dom.stageStatus.replaceChildren(el("span", { text: "Couldn't load the globe. " }), retry);
    }
}

async function start() {
    wire();
    render();

    const countriesPromise = api.countries();
    const dailyPromise = loadMode("daily");
    const regionOfPromise = countriesPromise
        .then((list) => new Map(list.map((c) => [c.code, c.region])))
        .catch(() => new Map());
    loadWorld(regionOfPromise);

    try {
        app.countries = await countriesPromise;
    } catch (error) {
        if (isAuthError(error)) {
            showSessionExpired(error.code);
        } else {
            const reload = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Reload");
            reload.addEventListener("click", () => window.location.reload());
            dom.panelStatus.hidden = false;
            dom.panelStatus.replaceChildren(el("span", { text: "Couldn't load the country list. " }), reload);
        }
        return;
    }
    for (const c of app.countries) app.byCode.set(c.code, c);
    app.indexes.country = buildIndex(app.countries, "country");
    app.indexes.capital = buildIndex(app.countries, "capital");

    await dailyPromise;
    // Today's unfinished puzzle always comes first; otherwise pick up where you left off.
    const daily = app.games.daily;
    const remembered = getChoice("mode", MODE_NAMES, "daily");
    const mode = daily && daily.status !== "PLAYING" ? remembered : "daily";
    await switchMode(mode);
    // Stats feed the end card, the give-up warning ("ending your 5-day streak")
    // and the share text, so fetch them up front rather than on first need.
    if (!app.board && !app.boardLoading) loadBoard();

    if (getPref("seenHelp", "0") !== "1") {
        setPref("seenHelp", "1");
        openDialog(dom.help);
    }
}

start();

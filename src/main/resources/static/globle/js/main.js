// Globle: the page controller.
//
// Two views share the page. Home (#/) shows today's two dailies and the practice
// presets; a game view (#/classic, #/hard, #/play/<preset>) shows one game. The
// routes are hash routes with real history entries, so a phone's back button goes
// home and a reload comes back to the same game. Routes and presets are defined in
// modes.js.
//
// The server is the source of truth: every guess, give-up and new game returns a
// full GameState, and the page re-renders from that rather than patching itself,
// so a reload, a second device or a midnight rollover can never leave the screen
// disagreeing with the server. Each route keeps its own game while you move
// between them.
//
// Hard (daily and practice) is the same game with a blind globe and a plain text
// box: no suggestions, the name has to be typed exactly.

import { api } from "./api.js";
import { Globe } from "./globe.js";
import { Autocomplete } from "./autocomplete.js";
import { buildIndex, findExact } from "./match.js";
import { guessColor, legendGradient } from "./scale.js";
import { shareText } from "./share.js";
import { closestGuess, latestGuess, newGuess, highlightFor, ownRow } from "./stats.js";
import { flagEmoji, formatDistance, formatCountdown, regionLabel, plural } from "./format.js";
import { getPref, setPref, getChoice } from "./prefs.js";
import { celebrate } from "./celebrate.js";
import {
    HOME, VARIANTS, PRESETS, FEATURED, MORE_REGIONS, DAILY_LABELS,
    parseRoute, routeHash, sameRoute, dailyRoute, playRoute, routeInfo, isHard, accentFor,
    presetFor, modeLabel, modeIcon, statusText, statusKind, resumeText,
} from "./modes.js";
import {
    $, $$, el, icon, setupDialog, openDialog, confirmSheet, toast, announce,
    renderGuesses, renderClosest, renderTiles, dailyTiles, renderDistribution,
    renderToday, renderWeek, renderAllTime, renderUnlimited, unlimitedSummary,
} from "./ui.js";

const BOARD_TABS = ["today", "week", "all", "unlimited"];
const BOARD_KINDS = ["country", "capital", "hard"];
const PRACTICE_NAMES = { country: "Unlimited", capital: "Capitals", hard: "Hard practice" };

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
const coarsePointer = window.matchMedia("(pointer: coarse)").matches;

const body = document.body;

function boardSlot(variant) {
    // data: the last Leaderboard; loading: the fetch in flight; wanted: fetch again after it
    return { variant, data: null, error: null, loading: null, wanted: false };
}

const app = {
    route: HOME,
    nav: 0,                 // bumped on every route change, so a late load knows it is stale
    // route key -> undefined: not loaded yet; null: no game (practice 204); else a GameState
    games: {},
    touched: {},            // route key -> when its state last came from the server
    loadErrors: {},
    inflight: {},
    home: null,
    homeStamp: -1,          // when the home summary on hand was requested
    homeError: null,
    homeLoading: null,
    homeWanted: false,
    countries: [],
    countriesError: null,
    byCode: new Map(),
    indexes: { country: null, capital: null },
    boards: { classic: boardSlot("classic"), hard: boardSlot("hard") },
    boardTab: getChoice("boardTab", BOARD_TABS, "today"),
    boardKind: getChoice("boardKind", BOARD_KINDS, "country"),
    boardVariant: getChoice("boardVariant", VARIANTS, "classic"),
    statsVariant: getChoice("statsVariant", VARIANTS, "classic"),
    units: getChoice("units", ["km", "mi"], "km"),
    busy: new Set(),        // route keys with a guess / give-up / new-game request in flight
    signedOut: false,
    puzzle: Number(body.dataset.puzzle) || null,
    copiedTimer: 0,
    tipTimer: 0,
};

// ------------------------------------------------------------------ elements

const dom = {
    titleTag: $("#title-tag"),
    gameBar: $("#game-bar"),
    modeIcon: $("#mode-icon"),
    modeLabel: $("#mode-label"),
    modeBadge: $("#mode-badge"),
    home: $("#home"),
    homeTitle: $("#home-title"),
    homeNext: $("#home-next"),
    homeCountdown: $("#home-countdown"),
    cards: { classic: $("#card-classic"), hard: $("#card-hard") },
    presets: $("#presets"),
    more: $("#more-regions"),
    panel: $("#panel"),
    stage: $("#stage"),
    canvas: $("#globe"),
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
    inputIcon: $("#guess-icon"),
    inputHelp: $("#guess-help"),
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
    newAfter: $("#btn-new-after"),
    playHard: $("#btn-play-hard"),
    session: $("#session-banner"),
    welcome: $("#dlg-welcome"),
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

function onHome() {
    return app.route.name === "home";
}

/** Whether the game stored under this route key is the one on screen. */
function isOnScreen(key) {
    return !!key && app.route.key === key;
}

function currentState() {
    return app.route.key ? app.games[app.route.key] : undefined;
}

function currentKind() {
    const info = routeInfo(app.route);
    return info ? info.kind : "country";
}

/** Today's puzzle number: the same for both dailies. */
function puzzleNumber() {
    if (app.home && app.home.puzzle != null) return app.home.puzzle;
    for (const variant of VARIANTS) {
        const daily = app.games[variant];
        if (daily && daily.puzzle != null) return daily.puzzle;
    }
    return app.puzzle;
}

function countryName(code) {
    const c = app.byCode.get(code);
    return c ? c.name : code;
}

/** A missing variant is classic: responses from before Hard existed. */
function variantOf(state) {
    return state && state.variant === "hard" ? "hard" : "classic";
}

/** Which practice board a finished game counts on: country, capital or hard. */
function practiceKind(state) {
    if (variantOf(state) === "hard") return "hard";
    return state && state.kind === "capital" ? "capital" : "country";
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

function isStale(withNext) {
    return !!withNext && !!withNext.nextPuzzleAt && Date.parse(withNext.nextPuzzleAt) <= Date.now();
}

// ------------------------------------------------------------------ errors

function showError(text) {
    if (dom.form.hidden || onHome()) {
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

function isAuthError(error) {
    return !!error && (error.code === "signed-out" || error.code === "forbidden");
}

/** Problems any request can have. route is the one the request was for, which may no longer be on screen. */
function handleCommonError(error, route = app.route) {
    if (isAuthError(error)) {
        showSessionExpired(error.code);
        return true;
    }
    if (isOnScreen(route.key)) showError(TRANSPORT_MESSAGES[error && error.code] || "Something went wrong. Try again.");
    if (error && error.code === "timeout" && route.key) reloadSlot(route);
    return true;
}

function handleGameError(error, code, route = app.route) {
    const info = routeInfo(route);
    const kind = info ? info.kind : "country";
    const here = isOnScreen(route.key);
    switch (error && error.code) {
        case "unknown-country":
            if (here) showError(kind === "capital" ? "That isn't a capital we know. Pick one from the list." : "That isn't a country we know.");
            break;
        case "already-guessed":
            if (here) showError(`You already guessed ${countryName(code)}.`);
            break;
        case "no-capital":
            if (here) showError(`${countryName(code)} has no capital to guess. Try another.`);
            break;
        case "finished":
            // Not showError: the reload is about to hide the form it would land in.
            // The typed guess was for that game, so it goes too.
            if (here) {
                clearError();
                autocomplete.clear();
                toast("That game already ended (maybe on another device). Loading your current one.", 3600);
            }
            reloadSlot(route);
            break;
        case "not-found":
            if (here) toast("That game could not be found. Loading your current one.", 3600);
            reloadSlot(route);
            break;
        case "new-day":
            // The page was still showing yesterday's puzzle; the server refused
            // rather than count this against today's.
            if (here) {
                clearError();
                autocomplete.clear();
                toast("A new puzzle started at midnight. Nothing was counted.", 4000);
            }
            reloadSlot(route);
            loadHome();
            break;
        case "bad-request":
            if (here) showError("That didn't go through. Try again.");
            break;
        default:
            handleCommonError(error, route);
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
        // The same route, for a new tab or a modified click.
        link.setAttribute("href", location.pathname + location.hash);
        link.addEventListener("click", (e) => {
            link.setAttribute("href", location.pathname + location.hash);   // the route now, if it changed since
            if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
            e.preventDefault();
            // A same-page #hash link alone would neither reload nor fetch a fresh
            // token; reload() keeps the hash, so the game on screen comes back.
            window.location.reload();
        });
    }
    dom.session.hidden = false;
    autocomplete.setLocked(true);
    for (const b of [dom.go, dom.giveUp, dom.skip, dom.startBtn, dom.newAfter]) b.disabled = true;
    announce("Your session has expired. Sign in again to keep playing.");
}

function isBusy(key = app.route.key) {
    return !!key && app.busy.has(key);
}

function setBusy(key, on) {
    if (on) app.busy.add(key);
    else app.busy.delete(key);
    applyBusy();
}

/**
 * Locks the guess box and buttons only while the game on screen has a request in
 * flight: one still pending for another game must not lock this one.
 */
function applyBusy() {
    if (app.signedOut) return;
    const busy = isBusy();
    autocomplete.setLocked(busy);
    dom.go.disabled = busy;
    dom.go.classList.toggle("is-busy", busy);
    for (const b of [dom.giveUp, dom.skip, dom.startBtn, dom.newAfter]) b.disabled = busy;
    dom.panelStatus.setAttribute("aria-busy", busy ? "true" : "false");
}

// ------------------------------------------------------------------ rendering

function render({ fresh = null } = {}) {
    const route = app.route;
    const home = onHome();
    body.dataset.view = home ? "home" : "game";
    body.dataset.accent = accentFor(route);
    dom.home.hidden = !home;
    dom.panel.hidden = home;
    dom.gameBar.hidden = home;
    // On home the globe is scenery: not a tab stop, and not announced as a control.
    dom.canvas.tabIndex = home ? -1 : 0;
    if (home) dom.canvas.setAttribute("aria-hidden", "true");
    else dom.canvas.removeAttribute("aria-hidden");
    // Blind while a Hard game is being played; once it is over the land comes back.
    const shown = currentState();
    dom.stageBlind.hidden = !isHard(route) || (!!shown && shown.status !== "PLAYING");

    const number = puzzleNumber();
    dom.titleTag.textContent = home && number ? `#${number}` : "";

    if (home) {
        renderHome();
        syncGlobe();
        return;
    }

    const info = routeInfo(route);
    const key = route.key;
    const state = app.games[key];
    const failure = state === undefined ? (app.loadErrors[key] || null) : null;
    const loading = state === undefined && !failure;
    const none = state === null;
    const playing = !!state && state.status === "PLAYING";
    const finished = !!state && !playing;

    dom.modeIcon.className = `bi ${modeIcon(route)}`;
    dom.modeLabel.textContent = modeLabel(route, {
        puzzle: state && state.puzzle != null ? state.puzzle : number,
        region: state ? state.region : null,
    });
    dom.practice.hidden = info.daily;

    // The country list is what turns typing into a guess; without it, say so. Until
    // it arrives the guess box stays hidden: Hard would call a correct name
    // misspelled, and building the index would wipe what had been typed.
    const blocked = !!app.countriesError;
    const listLoading = !blocked && !app.indexes.country;
    dom.panelStatus.hidden = !(loading || failure || blocked || (playing && listLoading));
    if (blocked) {
        const reload = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Reload");
        reload.addEventListener("click", () => window.location.reload());
        dom.panelStatus.replaceChildren(el("span", { text: "Couldn't load the country list. " }), reload);
    } else if (loading || (playing && listLoading)) {
        dom.panelStatus.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }),
            loading ? " Loading your game…" : " Loading the country list…");
    } else if (failure) {
        const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        retry.addEventListener("click", () => reloadSlot(route));
        dom.panelStatus.replaceChildren(el("span", { text: "Couldn't load this game. " }), retry);
    }

    dom.start.hidden = !none;
    if (none) renderStart(route);
    dom.form.hidden = !playing || blocked || listLoading;
    dom.end.hidden = !finished;
    if (finished) renderEnd(state);
    dom.closest.hidden = !playing;
    dom.listWrap.hidden = !state;
    dom.actions.hidden = !playing;
    dom.skip.hidden = info.daily;

    if (state) {
        const answerCode = state.answer ? state.answer.code : null;
        const latest = latestGuess(state.guesses);
        renderClosest(dom.closest, { state, units: app.units, closest: closestGuess(state.guesses, answerCode) });
        renderGuesses(dom.list, { state, units: app.units, latestCode: latest ? latest.code : null, fresh: !!fresh });
        dom.count.textContent = String(state.guesses.length);
        dom.empty.hidden = state.guesses.length > 0;
        const capital = state.kind === "capital";
        dom.input.placeholder = isHard(route) ? "Type a country, then Enter" : capital ? "Guess a capital…" : "Guess a country…";
        $("#guess-label").textContent = capital ? "Guess a capital city" : "Guess a country";
        autocomplete.setGuessed(state.guesses.map((g) => g.code));
    }

    syncGlobe();
}

/** The guess box for a route: a combobox, or (Hard) a plain text box with no suggestions. */
function configureInput(route) {
    const info = routeInfo(route);
    const hard = isHard(route);
    autocomplete.setIndex(info ? app.indexes[info.kind] : null);
    autocomplete.setEnabled(!hard);
    dom.input.setAttribute("autocapitalize", hard ? "off" : "words");
    dom.form.classList.toggle("is-plain", hard);
    dom.inputIcon.className = `bi ${hard ? "bi-keyboard" : "bi-search"} gl-combo-icon`;
    dom.inputHelp.textContent = hard
        ? "No suggestions in Hard: type the country's name and press Enter."
        : "Type to see matching places, use the arrow keys to choose, and Enter to guess.";
}

function syncGlobe() {
    const route = app.route;
    globe.setBlind(isHard(route));
    if (onHome()) {
        globe.setGame({ kind: "country", region: "world", guesses: [], status: "PLAYING" });
        return;
    }
    const info = routeInfo(route);
    const state = app.games[route.key];
    if (!state) {
        globe.setGame({ kind: info.kind, region: info.region, guesses: [], status: "PLAYING" });
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
    if (onHome()) {
        globe.focusRegion("world");
        return;
    }
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
    const region = state ? state.region : routeInfo(app.route).region;
    globe.focusRegion(region || "world");
}

function renderStart(route) {
    const preset = PRESETS[route.preset];
    dom.startTitle.textContent = preset.label;
    dom.startCopy.textContent = preset.sub;
}

function renderEnd(state) {
    const won = state.status === "WON";
    const answer = state.answer || {};
    const capitalMode = state.kind === "capital";
    const variant = variantOf(state);
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
    dom.newAfter.hidden = daily;
    // After the classic daily, the obvious next thing is today's Hard, unless it is done.
    const hard = dailySummary("hard");
    const hardDone = !!hard && (hard.status === "WON" || hard.status === "GAVE_UP");
    dom.playHard.hidden = !(daily && variant === "classic" && !hardDone);

    if (daily) {
        const slot = app.boards[variant];
        const me = slot.data ? slot.data.me : null;
        renderTiles($("#end-tiles"), dailyTiles(me));
        $("#end-tiles").classList.toggle("is-loading", !slot.data || !!slot.loading);
        renderDistribution($("#end-dist"), me ? me.distribution : null, highlightFor(state), {
            empty: variant === "hard" ? "Win a Hard daily to start your chart." : undefined,
        });
        tickCountdown();
    } else {
        const kind = practiceKind(state);
        const source = practiceSource();
        $("#end-record").textContent = source.data
            ? `Your ${PRACTICE_NAMES[kind]} record: ${unlimitedSummary(ownRow(practiceRows(source.data, kind)))}`
            : "";
    }
}

function tickCountdown() {
    const state = currentState();
    if (app.route.name === "daily" && state && state.status !== "PLAYING") {
        const node = $("#countdown");
        const ready = $("#btn-next-daily");
        const ms = state.nextPuzzleAt ? Date.parse(state.nextPuzzleAt) - Date.now() : NaN;
        if (Number.isNaN(ms)) {
            $("#end-next").hidden = true;
            ready.hidden = true;
        } else {
            $("#end-next").hidden = ms <= 0;
            ready.hidden = ms > 0;
            node.textContent = formatCountdown(ms);
        }
    }
    if (onHome()) {
        const next = app.home && app.home.nextPuzzleAt
            ? app.home.nextPuzzleAt
            : (app.games.classic && app.games.classic.nextPuzzleAt) || null;
        const ms = next ? Date.parse(next) - Date.now() : NaN;
        dom.homeNext.hidden = Number.isNaN(ms) || ms <= 0;
        if (!dom.homeNext.hidden) dom.homeCountdown.textContent = formatCountdown(ms);
    }
}

// ------------------------------------------------------------------ home

/**
 * One daily's line on the home screen: { status, guessCount, currentStreak }. The
 * home summary from the server, unless this page has heard about that game since
 * the summary was requested (a guess just made), in which case the game wins.
 */
function dailySummary(variant) {
    const local = app.games[variant];
    const remote = app.home && app.home[variant] ? app.home[variant] : null;
    const me = app.boards[variant].data ? app.boards[variant].data.me : null;
    const streak = remote && Number.isFinite(remote.currentStreak) ? remote.currentStreak : me ? me.currentStreak : null;
    const sameDay = !!local && (!app.home || app.home.puzzle == null || local.puzzle === app.home.puzzle);
    if (local && sameDay && (!remote || (app.touched[variant] || 0) > app.homeStamp)) {
        return {
            status: local.status === "PLAYING" && local.guessCount === 0 ? "NEW" : local.status,
            guessCount: local.guessCount,
            currentStreak: streak,
        };
    }
    if (remote) return { ...remote, currentStreak: streak };
    return streak != null ? { status: "NEW", guessCount: 0, currentStreak: streak } : null;
}

/**
 * A practice preset's game in progress, as { guessCount }, or null. Same freshness
 * rule. A game with no guesses yet gets no chip: opening a preset starts one, and
 * merely looking at it leaves nothing to resume.
 */
function presetSummary(preset) {
    const key = playRoute(preset).key;
    const local = app.games[key];
    if (local !== undefined && (!app.home || (app.touched[key] || 0) > app.homeStamp)) {
        return local && local.status === "PLAYING" && local.guessCount > 0 ? { guessCount: local.guessCount } : null;
    }
    const list = app.home && Array.isArray(app.home.inProgress) ? app.home.inProgress : [];
    const games = list.filter((g) => presetFor(g) === preset && g.guessCount > 0);
    if (!games.length) return null;
    return { guessCount: Math.max(...games.map((g) => g.guessCount || 0)) };
}

function renderHome() {
    const number = puzzleNumber();
    // The summary could not be fetched (and not because the session ended): a card
    // with no game of today's on this page cannot know its status, so it says so
    // rather than offer a bright "Play" for a daily that may be solved already.
    const failed = !app.home && !!app.homeError && !isAuthError(app.homeError);
    for (const variant of VARIANTS) {
        const card = dom.cards[variant];
        const summary = dailySummary(variant);
        const unknown = failed && !app.games[variant];
        $("[data-number]", card).textContent = number ? `Daily #${number}` : "Daily";
        const status = $("[data-status]", card);
        if (unknown) {
            status.replaceChildren(el("span", { "aria-hidden": "true", text: "—" }), el("span", { class: "sr-only", text: "Status unknown" }));
            status.className = "gl-daily-status is-unknown";
        } else {
            status.textContent = statusText(summary);
            status.className = `gl-daily-status is-${statusKind(summary)}`;
        }
        // The streak is shown after the icon but read after the status, so the
        // card's name starts with the game: "Classic Daily #4 Play, 3-day streak".
        const streak = summary && Number.isFinite(summary.currentStreak) ? summary.currentStreak : null;
        const node = $("[data-streak]", card);
        node.classList.toggle("is-cold", !streak);
        node.replaceChildren(...(streak == null ? [] : [el("span", { "aria-hidden": "true", text: `\u{1F525} ${streak}` })]));
        $("[data-streak-sr]", card).textContent = streak ? `, ${streak}-day streak` : "";
        card.classList.toggle("is-loading", !app.home && !app.homeError && !summary);
    }
    $("#home-error").hidden = !failed;
    for (const link of $$("[data-preset]", dom.home)) {
        const summary = presetSummary(link.dataset.preset);
        const chip = $("[data-resume]", link);
        chip.hidden = !summary;
        if (summary) {
            const text = resumeText(summary.guessCount);
            if (chip.classList.contains("gl-more-dot")) chip.replaceChildren(el("span", { class: "sr-only", text: ` (${text})` }));
            else chip.textContent = text;
            chip.title = text;
        }
    }
    tickCountdown();
}

function buildHome() {
    dom.presets.replaceChildren(...FEATURED.map((name) => {
        const p = PRESETS[name];
        return el("li", {},
            el("a", { class: `gl-preset is-${p.accent}`, href: routeHash(playRoute(name)), "data-route": true, "data-preset": name },
                el("span", { class: "gl-preset-icon", "aria-hidden": "true" }, icon(p.icon)),
                el("span", { class: "gl-preset-text" },
                    el("span", { class: "gl-preset-name", text: p.label }),
                    el("span", { class: "gl-preset-sub", text: p.sub })),
                el("span", { class: "gl-preset-resume", "data-resume": true, hidden: true }),
                el("i", { class: "bi bi-chevron-right gl-preset-go", "aria-hidden": "true" })));
    }));
    for (const name of MORE_REGIONS) {
        dom.more.append(el("a", { class: "gl-more-chip", href: routeHash(playRoute(name)), "data-route": true, "data-preset": name },
            el("span", { text: regionLabel(PRESETS[name].region) }),
            el("span", { class: "gl-more-dot", "data-resume": true, hidden: true })));
    }
}

/**
 * Fetches the home summary. A call while one is in flight queues one more: the
 * earlier answer may predate the game that just ended.
 */
function loadHome() {
    app.homeWanted = true;
    if (app.homeLoading) return app.homeLoading;
    app.homeLoading = (async () => {
        while (app.homeWanted) {
            app.homeWanted = false;
            const sentAt = performance.now();
            try {
                const home = await api.home();
                app.home = home;
                app.homeStamp = sentAt;
                app.homeError = null;
                if (home && home.puzzle != null) app.puzzle = home.puzzle;
            } catch (error) {
                app.homeError = error;
                if (isAuthError(error)) showSessionExpired(error.code);
                break;
            }
        }
        app.homeLoading = null;
        if (onHome()) render();
        else {
            const state = currentState();
            if (state && state.status !== "PLAYING") renderEnd(state);
        }
    })();
    return app.homeLoading;
}

// ------------------------------------------------------------------ loading games

function fetchSlot(route) {
    const info = routeInfo(route);
    if (info.daily) return api.daily(info.variant);
    // No region: the server finds a game of this kind in any region, so a Capitals
    // game started in a region before presets existed still resumes.
    if (info.anyRegion) return api.current(info.kind);
    return api.current(info.kind, info.region, info.variant);
}

function setGame(key, state) {
    app.games[key] = state;
    app.touched[key] = performance.now();
    app.loadErrors[key] = null;
    if (state && state.mode === "daily" && state.puzzle != null) app.puzzle = state.puzzle;
}

/** Loads a route's game once; a second call while it is loading waits for the same request. */
function loadSlot(route) {
    const key = route.key;
    if (app.inflight[key]) return app.inflight[key];
    app.loadErrors[key] = null;
    const promise = (async () => {
        try {
            setGame(key, await fetchSlot(route));
        } catch (error) {
            app.loadErrors[key] = error;
            if (isAuthError(error)) showSessionExpired(error.code);
        } finally {
            delete app.inflight[key];
        }
    })();
    app.inflight[key] = promise;
    return promise;
}

async function reloadSlot(route) {
    const key = route.key;
    if (!key) return;
    app.games[key] = undefined;
    if (isOnScreen(key)) render();
    await loadSlot(route);
    if (isOnScreen(key)) {
        render();
        aimGlobe();
    }
}

/**
 * Shows a remembered game at once, then checks it against the server quietly:
 * another device may have played since. Only a real difference redraws, and a
 * finished practice game stays on screen rather than giving way to "start".
 */
async function refreshSlot(route) {
    const key = route.key;
    const before = app.games[key];
    if (before === undefined || isBusy(key) || app.inflight[key]) return;
    let fresh;
    try {
        fresh = await fetchSlot(route);
    } catch {
        return;
    }
    if (isBusy(key) || app.games[key] !== before) return;
    const info = routeInfo(route);
    const same = (a, b) => !!a && !!b && a.id === b.id && a.date === b.date
        && a.status === b.status && a.guessCount === b.guessCount;
    if (same(before, fresh)) return;
    if (!info.daily && !fresh && !(before && before.status === "PLAYING")) return;
    setGame(key, fresh);
    if (!isOnScreen(key)) return;
    if (info.daily && before && fresh && before.puzzle !== fresh.puzzle) {
        toast(`A new day has started. This is ${DAILY_LABELS[info.variant]} #${fresh.puzzle}.`, 4000);
    } else if (!info.daily && before && before.status === "PLAYING" && !fresh) {
        toast("That game ended on another device.", 3600);
    }
    clearError();
    render();
    aimGlobe();
}

/** Swaps a daily left open past midnight for today's, and says so. */
async function refreshDaily(variant) {
    const route = dailyRoute(variant);
    const before = app.games[variant];
    if (!isOnScreen(variant)) {
        app.games[variant] = undefined;
        loadHome();
        return;
    }
    await reloadSlot(route);
    const after = app.games[variant];
    if (isOnScreen(variant) && before && after && before.puzzle != null && after.puzzle != null
            && before.puzzle !== after.puzzle) {
        toast(`A new day has started. This is ${DAILY_LABELS[variant]} #${after.puzzle}.`, 4000);
    }
    loadHome();
}

/**
 * Midnight passed while the page was open. Dailies not on screen are yesterday's
 * now, so they load afresh when next opened. One on screen is left alone: if it
 * is unfinished the once-a-second check reloads it, and a finished one offers
 * "A new puzzle is ready" itself.
 */
function newDay() {
    for (const variant of VARIANTS) {
        if (!isOnScreen(variant) && isStale(app.games[variant])) app.games[variant] = undefined;
    }
    loadHome();
}

// ------------------------------------------------------------------ routing

/**
 * Moves to a game route with a new history entry (or replacing this one). The
 * entry remembers whether it was pushed from home, so Home can be "back" rather
 * than piling up entries.
 */
function go(route, { replace = false } = {}) {
    if (!route || route.name === "home") {
        goHome();
        return;
    }
    if (sameRoute(route, app.route)) return;
    if (replace) history.replaceState(history.state, "", routeHash(route));
    else history.pushState({ globle: onHome() ? "from-home" : "game" }, "", routeHash(route));
    enterRoute(route);
}

function goHome() {
    if (onHome()) return;
    const state = history.state;
    if (state && state.globle === "from-home") {
        history.back();
        // In case the entry before is not ours after all, land home anyway.
        const nav = app.nav;
        setTimeout(() => {
            if (app.nav === nav && !onHome()) replaceHome();
        }, 600);
        return;
    }
    replaceHome();
}

function replaceHome() {
    history.replaceState(null, "", location.pathname + location.search);
    enterRoute(HOME);
}

/** The back and forward buttons, and a hash typed into the address bar. */
function syncToLocation() {
    const route = parseRoute(location.hash);
    if (!route) history.replaceState(null, "", location.pathname + location.search);
    const target = route || HOME;
    if (!sameRoute(target, app.route)) enterRoute(target);
}

function closeDialogs() {
    for (const dialog of $$("dialog[open]")) dialog.close("dismiss");
}

async function enterRoute(route, { initial = false } = {}) {
    const nav = ++app.nav;
    app.route = route;
    if (!initial) {
        closeDialogs();
        window.scrollTo(0, 0);
    }
    clearError();
    hideTip();
    // The lock follows the game: free for this one even if another game's request
    // is still pending, and still on when coming back to the game that sent one.
    applyBusy();
    $("#share-fallback").hidden = true;

    if (route.name === "home") {
        render();
        globe.restartSpin();
        aimGlobe();
        loadHome();
        if (!initial) dom.homeTitle.focus({ preventScroll: true });
        return;
    }

    const info = routeInfo(route);
    const key = route.key;
    configureInput(route);
    render();
    aimGlobe();
    if (!initial) dom.modeBadge.focus({ preventScroll: true });
    // The end card, the give-up warning and the share text read the daily's stats.
    if (info.daily && !app.boards[info.variant].data) loadBoard(info.variant);

    if (app.games[key] === undefined) {
        await loadSlot(route);
        if (app.nav !== nav) return;
        // A preset picked on home with nothing to resume starts at once; the
        // start card stays only as the fallback when that fails.
        if (app.games[key] === null && !info.daily && !app.countriesError) {
            await newGame();
            if (app.nav !== nav) return;
        }
        render();
        aimGlobe();
    } else {
        refreshSlot(route);
    }
    const state = app.games[key];
    if (state && state.status !== "PLAYING" && !info.daily && !practiceSource().data) loadBoard("classic");
    if (!initial && state && state.status === "PLAYING" && !coarsePointer && !dom.form.hidden) {
        dom.input.focus({ preventScroll: true });
    }
}

// ------------------------------------------------------------------ playing

async function guess(code) {
    const route = app.route;
    const key = route.key;
    const state = key ? app.games[key] : null;
    if (isBusy(key) || app.signedOut || !state || state.status !== "PLAYING") return;
    const info = routeInfo(route);
    clearError();
    setBusy(key, true);
    try {
        const next = info.daily
            ? await api.guessDaily(code, state.date, info.variant)
            : await api.guessUnlimited(state.id, code);
        if (isOnScreen(key)) autocomplete.clear();
        applyState(route, next, state);
        if (coarsePointer) dom.input.blur();
    } catch (error) {
        handleGameError(error, code, route);
    } finally {
        setBusy(key, false);
        if (!coarsePointer && !dom.form.hidden && isOnScreen(key)) dom.input.focus({ preventScroll: true });
    }
}

/** Hard: what was typed must name one place exactly. No list to pick from, no "did you mean". */
function submitPlain() {
    if (isBusy() || app.signedOut) return;
    const text = dom.input.value.trim();
    if (!text) return;
    // render() keeps the box hidden until the list is here; this is the backstop,
    // so a correct name is never called misspelled.
    if (!app.indexes.country) {
        showError("Still loading the country list — try again in a moment.");
        return;
    }
    const code = findExact(app.indexes.country, text);
    if (!code) {
        showError("Not recognised — check the spelling");
        return;
    }
    const state = currentState();
    if (state && state.guesses.some((g) => g.code === code)) {
        showError(`You already guessed ${countryName(code)}.`);
        return;
    }
    guess(code);
}

/** Takes a fresh GameState from the server and shows what changed. */
function applyState(route, next, prev) {
    const key = route.key;
    setGame(key, next);
    const added = newGuess(prev, next);
    const justFinished = (!prev || prev.status === "PLAYING") && next.status !== "PLAYING";
    if (justFinished) {
        // Stats, streaks and the home cards just changed, wherever the player is now.
        loadBoard(next.mode === "daily" ? variantOf(next) : "classic");
        loadHome();
    }
    if (!isOnScreen(key)) return;

    if (next.mode === "daily" && prev && prev.puzzle != null && next.puzzle != null && prev.puzzle !== next.puzzle) {
        toast(`A new day has started. This is ${DAILY_LABELS[variantOf(next)]} #${next.puzzle}.`, 4000);
    }
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
    if (!coarsePointer) {
        const target = $("#btn-share");
        if (target) target.focus({ preventScroll: true });
    }
    // On a phone the globe is above the card. Keep the reveal in view and just
    // make sure the top of the card peeks up from the bottom of the screen. Given
    // up from the bottom of a long list, the globe is above the screen: bring it
    // back, and the card follows it. (Same breakpoint as the CSS; on a desktop the
    // globe is sticky and always in view.)
    const behavior = reducedMotion ? "auto" : "smooth";
    const top = dom.end.getBoundingClientRect().top;
    const peek = window.innerHeight - 140;
    if (window.matchMedia("(max-width: 959.98px)").matches && dom.stage.getBoundingClientRect().top < 0) {
        dom.stage.scrollIntoView({ block: "start", behavior });
    } else if (top > peek) {
        window.scrollBy({ top: top - peek, behavior });
    }
}

function currentStreak(variant) {
    const slot = app.boards[variant];
    if (slot.data && slot.data.me) return slot.data.me.currentStreak || 0;
    const summary = dailySummary(variant);
    return summary && summary.currentStreak ? summary.currentStreak : 0;
}

async function giveUp() {
    const route = app.route;
    const key = route.key;
    const state = key ? app.games[key] : null;
    if (isBusy(key) || app.signedOut || !state || state.status !== "PLAYING") return;
    const info = routeInfo(route);
    const streak = info.daily ? currentStreak(info.variant) : 0;
    const ok = await confirmSheet(dom.confirm, {
        title: "Give up?",
        body: info.daily
            ? `The answer will be revealed and today's ${info.variant === "hard" ? "Hard " : ""}puzzle counts as a loss${streak ? `, ending your ${streak}-day streak` : ""}.`
            : "The answer will be revealed and this game counts as a loss.",
        confirm: "Reveal the answer",
        cancel: "Keep playing",
        danger: true,
    });
    if (!ok || !isOnScreen(key)) return;
    setBusy(key, true);
    try {
        // state.date is the puzzle the sheet was about. If midnight passed while it
        // was open, the server refuses ("new-day") instead of forfeiting today's.
        const next = info.daily ? await api.giveUpDaily(state.date, info.variant) : await api.giveUpUnlimited(state.id);
        applyState(route, next, state);
    } catch (error) {
        handleGameError(error, null, route);
    } finally {
        setBusy(key, false);
    }
}

/**
 * Starts a new practice game in this route's slot. If one is in progress with
 * guesses, the server records it as given up, so the player is told that first,
 * in the page. One with no guesses yet is simply dropped, so that needs no warning.
 * Resolves false if they backed out, or if it turned out a game with guesses was
 * already in progress (played on another device since this page loaded), which is
 * then shown.
 */
async function newGame() {
    const route = app.route;
    const info = routeInfo(route);
    const key = route.key;
    if (!info || info.daily || isBusy(key) || app.signedOut) return false;
    const state = app.games[key];
    const abandoning = !!state && state.status === "PLAYING" && state.guessCount > 0;
    let current = state;
    if (!abandoning) {
        // This page thinks there is nothing to lose, but another device may have
        // guessed since. Starting over would silently give that game up.
        setBusy(key, true);
        try {
            current = await fetchSlot(route);
        } catch (error) {
            handleCommonError(error, route);
            return false;
        } finally {
            setBusy(key, false);
        }
        if (current && current.status === "PLAYING" && current.guessCount > 0) {
            setGame(key, current);
            if (isOnScreen(key)) {
                clearError();
                render();
                aimGlobe();
                toast("You have a game in progress from another device.", 3600);
            }
            return false;
        }
        if (!isOnScreen(key)) return false;
    }
    if (abandoning) {
        const ok = await confirmSheet(dom.confirm, {
            title: "Start a new game?",
            body: `This one counts as given up after ${plural(state.guessCount, "guess", "guesses")}. The answer won't be shown.`,
            confirm: "Start new game",
            cancel: "Keep playing",
        });
        if (!ok || !isOnScreen(key)) return false;
    }
    // A new game replaces the one in the same slot. A Capitals game resumed from a
    // region keeps that region for its replacement, so the server gives up the
    // right one; once it is over, new games are world-wide again.
    const region = info.anyRegion && current && current.status === "PLAYING" && current.region ? current.region : info.region;
    setBusy(key, true);
    try {
        const next = await api.newGame(info.kind, region, info.variant);
        setGame(key, next);
        if (isOnScreen(key)) {
            clearError();
            render();
            globe.focusRegion(next.region || region);
            announce(`New ${modeLabel(route, { region: next.region })} game. Make your first guess.`);
            if (!coarsePointer) dom.input.focus({ preventScroll: true });
        }
        if (abandoning) {
            toast("Your last game counted as given up.");
            loadBoard("classic");
        }
        return true;
    } catch (error) {
        handleCommonError(error, route);
        return false;
    } finally {
        setBusy(key, false);
    }
}

// ------------------------------------------------------------------ share

async function shareResult() {
    const state = currentState();
    if (!state || state.status === "PLAYING") return;
    const daily = state.mode === "daily";
    const variant = variantOf(state);
    // A win refreshes the stats; give that a moment so the streak shared is the
    // new one. Short, because the share sheet needs the tap to still be "recent".
    if (daily && app.boards[variant].loading) {
        await Promise.race([app.boards[variant].loading, new Promise((resolve) => setTimeout(resolve, 800))]);
    }
    const streak = daily ? currentStreak(variant) : 0;
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
 * Fetches one daily variant's leaderboard (which also carries the viewer's own
 * stats and every practice board). A call made while a fetch is already in flight
 * queues one more afterwards: that earlier response may predate the game that
 * just ended.
 */
function loadBoard(variant = "classic") {
    const slot = app.boards[variant];
    slot.wanted = true;
    if (slot.loading) return slot.loading;
    slot.loading = (async () => {
        while (slot.wanted) {
            slot.wanted = false;
            try {
                slot.data = await api.leaderboard(variant);
                slot.error = null;
            } catch (error) {
                slot.error = error;
                if (isAuthError(error)) showSessionExpired(error.code);
                break;
            }
        }
        slot.loading = null;
        boardsChanged();
    })();
    boardsChanged();
    return slot.loading;
}

function boardsChanged() {
    renderBoard();
    renderStats();
    const state = currentState();
    if (state && state.status !== "PLAYING") renderEnd(state);
    if (onHome()) renderHome();
}

/** Either variant's response carries the practice boards; use whichever is on hand. */
function practiceSource() {
    const { classic, hard } = app.boards;
    if (classic.data) return classic;
    if (hard.data) return hard;
    return classic.loading || !hard.loading ? classic : hard;
}

function practiceRows(data, kind) {
    return data && data.unlimited ? data.unlimited[kind] || [] : [];
}

function boardPlaceholder(node, slot) {
    if (slot.error) {
        const button = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        button.addEventListener("click", () => loadBoard(slot.variant));
        node.replaceChildren(el("p", { class: "gl-muted gl-board-empty" }, "Couldn't load the leaderboard. ", button));
    } else {
        node.replaceChildren(el("p", { class: "gl-muted gl-board-empty" },
            el("span", { class: "gl-spinner", "aria-hidden": "true" }), " Loading…"));
    }
}

function pressed(group, attr, value) {
    for (const b of $$(`[data-${attr}]`, group)) {
        b.setAttribute("aria-pressed", b.dataset[attr] === value ? "true" : "false");
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
    const practice = app.boardTab === "unlimited";
    $("#board-variant").hidden = practice;
    $("#board-sub").hidden = !practice;
    pressed($("#board-variant"), "variant", app.boardVariant);
    pressed($("#board-sub"), "kind", app.boardKind);

    if (practice) {
        const slot = practiceSource();
        if (!slot.data) {
            boardPlaceholder(panel, slot);
            return;
        }
        renderUnlimited(panel, practiceRows(slot.data, app.boardKind), app.boardKind);
        return;
    }
    const slot = app.boards[app.boardVariant];
    if (!slot.data) {
        boardPlaceholder(panel, slot);
        return;
    }
    const hard = app.boardVariant === "hard";
    const what = hard ? "Hard dailies" : "Daily puzzles";
    switch (app.boardTab) {
        case "week": renderWeek(panel, slot.data.week, { what }); break;
        case "all": renderAllTime(panel, slot.data.allTime, { what }); break;
        default: renderToday(panel, slot.data.today, { name: hard ? "Hard" : "Puzzle" });
    }
}

function renderStats() {
    const variant = app.statsVariant;
    const slot = app.boards[variant];
    pressed($("#stats-variant"), "variant", variant);
    const tiles = $("#stats-tiles");
    const practice = practiceSource().data;
    for (const [id, kind] of [["#stats-unlimited", "country"], ["#stats-capitals", "capital"], ["#stats-hard", "hard"]]) {
        $(id).textContent = practice ? unlimitedSummary(ownRow(practiceRows(practice, kind))) : "";
    }
    if (!slot.data) {
        boardPlaceholder($("#stats-dist"), slot);
        renderTiles(tiles, dailyTiles(null));
        tiles.classList.add("is-loading");
        return;
    }
    tiles.classList.remove("is-loading");
    const me = slot.data.me;
    renderTiles(tiles, dailyTiles(me));
    const today = app.games[variant];
    renderDistribution($("#stats-dist"), me ? me.distribution : null, highlightFor(today), {
        empty: variant === "hard" ? "Win a Hard daily to start your chart." : undefined,
    });
}

function selectBoardTab(tab, focus) {
    if (!BOARD_TABS.includes(tab)) return;
    app.boardTab = tab;
    setPref("boardTab", tab);
    renderBoard();
    if (focus) $(`[role=tab][data-tab="${tab}"]`, dom.board).focus();
}

/** The daily variant on screen, if a daily is. */
function dailyOnScreen() {
    return app.route.name === "daily" ? app.route.variant : null;
}

function openBoard() {
    const variant = dailyOnScreen();
    if (variant) app.boardVariant = variant;
    openDialog(dom.board, `[role=tab][data-tab="${app.boardTab}"]`);
    renderBoard();
    loadBoard(app.boardVariant);
}

function openStats() {
    const variant = dailyOnScreen();
    if (variant) app.statsVariant = variant;
    openDialog(dom.stats);
    renderStats();
    loadBoard(app.statsVariant);
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
    buildHome();

    // In-page links (home cards, presets, Home, Back home) move between views
    // without reloading. A modified click (new tab) is left to the browser.
    document.addEventListener("click", (e) => {
        const link = e.target instanceof Element ? e.target.closest("a[data-route]") : null;
        if (!link || e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        e.preventDefault();
        go(parseRoute(link.getAttribute("href")));
    });
    // From the classic end card straight to the Hard daily: replaces the classic
    // entry, so Home (or back) still goes home in one step.
    dom.playHard.addEventListener("click", (e) => {
        if (e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        e.preventDefault();
        go(dailyRoute("hard"), { replace: true });
    });
    window.addEventListener("popstate", syncToLocation);
    window.addEventListener("hashchange", syncToLocation);

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
        if (isHard(app.route)) submitPlain();
        else autocomplete.submit();
    });
    // Hard's plain box: Esc clears it (the combobox handles its own keys).
    dom.input.addEventListener("keydown", (e) => {
        if (!isHard(app.route) || e.key !== "Escape" || !dom.input.value) return;
        e.preventDefault();
        dom.input.value = "";
        clearError();
    });
    // Tapping the arrow should not blur the input and drop the phone keyboard.
    dom.go.addEventListener("mousedown", (e) => e.preventDefault());

    dom.giveUp.addEventListener("click", giveUp);
    dom.skip.addEventListener("click", () => newGame());
    dom.startBtn.addEventListener("click", () => newGame());
    dom.newAfter.addEventListener("click", () => newGame());
    // Home's summary failed to load: clearing the error first brings back the
    // cards' loading look while the retry runs.
    $("#btn-home-retry").addEventListener("click", () => {
        app.homeError = null;
        render();
        loadHome();
    });
    $("#btn-share").addEventListener("click", shareResult);
    $("#btn-board-end").addEventListener("click", openBoard);
    $("#btn-next-daily").addEventListener("click", () => {
        if (app.route.name === "daily") {
            reloadSlot(app.route);
            loadHome();
        }
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
    $("#btn-stats").addEventListener("click", openStats);
    $("#btn-board").addEventListener("click", openBoard);

    for (const dialog of [dom.welcome, dom.help, dom.stats, dom.board]) setupDialog(dialog);
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
    for (const b of $$("[data-variant]", $("#board-variant"))) {
        b.addEventListener("click", () => {
            app.boardVariant = b.dataset.variant;
            setPref("boardVariant", app.boardVariant);
            renderBoard();
            loadBoard(app.boardVariant);
        });
    }
    for (const b of $$("[data-kind]", $("#board-sub"))) {
        b.addEventListener("click", () => {
            app.boardKind = b.dataset.kind;
            setPref("boardKind", app.boardKind);
            renderBoard();
        });
    }
    for (const b of $$("[data-variant]", $("#stats-variant"))) {
        b.addEventListener("click", () => {
            app.statsVariant = b.dataset.variant;
            setPref("statsVariant", app.statsVariant);
            renderStats();
            loadBoard(app.statsVariant);
        });
    }

    // The colour legend is the real scale, not a picture of it.
    for (const bar of $$(".gl-legend-bar")) bar.style.setProperty("--grad", legendGradient());

    // "/" jumps to the guess box, as on most search-driven pages.
    document.addEventListener("keydown", (e) => {
        if (e.key !== "/" || e.ctrlKey || e.metaKey || e.altKey) return;
        const target = e.target;
        if (target && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName))) return;
        if (document.querySelector("dialog[open]") || onHome() || dom.form.hidden) return;
        e.preventDefault();
        dom.input.focus();
    });

    // Coming back to the tab after midnight, or from the back/forward cache,
    // should not show yesterday's puzzles as today's.
    // A visible tab gets no visibilitychange at midnight, so unfinished dailies
    // also check every second. Once per nextPuzzleAt: a client clock running
    // ahead of the server's would otherwise reload the same game every second.
    // The server is the real guard: a guess or give-up still aimed at yesterday
    // is refused with "new-day" rather than counted against today.
    const refreshedFor = { classic: null, hard: null };
    let homeRefreshedFor = null;
    setInterval(() => {
        tickCountdown();
        if (app.signedOut) return;
        for (const variant of VARIANTS) {
            const daily = app.games[variant];
            if (!daily || daily.status !== "PLAYING" || isBusy(variant)) continue;
            if (daily.nextPuzzleAt === refreshedFor[variant] || !isStale(daily)) continue;
            refreshedFor[variant] = daily.nextPuzzleAt;
            refreshDaily(variant);
        }
        if (app.home && isStale(app.home) && app.home.nextPuzzleAt !== homeRefreshedFor) {
            homeRefreshedFor = app.home.nextPuzzleAt;
            newDay();
        }
    }, 1000);
    document.addEventListener("visibilitychange", () => {
        if (document.hidden || app.signedOut) return;
        for (const variant of VARIANTS) {
            if (isStale(app.games[variant])) refreshDaily(variant);
        }
        // Also retry a summary that failed to load: coming back is a natural moment.
        if (isStale(app.home) || (!app.home && app.homeError)) loadHome();
    });
    window.addEventListener("pageshow", (e) => {
        if (!e.persisted) return;
        app.games = {};
        app.touched = {};
        app.home = null;
        app.homeStamp = -1;
        const route = parseRoute(location.hash) || HOME;
        enterRoute(route, { initial: true });
    });
}

// ------------------------------------------------------------------ start

async function loadWorld() {
    dom.stageStatus.hidden = false;
    dom.stageStatus.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }), " Loading the world…");
    try {
        globe.setWorld(await api.world());
        dom.stageStatus.hidden = true;
        syncGlobe();
        aimGlobe();
    } catch (error) {
        if (isAuthError(error)) {
            showSessionExpired(error.code);
            return;
        }
        const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, "Try again");
        retry.addEventListener("click", () => loadWorld());
        dom.stageStatus.replaceChildren(el("span", { text: "Couldn't load the globe. " }), retry);
    }
}

function welcomeOnce() {
    if (getPref("seenHelp", "0") === "1") return;
    setPref("seenHelp", "1");
    openDialog(dom.welcome);
}

async function start() {
    wire();
    const initial = parseRoute(location.hash);
    if (!initial) history.replaceState(null, "", location.pathname + location.search);
    app.route = initial || HOME;

    const countriesPromise = api.countries();
    loadWorld();
    // Home or a game, straight away: neither waits for the country list.
    enterRoute(app.route, { initial: true });
    // A game view still wants the home summary, for "Play today's Hard".
    if (!onHome()) loadHome();

    try {
        app.countries = await countriesPromise;
    } catch (error) {
        app.countriesError = error;
        if (isAuthError(error)) showSessionExpired(error.code);
        render();
        welcomeOnce();
        return;
    }
    for (const c of app.countries) app.byCode.set(c.code, c);
    app.indexes.country = buildIndex(app.countries, "country");
    app.indexes.capital = buildIndex(app.countries, "capital");
    if (!onHome()) {
        configureInput(app.route);
        render();
        // The guess box appears only now. If the player picked this game after the
        // page loaded, enterRoute could not focus it then, so do it here.
        const state = currentState();
        if (app.nav > 1 && state && state.status === "PLAYING" && !coarsePointer && !dom.form.hidden
                && !document.querySelector("dialog[open]")) {
            dom.input.focus({ preventScroll: true });
        }
    }
    welcomeOnce();
}

start();

// Name them all on the page (#/all and #/all/<region>): the setup card, the run
// being typed, and the end card. main.js routes here and hands over what it shares
// (the globe, the country list and its search index, the session), and redraws this
// from render(), so a change of language reaches it like everything else.
//
// The rules are in rollcall.js and are pure; this file is the DOM and the server.
//
// The server is the source of truth for a run, as for every game: each response is
// the whole RollCallState, and the page draws from it. Typing must never wait for
// the network, though. A name is shown the moment it is accepted and queued; the
// queue sends one at a time, in order, and each answer replaces the server state on
// hand, with the names still queued drawn on top (rollcall.js withPending). A name
// the server refuses is taken back and the reason shown. The box keeps focus
// throughout, so a phone's keyboard stays up for the next country: Enter is taken
// on keydown (nothing is submitted, so nothing ends the edit), and the ↵ and "Did
// you mean" buttons never take focus from the box.
//
// Nothing here may give away a country not yet named: the box has no list while
// typing, and the globe draws (and its tooltip names) only what has been named
// until the run is over. The only countries ever offered are the ones the spell
// check finds close to what was typed, after Enter ("Did you mean Nauru?").

import { api } from "./api.js";
import { t, tn, has, compareText, getLang, translateText } from "./i18n.js";
import { placeName } from "./names.js";
import { normalize } from "./match.js";
import { flagEmoji, formatClock, formatInteger, regionLabel } from "./format.js";
import { rollCallShareText } from "./share.js";
import { getChoice, setPref } from "./prefs.js";
import { ANSWER_COLOR } from "./scale.js";
import { $, $$, el, confirmSheet, toast, announce, copyText } from "./ui.js";
import { modeLabel, rollCallRoute, isRollCall, studyRoute, routeHash } from "./modes.js";
import { rememberText } from "./study-logic.js";
import {
    ROLLCALL_REGIONS, AUTOFILLS, MINE_AFTER, MINE_LOOKBACK, HINT_MS, GUARD_MS,
    poolOf, decide as decideTyping, decideEnter, acceptable, waitFor, continuesName, namedCodes, withPending,
    typedCount, counts, groupByContinent, autofillChoices, pickAutofill,
    clockStart, clockSeconds, msToNextSecond, beats,
} from "./rollcall.js";

/** A country filled in at the start: a deeper green than one typed. */
const AUTO_COLOR = "#15803d";
/** The dot over a tiny country missed: the hatch's own green. */
const MISSED_COLOR = "#86efac";
/** Problems with the request itself; each has an error.<code> message. */
const TRANSPORT_ERRORS = ["offline", "timeout", "server", "bad-response"];
/** What typing can name that does not count, explained after a pause or on Enter. */
const EXPLAINED = ["already", "territory", "outside", "part", "former"];

/**
 * A sentence's own wording for one country where the plain {name} reads wrong, as
 * "rollcall.part.GBR" ("part of the United Kingdom", "parte del Reino Unido"); else
 * the key itself. The overrides sit beside their key in i18n.js.
 */
function forPlace(key, code) {
    return code && has(`${key}.${code}`) ? `${key}.${code}` : key;
}

function isAuthError(error) {
    return !!error && (error.code === "signed-out" || error.code === "forbidden");
}

export class RollCallView {
    /**
     * hooks, from main.js:
     *   globe, coarsePointer, confirmDialog
     *   route() nav()                     the route on screen; bumped on every change
     *   countries() byCode() index()      the country list, by code, and its search index
     *   countriesError()                  the list could not be loaded
     *   home() homeStamp()                the home summary and when it was asked for
     *   boardRow()                        the viewer's row on the Name them all board, if loaded
     *   signedOut() sessionExpired(code)
     *   go(route, options) openBoard()    options: { replace, keepPlace } (keepPlace: focus and scroll stay put)
     *   hideStageHint()                   once anything is on the globe
     *   homeChanged()                     redraw home's row if it is showing
     *   runEnded(region)                  a run was finished: home and boards are stale
     *   optionsChanged(region)            a region's options arrived (home shows the best)
     *   requestButton(code, source, ctx)  round 2: the "request a name change" button (requests.js)
     */
    constructor(hooks) {
        this.h = hooks;
        // region -> undefined (not asked yet) | null (none in progress) | the server's RollCallState.
        // A finished run stays here, for its end card, until Play again (the server only
        // ever returns a run in progress).
        this.runs = {};
        this.touched = {};       // region -> performance.now() of the last change, against the home summary
        this.loadErrors = {};
        this.inflight = {};      // region -> the GET /current in flight
        this.pending = {};       // region -> codes typed but not yet confirmed, oldest first; the first is in flight
        this.sending = {};       // region -> the queue is running
        this.waiters = {};       // region -> resolvers waiting for the queue to empty
        this.resync = {};        // region -> fetch the run again once the queue is empty
        this.version = {};       // region -> bumped by every change, so a slow quiet refresh knows it lost
        this.options = {};       // region -> { data, error, loading, stale }
        this.clocks = {};        // region -> { id, start }: the stopwatch, on performance.now()
        this.ended = {};         // region -> { newBest } for the end card
        this.wanted = getChoice("rollcallAutofill", AUTOFILLS, "off");
        this.feedback = null;    // the line under the box: { kind, code, error, n, total }
        this.drawnFeedback = null;
        this.setupError = null;  // an i18n key
        this.guard = null;       // { code, text, more, at }: the rest of a long name after its short form
        this.fresh = null;       // { code, drawn }: the name just accepted, highlighted in the list
        this.boxRegion = null;
        this.busy = { starting: false, finishing: false };
        this.locked = false;
        this.tick = 0;
        this.waitTimer = 0;
        this.hintTimer = 0;
        this.copiedTimer = 0;
        this.boxHadFocus = false; // the box had focus when ↵ or an offer was pressed (holdFocus)
        this.pools = { countries: null, byRegion: {} };
        // Round 2: region -> { runId, texts }: this run's recent "not recognised" texts,
        // newest first, in memory only (the request sheet's quick picks).
        this.unrecognised = {};

        const id = (name) => document.getElementById(name);
        this.dom = {
            status: id("rc-status"),
            setup: id("rc-setup"),
            regions: id("rc-regions"),
            fill: id("rc-autofill"),
            best: id("rc-best"),
            setupError: id("rc-setup-error"),
            start: id("rc-start"),
            play: id("rc-play"),
            count: id("rc-count"),
            total: id("rc-total"),
            countSr: id("rc-count-sr"),
            counter: id("rc-counter"),
            clock: id("rc-clock"),
            form: id("rc-form"),
            input: id("rc-input"),
            go: id("rc-go"),
            feedback: id("rc-feedback"),
            actions: id("rc-actions"),
            over: id("rc-over"),
            done: id("rc-done"),
            end: id("rc-end"),
            endKicker: id("rc-end-kicker"),
            endTitle: id("rc-end-title"),
            endMeta: id("rc-end-meta"),
            endNote: id("rc-end-note"),
            share: id("rc-share"),
            shareLabel: id("rc-share-label"),
            shareFallback: id("rc-share-fallback"),
            again: id("rc-again"),
            board: id("rc-board"),
            progress: id("rc-progress"),
            missedSection: id("rc-missed-section"),
            missedCount: id("rc-missed-count"),
            missed: id("rc-missed"),
            namedSection: id("rc-named-section"),
            namedCount: id("rc-named-count"),
            named: id("rc-named"),
            namedEmpty: id("rc-named-empty"),
            study: id("rc-study"),            // round 2: the end card's "Study what you missed"
            studyLink: id("rc-study-link"),   // round 2: the setup card's "Study your last game"
        };
        this.wire();
    }

    // ------------------------------------------------------------------ wiring

    wire() {
        const d = this.dom;
        for (const button of $$("[data-region]", d.regions)) {
            // Focus stays on the button pressed (the buttons are never redrawn).
            button.addEventListener("click", () => this.h.go(rollCallRoute(button.dataset.region), { replace: true, keepPlace: true }));
        }
        for (const radio of $$("input[name=rc-autofill]", d.fill)) {
            radio.addEventListener("change", () => {
                if (!radio.checked) return;
                this.wanted = radio.value;
                setPref("rollcallAutofill", radio.value);
            });
        }
        d.start.addEventListener("click", () => this.start());
        d.form.addEventListener("submit", (e) => {
            e.preventDefault();
            // The ↵ button (Enter on the keyboard is taken on keydown, below).
            const keyed = document.activeElement === d.go;
            this.submit();
            this.backToBox(keyed);
        });
        d.input.addEventListener("input", () => this.typed());
        d.input.addEventListener("keydown", (e) => {
            if (e.key === "Escape" && d.input.value) {
                e.preventDefault();
                this.clearBox();
            } else if (e.key === "Enter" && !e.isComposing && e.keyCode !== 229) {
                // Handled here rather than by submitting the form: nothing is submitted, so
                // nothing can end the edit and close a phone's keyboard. (Enter that ends an
                // IME composition is left alone; a plain Enter still reaches the form's
                // submit if this ever misses it.)
                e.preventDefault();
                // A held key repeats: only a fresh press may take a "Did you mean" offer.
                if (!e.repeat) this.submit();
            }
        });
        // Pressing ↵ must not take focus from the box and drop the keyboard.
        this.holdFocus(d.go);
        d.done.addEventListener("click", () => this.done());
        d.over.addEventListener("click", () => this.startOver());
        d.share.addEventListener("click", () => this.share());
        d.again.addEventListener("click", () => this.again());
        d.board.addEventListener("click", () => this.h.openBoard());
        // The stopwatch stops while nobody can see it, and resyncs with the server on return.
        document.addEventListener("visibilitychange", () => this.visibility());
    }

    // ------------------------------------------------------------------ state

    /** The region on screen, or null when another view is. */
    region() {
        const route = this.h.route();
        return isRollCall(route) ? route.region : null;
    }

    onScreen(region) {
        return !!region && this.region() === region;
    }

    /** The run as drawn: the server's, plus any names still on their way to it. */
    shown(region) {
        const run = this.runs[region];
        if (!run || run.status !== "PLAYING") return run;
        return withPending(run, this.pending[region] || [], this.h.byCode());
    }

    /** The countries this region's runs ask for, from the country list. */
    pool(region) {
        const countries = this.h.countries();
        if (this.pools.countries !== countries) this.pools = { countries, byRegion: {} };
        if (!this.pools.byRegion[region]) this.pools.byRegion[region] = poolOf(countries, region);
        return this.pools.byRegion[region];
    }

    isSending(region) {
        return !!this.sending[region] || (this.pending[region] || []).length > 0;
    }

    blocked() {
        return this.locked || this.h.signedOut() || this.busy.starting || this.busy.finishing;
    }

    /** Takes a run from the server (or null: none in progress). */
    setRun(region, state) {
        const before = this.runs[region];
        this.runs[region] = state;
        this.touched[region] = performance.now();
        this.version[region] = (this.version[region] || 0) + 1;
        this.loadErrors[region] = null;
        if (state && state.status === "PLAYING") {
            const clock = this.clocks[region];
            const same = !!clock && clock.id === state.id;
            this.clocks[region] = { id: state.id, start: clockStart(same ? clock.start : null, state.elapsedSeconds, performance.now()) };
        }
        if (before && (!state || state.id !== before.id)) {
            // Another run now: what was queued for the old one has no home.
            this.pending[region] = [];
            if (this.onScreen(region)) {
                this.guard = null;
                this.fresh = null;
            }
        }
    }

    /** A response to a name, a start or a finish: the run as the server now has it. */
    applyServer(region, next) {
        const before = this.runs[region];
        this.setRun(region, next);
        const ended = !!next && next.status === "FINISHED"
            && (!before || before.id !== next.id || before.status === "PLAYING");
        if (ended) this.finished(region, next);
        if (!this.onScreen(region)) return;
        this.render();
        this.syncGlobe();
        if (ended) this.revealEnd(region, next);
        else this.startClock();
    }

    /** Loads the run in progress in a region once; a second call waits for the same request. */
    load(region) {
        if (this.inflight[region]) return this.inflight[region];
        this.loadErrors[region] = null;
        const promise = (async () => {
            try {
                this.setRun(region, await api.rollCallCurrent(region));
            } catch (error) {
                this.loadErrors[region] = error;
                if (isAuthError(error)) this.h.sessionExpired(error.code);
            } finally {
                delete this.inflight[region];
            }
        })();
        this.inflight[region] = promise;
        return promise;
    }

    /**
     * Checks a run on hand against the server, quietly: another device may have
     * named countries, finished it or started over since. Only a real difference
     * redraws; the stopwatch takes the server's time either way.
     */
    async refresh(region) {
        const before = this.runs[region];
        if (before === undefined || this.inflight[region] || this.isSending(region) || this.busy.starting || this.busy.finishing) return;
        const version = this.version[region];
        let fresh;
        try {
            fresh = await api.rollCallCurrent(region);
        } catch {
            return;
        }
        if (this.version[region] !== version || this.isSending(region)) return;
        if (!fresh) {
            // A finished run on screen stays there until Play again.
            if (!before || before.status !== "PLAYING") return;
            this.setRun(region, null);
            if (this.onScreen(region)) {
                toast(t("rollcall.toast.ended"), 3600);
                this.loadOptions(region, { force: true });
                this.redraw({ aim: true });
            }
            return;
        }
        const same = !!before && before.id === fresh.id && before.status === fresh.status
            && before.namedCount === fresh.namedCount;
        this.setRun(region, fresh);
        if (!this.onScreen(region)) return;
        if (same) {
            this.startClock();
            return;
        }
        if (before && before.status === "PLAYING" && before.id !== fresh.id) toast(t("rollcall.toast.ended"), 3600);
        this.redraw({ aim: !before || before.id !== fresh.id });
        this.startClock();
    }

    /** Fetches the run again after something went wrong, keeping what is on screen until it arrives. */
    async reload(region) {
        const version = this.version[region];
        let fresh;
        try {
            fresh = await api.rollCallCurrent(region);
        } catch (error) {
            if (isAuthError(error)) this.h.sessionExpired(error.code);
            return;
        }
        // Something newer arrived meanwhile (a name, "I'm done"): it wins.
        if (this.version[region] !== version || this.isSending(region)) return;
        const before = this.runs[region];
        if (!fresh && before && before.status === "FINISHED") return;
        this.setRun(region, fresh);
        if (!fresh) this.loadOptions(region, { force: true });
        if (this.onScreen(region)) {
            this.redraw({ aim: !fresh });
            this.startClock();
        }
    }

    /**
     * The setup card's numbers for a region (and home's "Best"), fetched once and
     * again after a run there ends. Never rejects: resolves with the options, or null.
     */
    loadOptions(region, { force = false } = {}) {
        const slot = this.options[region] || (this.options[region] = { data: null, error: null, loading: null, stale: false });
        if (slot.loading) return slot.loading;
        if (slot.data && !slot.stale && !force) return Promise.resolve(slot.data);
        slot.loading = (async () => {
            try {
                slot.data = await api.rollCallOptions(region);
                slot.error = null;
                slot.stale = false;
            } catch (error) {
                slot.error = error;
                if (isAuthError(error)) this.h.sessionExpired(error.code);
            } finally {
                slot.loading = null;
            }
            if (this.onScreen(region)) this.render();
            this.h.optionsChanged(region);
            return slot.data;
        })();
        if (this.onScreen(region)) this.render();
        return slot.loading;
    }

    // ------------------------------------------------------------------ the route

    /** The route changed (to anything), before it is drawn: timers for the old one stop. */
    routeChanged(route) {
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.guard = null;
        this.stopClock();
        this.dom.shareFallback.hidden = true;
        if (isRollCall(route) && this.boxRegion !== route.region) {
            // One box serves every region; what was typed for another does not carry over.
            this.dom.input.value = "";
            this.feedback = null;
            this.setupError = null;
            this.boxRegion = route.region;
        }
    }

    /** Called after main.js has drawn the route: loads the run, or checks the one on hand. */
    async enter(route, { initial = false } = {}) {
        const region = route.region;
        const nav = this.h.nav();
        if (this.runs[region] === undefined) {
            await this.load(region);
            if (this.h.nav() !== nav) return;
            this.redraw({ aim: true });
        } else {
            this.refresh(region);
        }
        const run = this.runs[region];
        // The setup card needs the options; a run in progress wants them too, for the
        // end card's "new best" and for Start over's choice.
        if (run !== undefined && (run === null || run.status === "PLAYING")) this.loadOptions(region);
        this.startClock();
        if (!initial && run && run.status === "PLAYING") this.focusBox();
    }

    /** The country list arrived (or failed): the box can show, and names replace codes. */
    countriesReady({ focus = false } = {}) {
        if (!this.region()) return;
        this.redraw();
        if (focus) this.focusBox();
    }

    /** Start again from nothing (the page came back from the back/forward cache). */
    reset() {
        this.runs = {};
        this.touched = {};
        this.loadErrors = {};
        this.pending = {};
        this.options = {};
        this.clocks = {};
        this.ended = {};
        this.feedback = null;
        this.guard = null;
        this.fresh = null;
    }

    /** The session ended: nothing more can be saved. */
    lock() {
        this.locked = true;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.applyBusy();
    }

    visibility() {
        if (document.hidden) {
            this.stopClock();
            return;
        }
        const region = this.region();
        if (!region || this.h.signedOut()) return;
        this.refresh(region);
        this.startClock();
    }

    /**
     * The box, on a desktop: a phone would pop its keyboard up unasked. orCounter is
     * for when the control that had focus just went (Start): where the box cannot
     * take it (a touch screen, or the list still loading) the counter does, so a
     * screen reader is not left on a hidden button. Elsewhere focus stays put.
     */
    focusBox({ orCounter = false } = {}) {
        const d = this.dom;
        if (d.play.hidden || document.querySelector("dialog[open]")) return;
        if (this.h.coarsePointer || d.form.hidden) {
            if (orCounter) d.counter.focus({ preventScroll: true });   // it reads "0 of 197"
            return;
        }
        d.input.focus({ preventScroll: true });
    }

    /**
     * A button beside the box (↵, a "Did you mean" country) that must never take focus
     * from it: pressing it with a mouse, a finger or a pen does not move focus, so a
     * phone's keyboard stays up and typing goes straight on. (A keyboard user can
     * still Tab to it; backToBox() then takes focus back.)
     */
    holdFocus(button) {
        // Where focus is as the press begins, before anything can move it.
        button.addEventListener("pointerdown", () => {
            this.boxHadFocus = document.activeElement === this.dom.input;
        });
        // mousedown (not pointerdown) is the event whose default moves focus, and
        // touch browsers fire it too, just before the click (as autocomplete.js does).
        button.addEventListener("mousedown", (e) => {
            if (e.button > 0) return;
            e.preventDefault();
        });
    }

    /**
     * After ↵ or a "Did you mean" country: focus is back in the box for the next name,
     * if it ever left. keyed: the button was pressed from the keyboard (it had focus).
     * On a touch screen the box is focused again only if it had focus when the press
     * began (the keyboard was up), so the keyboard never pops up unasked.
     */
    backToBox(keyed) {
        const d = this.dom;
        const hadFocus = !!this.boxHadFocus;
        this.boxHadFocus = false;
        if (document.activeElement === d.input || d.play.hidden || d.form.hidden || d.input.readOnly) return;
        if (keyed || hadFocus || !this.h.coarsePointer) d.input.focus({ preventScroll: true });
    }

    /** "/" from anywhere: the box, if it is showing. Returns whether it took focus. */
    focusInput() {
        const d = this.dom;
        if (d.play.hidden || d.form.hidden) return false;
        d.input.focus();
        return true;
    }

    // ------------------------------------------------------------------ drawing

    redraw({ aim = false } = {}) {
        if (!this.region()) return;
        this.render();
        this.syncGlobe();
        if (aim) this.aimGlobe();
    }

    render() {
        const region = this.region();
        if (!region) return;
        const d = this.dom;
        const run = this.shown(region);
        const failure = run === undefined ? this.loadErrors[region] || null : null;
        const loading = run === undefined && !failure;
        const listFailed = !!this.h.countriesError();
        const listLoading = !listFailed && !this.h.index();
        const playing = !!run && run.status === "PLAYING";
        const finished = !!run && run.status === "FINISHED";

        // Typing means nothing without the country list, so without it the box stays hidden.
        d.status.hidden = !(loading || failure || (playing && (listFailed || listLoading)));
        if (playing && listFailed) {
            const reload = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, t("common.reload"));
            reload.addEventListener("click", () => window.location.reload());
            d.status.replaceChildren(el("span", { text: `${t("panel.listFailed")} ` }), reload);
        } else if (loading || (playing && listLoading)) {
            d.status.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }),
                ` ${t(loading ? "panel.loadingGame" : "panel.loadingList")}`);
        } else if (failure) {
            const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, t("common.tryAgain"));
            retry.addEventListener("click", async () => {
                const loading = this.load(region);
                this.render();   // back to "Loading your game…" while it runs
                await loading;
                if (!this.onScreen(region)) return;
                this.redraw({ aim: true });
                this.startClock();
                if (this.runs[region] !== undefined) this.loadOptions(region);
            });
            d.status.replaceChildren(el("span", { text: `${t("panel.gameFailed")} ` }), retry);
        }

        this.renderRegions(region);
        d.setup.hidden = run !== null;
        if (run === null) this.renderSetup(region);
        d.play.hidden = !playing;
        d.form.hidden = !playing || listFailed || listLoading;
        if (playing) this.renderPlay(region, run);
        d.actions.hidden = !playing;
        d.end.hidden = !finished;
        if (finished) this.renderEnd(region, run);
        this.renderProgress(region, run);
        // Names need the country list; until it is here the lists would show codes.
        this.renderLists(region, listLoading ? null : run);
        this.applyBusy();
    }

    /** The region picker: this one pressed, a dot on each other region with a run in progress. */
    renderRegions(region) {
        const elsewhere = this.regionsInProgress();
        for (const button of $$("[data-region]", this.dom.regions)) {
            const here = button.dataset.region === region;
            button.setAttribute("aria-pressed", here ? "true" : "false");
            const dot = $("[data-dot]", button);
            if (dot) dot.hidden = here || !elsewhere.has(button.dataset.region);
        }
    }

    renderSetup(region) {
        const d = this.dom;
        const slot = this.options[region] || null;
        const options = slot ? slot.data : null;
        const failed = !!slot && !!slot.error && !options;
        const choices = autofillChoices(options, { failed });
        const selected = pickAutofill(choices, this.wanted);
        for (const choice of choices) {
            const label = $(`[data-autofill="${choice.value}"]`, d.fill);
            if (!label) continue;
            const radio = $("input", label);
            radio.disabled = !choice.available;
            radio.checked = choice.value === selected;
            label.classList.toggle("is-disabled", !choice.available);
            $("[data-count]", label).textContent = choice.count != null ? formatInteger(choice.count) : "";
            $("[data-note]", label).textContent = this.choiceNote(choice, options);
        }
        if (failed) {
            const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, t("common.tryAgain"));
            retry.addEventListener("click", () => this.loadOptions(region, { force: true }));
            d.best.replaceChildren(el("span", { text: `${t("rollcall.setup.failed")} ` }), retry);
        } else {
            d.best.textContent = options ? this.bestLine(options.best) : "";
        }
        d.best.hidden = !options && !failed;
        // Round 2: "Study your last game", once any game here has finished, counting or
        // not (the server's studyable; or one this page saw end, before the options reload).
        if (d.studyLink) {
            d.studyLink.hidden = !((options && (options.studyable || options.finishedRuns > 0)) || this.ended[region]);
            d.studyLink.setAttribute("href", routeHash(studyRoute(region)));
        }
        // A live region (assertive): written only when it changes. Otherwise every redraw
        // while it shows (the options arriving, the home summary) would read it out again.
        const setupError = this.setupError ? t(this.setupError) : "";
        if (d.setupError.textContent !== setupError) d.setupError.textContent = setupError;
    }

    /** The small print beside an autofill choice: why it is closed, or what it means. */
    choiceNote(choice, options) {
        if (choice.reason === "locked") {
            return choice.remaining >= MINE_AFTER ? t("rollcall.autofill.locked") : tn("rollcall.autofill.lockedMore", choice.remaining);
        }
        if (choice.reason === "full") return t("rollcall.autofill.full");
        if (choice.value === "off") return t("rollcall.autofill.offNote");
        if (choice.value === "mine" && choice.available) {
            // "each of your last 2 games" until there are 3 to read (never fewer than 2).
            const runs = Number(options && options.finishedRuns) || MINE_LOOKBACK;
            return t("rollcall.autofill.mineNote", { n: formatInteger(Math.max(MINE_AFTER, Math.min(MINE_LOOKBACK, runs))) });
        }
        return "";
    }

    /** "Your best here: 162/197 in 14:03 (30 filled in)". */
    bestLine(best) {
        if (!best) return t("rollcall.setup.noBest");
        const line = t("rollcall.setup.best", {
            named: formatInteger(best.named),
            total: formatInteger(best.total),
            time: formatClock(best.seconds),
        });
        return best.autoCount > 0 ? `${line} ${tn("rollcall.setup.bestAuto", best.autoCount)}` : line;
    }

    /** Regions with a run in progress: this page's own knowledge where it is newer than the home summary. */
    regionsInProgress() {
        const out = new Set();
        const home = this.h.home();
        const stamp = this.h.homeStamp();
        const remote = home && Array.isArray(home.rollcall) ? home.rollcall : [];
        for (const region of ROLLCALL_REGIONS) {
            const local = this.runs[region];
            if (local !== undefined && (this.touched[region] || 0) > stamp) {
                if (local && local.status === "PLAYING") out.add(region);
            } else if (remote.some((r) => r && r.region === region)) {
                out.add(region);
            }
        }
        return out;
    }

    renderPlay(region, run) {
        const d = this.dom;
        const n = formatInteger(run.namedCount);
        const total = formatInteger(run.total);
        d.count.textContent = n;
        d.total.textContent = total;
        d.countSr.textContent = t("rollcall.play.count", { n, total });
        d.counter.classList.toggle("is-saving", this.isSending(region));
        this.renderClock();
        this.renderFeedback();
    }

    renderClock() {
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run) return;
        this.dom.clock.textContent = formatClock(this.seconds(region));
    }

    /** The stopwatch's reading: the server's final time once the run is over. */
    seconds(region) {
        const run = this.runs[region];
        if (!run) return 0;
        const clock = this.clocks[region];
        if (run.status === "PLAYING" && clock && clock.id === run.id) return clockSeconds(clock.start, performance.now());
        return run.elapsedSeconds;
    }

    startClock() {
        this.stopClock();
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run || run.status !== "PLAYING" || document.hidden) return;
        this.renderClock();
        const clock = this.clocks[region];
        this.tick = setTimeout(() => this.startClock(), clock ? msToNextSecond(clock.start, performance.now()) : 1000);
    }

    stopClock() {
        clearTimeout(this.tick);
        this.tick = 0;
    }

    /** Sets the line under the box and draws it. */
    say(feedback) {
        this.feedback = feedback;
        this.renderFeedback();
    }

    /**
     * The line under the box: "✓ 🇫🇷 France" for the latest name (with "(from
     * “frnace”)" when the spell check read it), "Did you mean [🇳🇷 Nauru]?" with a
     * button per country offered, or why the text does not count. A live region, so
     * it is only rewritten when it changes (or the language does): a redraw for each
     * saved name must not read it out again.
     */
    renderFeedback() {
        const d = this.dom;
        const f = this.feedback;
        const lang = getLang();
        if (this.drawnFeedback && this.drawnFeedback.f === f && this.drawnFeedback.lang === lang) return;
        this.drawnFeedback = { f, lang };
        const problem = !!f && f.kind !== "named" && f.kind !== "suggest";
        d.feedback.classList.toggle("is-problem", problem);
        d.feedback.classList.toggle("is-suggest", !!f && f.kind === "suggest");
        if (problem) d.input.setAttribute("aria-invalid", "true");
        else d.input.removeAttribute("aria-invalid");
        if (!f) {
            d.feedback.replaceChildren();
            return;
        }
        if (f.kind === "named") {
            const country = this.h.byCode().get(f.code);
            const name = country ? placeName(country) : f.code;
            const n = formatInteger(f.n);
            const total = formatInteger(f.total);
            d.feedback.replaceChildren(...[
                el("span", { class: "gl-rc-tick", "aria-hidden": "true", text: "✓" }),
                el("span", { "aria-hidden": "true", text: flagEmoji(country ? country.iso2 : null) }),
                el("span", { class: "gl-rc-fb-name", "aria-hidden": "true", text: name }),
                f.from ? el("span", { class: "gl-rc-fb-from", "aria-hidden": "true", text: t("rollcall.from", { text: f.from }) }) : null,
                el("span", {
                    class: "sr-only",
                    text: f.from ? t("rollcall.namedFrom", { name, text: f.from, n, total }) : t("rollcall.named", { name, n, total }),
                }),
            ].filter(Boolean));
            return;
        }
        if (f.kind === "suggest") {
            // "Did you mean {options}?" around the buttons, so each language orders it its own way.
            const [before, after = ""] = t("rollcall.didYouMean").split("{options}");
            const options = [];
            f.codes.forEach((code, i) => {
                if (i > 0) {
                    const country = this.h.byCode().get(code);
                    const startsWithO = /^h?o/i.test(normalize(country ? placeName(country) : code));
                    options.push(` ${t(startsWithO ? "rollcall.orBeforeO" : "rollcall.or")} `);
                }
                options.push(this.offerButton(code));
            });
            d.feedback.replaceChildren(...[
                before.trim() ? el("span", { class: "gl-rc-dym", text: before.trim() }) : null,
                ...options,
                after.trim() ? el("span", { class: "gl-rc-dym gl-rc-dym-end", text: after.trim() }) : null,
                f.codes.length === 1 ? el("span", { class: "sr-only", text: t("rollcall.suggest.enter") }) : null,
            ].filter(Boolean));
            return;
        }
        d.feedback.textContent = this.explanation(f);
    }

    /** One country offered by "Did you mean": tapping it counts it, and the box keeps focus. */
    offerButton(code) {
        const country = this.h.byCode().get(code);
        const button = el("button", { type: "button", class: "gl-rc-dym-btn", "data-code": code },
            el("span", { "aria-hidden": "true", text: flagEmoji(country ? country.iso2 : null) }),
            el("span", { text: country ? placeName(country) : code }));
        this.holdFocus(button);
        button.addEventListener("click", () => {
            const keyed = document.activeElement === button;
            this.pick(code);
            this.backToBox(keyed);
        });
        return button;
    }

    /** Why something typed (or sent) did not count, in the page's language. */
    explanation(f) {
        const country = f.code ? this.h.byCode().get(f.code) : null;
        const name = country ? placeName(country) : f.code || "";
        // "isn't in the Americas": a region inside a sentence, where a label alone reads wrong.
        const where = this.region();
        const region = has(`rollcall.in.${where}`) ? t(`rollcall.in.${where}`) : regionLabel(where);
        const reason = f.kind === "refused" || f.kind === "error" ? f.error : f.kind;
        // The spell check's guesses say so: "Did you mean Greenland? It doesn't count…"
        const guess = !!f.fuzzy;
        const countries = formatInteger(this.pool("world").size);
        switch (reason) {
            case "already":
            case "already-guessed":
                return t(guess ? "rollcall.fuzzy.already" : "rollcall.already", { name });
            case "territory":
            case "not-an-answer":
                return t(guess ? "rollcall.fuzzy.notCountry" : "rollcall.notCountry", { name, n: countries });
            case "outside":
            case "outside-region":
                return t(guess ? "rollcall.fuzzy.outside" : "error.outside-region", { name, region });
            case "part": {
                // A part that needs an article in a sentence ("the West Bank", "la Franja de
                // Gaza") is written that way, and the sentence starts with a capital.
                const term = f.term ? `rollcall.term.${normalize(f.term).replace(/ /g, "")}` : null;
                const part = term && has(term) ? t(term) : f.term || name;
                const said = t(forPlace(guess ? "rollcall.fuzzy.part" : "rollcall.part", f.code), { part, name });
                return said.charAt(0).toUpperCase() + said.slice(1);
            }
            case "former":
                return t(forPlace(guess ? "rollcall.fuzzy.former" : "rollcall.former", f.code), { old: f.term || name, name });
            case "unknown":
                return t("guess.notRecognised");
            case "unknown-country":
                return t("error.unknown-country");
            default: {
                const why = reason === "signed-out" ? t("session.expired")
                    : reason === "forbidden" ? t("session.stale")
                    : t(TRANSPORT_ERRORS.includes(reason) ? `error.${reason}` : "error.generic");
                if (f.kind !== "refused" || !name) return why;
                const lost = f.more > 0 ? tn("rollcall.notSavedMore", f.more, { name }) : t("rollcall.notSaved", { name });
                return `${lost} ${why}`;
            }
        }
    }

    /** Per-continent progress, for a world run (playing or over). */
    renderProgress(region, run) {
        const node = this.dom.progress;
        const rows = region === "world" && run && run.byRegion ? run.byRegion : [];
        node.hidden = !rows.length;
        if (!rows.length) {
            node.replaceChildren();
            return;
        }
        node.replaceChildren(
            el("h3", { class: "gl-section", text: t("rollcall.play.progress") }),
            el("ul", { class: "gl-rc-bars" }, rows.map((r) => {
                const n = formatInteger(r.named);
                const total = formatInteger(r.total);
                return el("li", { class: r.total && r.named >= r.total ? "gl-rc-bar-row is-full" : "gl-rc-bar-row" },
                    el("span", { class: "gl-rc-bar-label", "aria-hidden": "true", text: regionLabel(r.region) }),
                    el("span", { class: "gl-rc-bar-track", "aria-hidden": "true" },
                        el("span", { class: "gl-rc-bar", vars: { "--w": r.total ? Math.min(1, r.named / r.total) : 0 } })),
                    el("span", { class: "gl-rc-bar-n", "aria-hidden": "true", text: `${n}/${total}` }),
                    el("span", { class: "sr-only", text: t("rollcall.play.regionCount", { region: regionLabel(r.region), n, total }) }));
            })));
    }

    /** The countries named (and, once it is over, missed), by continent, alphabetical in the page's language. */
    renderLists(region, run) {
        const d = this.dom;
        d.namedSection.hidden = !run;
        const missed = run && run.status === "FINISHED" ? run.missed || [] : [];
        d.missedSection.hidden = !missed.length;
        if (!run) return;
        const byCode = this.h.byCode();
        const sort = { name: (country) => placeName(country), compare: compareText };
        const world = region === "world";
        const totals = new Map((run.byRegion || []).map((r) => [r.region, r.total]));
        const fresh = this.fresh && !this.fresh.drawn ? this.fresh.code : null;
        const latest = this.fresh ? this.fresh.code : null;
        if (this.fresh) this.fresh.drawn = true;
        const ctx = { region, runId: run.id };   // round 2: for the request sheet's quick picks

        d.namedCount.textContent = formatInteger(run.namedCount);
        d.namedEmpty.hidden = run.named.length > 0;
        d.named.replaceChildren(...groupByContinent(run.named, byCode, sort).map((group) =>
            this.group(group, world, (g) => totals.has(g.region)
                ? `${formatInteger(g.items.length)}/${formatInteger(totals.get(g.region))}`
                : formatInteger(g.items.length), { fresh, latest, ctx })));
        if (missed.length) {
            d.missedCount.textContent = formatInteger(missed.length);
            d.missed.replaceChildren(...groupByContinent(missed, byCode, sort).map((group) =>
                this.group(group, world, (g) => formatInteger(g.items.length), { missed: true, ctx })));
        } else {
            d.missed.replaceChildren();
        }
    }

    group(group, headed, count, { fresh = null, latest = null, missed = false, ctx = null } = {}) {
        const list = el("ul", { class: missed ? "gl-rc-names is-missed" : "gl-rc-names" }, group.items.map((item) => {
            const classes = ["gl-rc-name"];
            if (item.auto) classes.push("is-auto");
            if (item.pending) classes.push("is-saving");
            if (item.code === fresh) classes.push("is-new");
            else if (item.code === latest) classes.push("is-latest");
            return el("li", { class: classes.join(" "), title: item.auto ? t("rollcall.list.auto") : null },
                el("span", { class: "gl-rc-flag", "aria-hidden": "true", text: flagEmoji(item.iso2) }),
                el("span", { class: "gl-rc-label", text: item.name }),
                item.auto ? el("i", { class: "bi bi-stars gl-rc-auto", "aria-hidden": "true" }) : null,
                item.auto ? el("span", { class: "sr-only", text: ` (${t("rollcall.list.auto")})` }) : null,
                item.pending ? el("span", { class: "sr-only", text: ` (${t("rollcall.list.saving")})` }) : null,
                // Round 2: "request a name change" beside every country listed.
                this.h.requestButton ? this.h.requestButton(item.code, missed ? "missed" : "named", ctx || {}) : null);
        }));
        if (!headed) return list;
        return el("section", { class: "gl-rc-group" },
            el("h4", { class: "gl-rc-group-head" },
                el("span", { text: group.region ? regionLabel(group.region) : t("rollcall.list.other") }),
                el("span", { class: "gl-rc-group-n", text: count(group) })),
            list);
    }

    renderEnd(region, run) {
        const d = this.dom;
        const missed = run.missed || [];
        d.end.classList.toggle("is-won", missed.length === 0);
        d.endKicker.textContent = t(missed.length === 0 ? "rollcall.end.all" : "rollcall.end.kicker");
        d.endTitle.textContent = t("rollcall.end.title", { n: formatInteger(run.namedCount), total: formatInteger(run.total) });
        const parts = [t("rollcall.end.time", { time: formatClock(run.elapsedSeconds) })];
        if (run.autoCount > 0) parts.push(tn("rollcall.end.auto", run.autoCount));
        d.endMeta.textContent = parts.join(" · ");
        const ended = this.ended[region] || {};
        const note = !counts(run) ? "rollcall.end.noCount" : ended.newBest ? "rollcall.end.newBest" : null;
        d.endNote.hidden = !note;
        d.endNote.classList.toggle("is-best", note === "rollcall.end.newBest");
        d.endNote.textContent = note ? t(note) : "";
        // Round 2: "Study what you missed", when something was.
        if (d.study) {
            d.study.hidden = missed.length === 0;
            d.study.setAttribute("href", routeHash(studyRoute(region)));
        }
    }

    applyBusy() {
        const d = this.dom;
        const region = this.region();
        const locked = this.locked || this.h.signedOut();
        const busy = this.busy.starting || this.busy.finishing;
        const slot = region ? this.options[region] : null;
        const waitingForOptions = !slot || (!slot.data && !slot.error);
        d.start.disabled = locked || busy || waitingForOptions;
        d.start.classList.toggle("is-busy", this.busy.starting);
        d.done.disabled = locked || busy;
        d.over.disabled = locked || busy;
        d.go.disabled = locked || busy;
        d.input.readOnly = locked || busy;
        d.input.setAttribute("aria-busy", busy ? "true" : "false");
        d.again.disabled = locked;
    }

    // ------------------------------------------------------------------ the globe

    /** Named countries in green (filled-in ones deeper), and once it is over the missed ones hatched. */
    syncGlobe() {
        const region = this.region();
        if (!region) return;
        const run = this.shown(region);
        const guesses = (run ? run.named : []).map((n) => ({
            code: n.code,
            color: n.auto ? AUTO_COLOR : ANSWER_COLOR,
            capital: null,
        }));
        if (run && run.status === "FINISHED") {
            for (const code of run.missed || []) guesses.push({ code, color: MISSED_COLOR, capital: null, style: "missed" });
        }
        this.h.globe.setGame({ kind: "country", region, guesses, latestCode: null, answer: null, status: run ? run.status : "PLAYING" });
        if (guesses.length) this.h.hideStageHint();
    }

    aimGlobe() {
        const region = this.region();
        if (region && this.h.globe.ready) this.h.globe.focusRegion(region);
    }

    /**
     * The tooltip for a country on the globe: { iso2, title, detail }, or null. Only
     * a country already named, or once the run is over one missed. The globe picks
     * only what it was given, and this checks again: a name never leaks early.
     */
    tipFor(code) {
        const region = this.region();
        const run = region ? this.shown(region) : null;
        if (!run) return null;
        const country = this.h.byCode().get(code);
        const named = run.named.find((n) => n.code === code);
        let detail = null;
        if (named) detail = t(named.auto ? "rollcall.tip.auto" : "rollcall.tip.named");
        else if (run.status === "FINISHED" && (run.missed || []).includes(code)) detail = t("rollcall.tip.missed");
        if (!detail) return null;
        return { iso2: country ? country.iso2 : null, title: country ? placeName(country) : code, detail };
    }

    // ------------------------------------------------------------------ typing

    /** What rollcall.js needs to judge a text in the run on screen. */
    context() {
        const region = this.region();
        return {
            index: this.h.index(),
            pool: this.pool(region),
            named: namedCodes(this.shown(region)),
            byCode: this.h.byCode(),
        };
    }

    decide(text) {
        return decideTyping(text, this.context());
    }

    canType() {
        const region = this.region();
        const run = region ? this.runs[region] : null;
        return !!run && run.status === "PLAYING" && !this.blocked() && !!this.h.index();
    }

    clearBox() {
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.guard = null;
        this.dom.input.value = "";
        if (this.feedback && this.feedback.kind !== "named") this.say(null);
    }

    /** Every change to the box: accept a finished name at once, wait on one that may go on, or explain after a pause. */
    typed() {
        if (!this.canType()) return;
        const input = this.dom.input;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        // "Bosnia" was accepted, and " and Herzegovina" is still being typed: swallow
        // it rather than leave the end of one name to start the next.
        const guard = this.guard;
        if (guard) {
            const now = performance.now();
            if (now - guard.at <= GUARD_MS && continuesName(this.h.index(), guard.code, guard.text, guard.more + input.value)) {
                guard.more += input.value;
                guard.at = now;
                input.value = "";
                return;
            }
            this.guard = null;
        }
        if (this.feedback && this.feedback.kind !== "named") this.say(null);
        const decision = this.decide(input.value);
        if (decision.kind === "accept") {
            this.accept(decision.code, input.value);
        } else if (decision.kind === "wait") {
            this.waitTimer = setTimeout(() => this.waited(), waitFor(input.value));
        } else if (EXPLAINED.includes(decision.kind) && !decision.waits) {
            this.hintTimer = setTimeout(() => {
                const now = this.decide(input.value);
                if (now.kind === decision.kind && now.code === decision.code) this.say({ kind: now.kind, code: now.code, term: now.term || null });
            }, HINT_MS);
        }
    }

    /** The pause after a name that might have gone on: it was that one after all. */
    waited() {
        if (!this.canType()) return;
        const text = this.dom.input.value;
        const decision = this.decide(text);
        if (acceptable(decision)) this.accept(decision.code, text);
    }

    /**
     * Enter (or ↵): accept what is typed now, or let the spell check read it: a
     * misspelling it is sure of counts at once ("✓ Kyrgyzstan (from “kyrigistan”)"),
     * one it is not sure of is offered ("Did you mean Nauru?"), and anything that does
     * not count is explained. Enter again on the same text, while one country is
     * offered, takes it.
     */
    submit() {
        if (!this.canType()) return;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.guard = null;
        const text = this.dom.input.value;
        const offered = this.feedback;
        if (offered && offered.kind === "suggest" && offered.codes.length === 1 && offered.text === text) {
            this.pick(offered.codes[0]);
            return;
        }
        const decision = decideEnter(text, this.context());
        switch (decision.kind) {
            case "empty":
                return;
            case "accept":
                this.accept(decision.code, text);
                return;
            case "confident":
                this.accept(decision.code, text, { from: text.trim() });
                return;
            case "suggest":
                // The text stays, to be fixed or confirmed; typing anything clears the offer.
                this.say({ kind: "suggest", codes: decision.codes, text });
                return;
            default:
                // A name that does not count has nothing left to fix: clear it for the
                // next one. Something not recognised stays, to be corrected.
                if (EXPLAINED.includes(decision.kind)) this.dom.input.value = "";
                // The ONE place a typed text is noted as "not recognised", for the
                // name-change request sheet's quick picks (requests.js).
                if (decision.kind === "unknown") this.noteUnrecognised(text);
                this.say({ kind: decision.kind, code: decision.code, term: decision.term || null, fuzzy: !!decision.fuzzy });
        }
    }

    /** A country offered by "Did you mean" was taken (tapped, or Enter again): it counts. */
    pick(code) {
        const f = this.feedback;
        if (!this.canType() || !f || f.kind !== "suggest" || !f.codes.includes(code)) return;
        const region = this.region();
        // Named meanwhile (another device), or the run moved on: the offer is stale.
        if (namedCodes(this.shown(region)).has(code) || !this.pool(region).has(code)) {
            this.say(null);
            return;
        }
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.accept(code, f.text, { guard: false });
    }

    /**
     * A country named: shown at once, queued for the server, and the box ready for the next.
     *   from   the misspelling the spell check read it from, for the line under the box
     *   guard  swallow the rest of its long name if that is still being typed (not after
     *          Enter on a misspelling, or a "Did you mean" pick: there is no rest to come)
     */
    accept(code, text, { from = null, guard = !from } = {}) {
        const region = this.region();
        const run = this.runs[region];
        if (!run || run.status !== "PLAYING") return;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        // Emptied in place: the box keeps focus, so a phone's keyboard stays up.
        this.dom.input.value = "";
        this.guard = guard ? { code, text, more: "", at: performance.now() } : null;
        (this.pending[region] || (this.pending[region] = [])).push(code);
        this.version[region] = (this.version[region] || 0) + 1;
        this.touched[region] = performance.now();
        const shown = this.shown(region);
        this.fresh = { code, drawn: false };
        this.feedback = { kind: "named", code, n: shown.namedCount, total: shown.total, from };
        this.render();
        this.syncGlobe();
        const point = this.h.globe.centreOf(code);
        this.h.globe.flyTo(point);
        this.h.globe.pulse(point);
        this.pump(region);
    }

    /** Sends the queued names one at a time, in order; later ones wait for earlier answers. */
    async pump(region) {
        if (this.sending[region]) return;
        this.sending[region] = true;
        try {
            for (;;) {
                const queue = this.pending[region] || [];
                if (!queue.length) break;
                const run = this.runs[region];
                if (!run || run.status !== "PLAYING" || this.h.signedOut()) {
                    if (this.h.signedOut() && this.onScreen(region)) {
                        this.say({ kind: "refused", code: queue[0], error: "signed-out", more: queue.length - 1 });
                    }
                    this.drop(region);
                    break;
                }
                const code = queue[0];
                let next;
                try {
                    next = await api.rollCallName(run.id, code);
                } catch (error) {
                    if (queue[0] === code) queue.shift();
                    if (this.refused(region, code, error)) {
                        this.drop(region);
                        break;
                    }
                    if (this.onScreen(region)) {
                        this.render();
                        this.syncGlobe();
                    }
                    continue;
                }
                if (queue[0] === code) queue.shift();
                this.applyServer(region, next);
            }
        } finally {
            this.sending[region] = false;
            this.settled(region);
        }
    }

    /**
     * A name the server would not take: it comes off the screen with the reason.
     * Returns true when the rest of the queue cannot be sent either.
     */
    refused(region, code, error) {
        const here = this.onScreen(region);
        const kind = error && error.code;
        if (isAuthError(error)) {
            // The line said "✓ Chile": it must say it was not saved, and why.
            if (here) this.say({ kind: "refused", code, error: kind, more: (this.pending[region] || []).length });
            this.h.sessionExpired(kind);
            return true;
        }
        switch (kind) {
            case "already-guessed":
                // Named on another device meanwhile: the server has it. Fetch it back.
                this.resync[region] = true;
                if (here) this.say({ kind: "refused", code, error: kind });
                return false;
            case "not-an-answer":
            case "outside-region":
            case "unknown-country":
            case "bad-request":
                if (here) this.say({ kind: "refused", code, error: kind });
                return false;
            case "finished":
            case "not-found":
                // Finished, or started over, on another device.
                if (here) toast(t("rollcall.toast.ended"), 3600);
                this.resync[region] = true;
                return true;
            default:
                // Offline, a server error, a timeout: this and the names after it were not
                // saved, and the line says how many went with it (they vanish from the
                // list too). A timeout may have landed after all, so ask the server.
                if (kind === "timeout") this.resync[region] = true;
                if (here) this.say({ kind: "refused", code, error: kind || "generic", more: (this.pending[region] || []).length });
                return true;
        }
    }

    /** Takes back every name still queued. */
    drop(region) {
        this.pending[region] = [];
        // The latest name was among them: its "✓ Honduras" line must not stay under the
        // box. (The line belongs to the box's region, on screen or not.)
        if (this.boxRegion === region && this.feedback && this.feedback.kind === "named") this.say(null);
        if (this.onScreen(region)) {
            this.render();
            this.syncGlobe();
        }
    }

    /** The queue is empty: whoever waited for it goes on, and a resync it asked for runs. */
    settled(region) {
        if (this.isSending(region)) return;
        const waiters = this.waiters[region] || [];
        this.waiters[region] = [];
        if (this.resync[region]) {
            this.resync[region] = false;
            this.reload(region);
        }
        for (const resolve of waiters) resolve();
        if (this.onScreen(region)) this.render();
    }

    /** Resolves once every name typed so far has had its answer. */
    drain(region) {
        if (!this.isSending(region)) return Promise.resolve();
        return new Promise((resolve) => (this.waiters[region] || (this.waiters[region] = [])).push(resolve));
    }

    // ------------------------------------------------------------------ starting and ending

    async start() {
        const region = this.region();
        if (!region || this.runs[region] !== null || this.blocked()) return;
        const slot = this.options[region];
        const choices = autofillChoices(slot ? slot.data : null, { failed: !!slot && !!slot.error && !slot.data });
        const checked = $("input[name=rc-autofill]:checked", this.dom.fill);
        const autofill = pickAutofill(choices, checked ? checked.value : this.wanted);
        this.busy.starting = true;
        this.setupError = null;
        this.render();
        try {
            // This page saw none in progress, but another device may have started one
            // since; starting here would end it. Show that one instead.
            const current = await api.rollCallCurrent(region);
            if (current) {
                this.applyServer(region, current);
                if (this.onScreen(region)) {
                    toast(t("rollcall.toast.otherDevice"), 3600);
                    this.aimGlobe();
                }
                return;
            }
            const next = await api.rollCallNew(region, autofill);
            // The choice is remembered when the player makes it (the radios' change); a
            // fallback to "off" here ("mine" still locked in this region) is not one.
            this.fresh = null;
            this.feedback = null;
            this.dom.input.value = "";
            this.applyServer(region, next);
            this.h.homeChanged();
            if (this.onScreen(region) && next.status === "PLAYING") {
                this.aimGlobe();
                this.announceStart(region, next);
                // Start is gone with the setup card: focus must land in the play card.
                this.focusBox({ orCounter: true });
            }
        } catch (error) {
            // The setup card's error belongs to this region: one moved to meanwhile
            // must not show it on its own card.
            if (isAuthError(error)) {
                this.h.sessionExpired(error.code);
            } else if (error && error.code === "bad-request") {
                // "mine" closed, or would now fill in everything: the numbers changed.
                if (this.onScreen(region)) this.setupError = "rollcall.setup.unavailable";
                this.loadOptions(region, { force: true });
            } else if (this.onScreen(region)) {
                const code = error && error.code;
                this.setupError = TRANSPORT_ERRORS.includes(code) ? `error.${code}` : "error.generic";
            }
        } finally {
            this.busy.starting = false;
            this.unbusy(region);
        }
    }

    announceStart(region, run) {
        announce(tn("rollcall.announce.start", run.total - run.autoCount, { mode: modeLabel(rollCallRoute(region)) }));
    }

    /** "I'm done": after a confirmation, and after every name typed has been saved. */
    async done() {
        const region = this.region();
        const run = region ? this.shown(region) : null;
        if (!run || run.status !== "PLAYING" || this.blocked()) return;
        const ok = await confirmSheet(this.h.confirmDialog, {
            title: t("rollcall.confirm.doneTitle"),
            body: typedCount(run) > 0
                ? t("rollcall.confirm.doneBody", {
                    n: formatInteger(run.namedCount),
                    total: formatInteger(run.total),
                    time: formatClock(this.seconds(region)),
                })
                : t("rollcall.confirm.doneEmpty"),
            confirm: t("rollcall.confirm.doneOk"),
            cancel: t("rollcall.confirm.keepGoing"),
        });
        if (!ok || !this.onScreen(region)) return;
        this.busy.finishing = true;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.render();
        try {
            await this.drain(region);
            const current = this.runs[region];
            // The last name may have finished it, or it ended elsewhere.
            if (!current || current.status !== "PLAYING") return;
            this.applyServer(region, await api.rollCallFinish(current.id));
        } catch (error) {
            this.failed(region, error);
        } finally {
            this.busy.finishing = false;
            this.unbusy(region);
        }
    }

    /**
     * Start over: a new run in this region, filled in as this one was (or not at all,
     * if that is no longer open). The server finishes this one if anything was typed,
     * so it counts, and drops it otherwise; the confirmation says which.
     */
    async startOver() {
        const region = this.region();
        const run = region ? this.shown(region) : null;
        if (!run || run.status !== "PLAYING" || this.blocked()) return;
        const typed = typedCount(run);
        const ok = await confirmSheet(this.h.confirmDialog, {
            title: t("rollcall.confirm.overTitle"),
            body: typed > 0
                ? t("rollcall.confirm.overBody", {
                    n: formatInteger(run.namedCount),
                    total: formatInteger(run.total),
                    time: formatClock(this.seconds(region)),
                })
                : t("rollcall.confirm.overEmpty"),
            confirm: t("rollcall.confirm.overOk"),
            cancel: t("rollcall.confirm.keepGoing"),
        });
        if (!ok || !this.onScreen(region)) return;
        this.busy.starting = true;
        clearTimeout(this.waitTimer);
        clearTimeout(this.hintTimer);
        this.render();
        try {
            await this.drain(region);
            const before = this.runs[region];
            const options = await this.loadOptions(region, { force: true });
            const autofill = pickAutofill(autofillChoices(options, { failed: !options }), before ? before.autofill : "off");
            const next = await api.rollCallNew(region, autofill);
            // The run just abandoned may count now: fetch the numbers again, so this
            // run's end card judges "new best" against it.
            this.loadOptions(region, { force: true });
            this.fresh = null;
            this.feedback = null;
            this.guard = null;
            this.dom.input.value = "";
            this.applyServer(region, next);
            if (before && counts(before)) {
                toast(t("rollcall.toast.counted", { n: formatInteger(before.namedCount), total: formatInteger(before.total) }), 3600);
                this.h.runEnded(region);
            }
            this.h.homeChanged();
            if (this.onScreen(region) && next.status === "PLAYING") {
                this.aimGlobe();
                this.announceStart(region, next);
                this.focusBox();
            }
        } catch (error) {
            this.failed(region, error);
        } finally {
            this.busy.starting = false;
            this.unbusy(region);
        }
    }

    /**
     * A start, start over or finish for `region` is over. The busy flags are page-wide
     * (they lock the box and the buttons of every region), so whichever region is on
     * screen now is redrawn, and one moved to meanwhile gets the check against the
     * server that refresh() skipped while busy.
     */
    unbusy(region) {
        this.render();
        const now = this.region();
        if (now && now !== region) this.refresh(now);
    }

    /** A start, start over or finish that did not go through. */
    failed(region, error) {
        if (isAuthError(error)) {
            this.h.sessionExpired(error.code);
            return;
        }
        const code = error && error.code;
        const here = this.onScreen(region);
        if (code === "finished" || code === "not-found") {
            if (here) toast(t("rollcall.toast.ended"), 3600);
            this.reload(region);
            return;
        }
        if (code === "timeout") this.reload(region);
        if (here) this.say({ kind: "error", code: null, error: code || "generic" });
    }

    /** A run just ended (named in full, "I'm done", or on another device's word). */
    finished(region, state) {
        const slot = this.options[region];
        const before = slot && slot.data ? slot.data.best : null;
        // Only a run that beat an earlier best is a new one; the first ever is just a best.
        this.ended[region] = { newBest: !!before && beats(state, before) };
        if (slot) slot.stale = true;
        this.pending[region] = [];
        this.feedback = null;
        this.h.runEnded(region);
    }

    /** The end card arrives where the box was: say so, show the region, and keep it in view. */
    revealEnd(region, state) {
        this.stopClock();
        this.aimGlobe();
        announce(t("rollcall.announce.done", {
            n: formatInteger(state.namedCount),
            total: formatInteger(state.total),
            time: formatClock(state.elapsedSeconds),
        }));
        // The box and "I'm done" just went: Share takes focus on every device (a button
        // brings up no phone keyboard), so a screen reader is not left on nothing.
        this.dom.share.focus({ preventScroll: true });
        const top = this.dom.end.getBoundingClientRect().top;
        if (top < 0 || top > window.innerHeight - 140) {
            this.dom.end.scrollIntoView({ block: "nearest", behavior: this.h.reducedMotion ? "auto" : "smooth" });
        }
    }

    /** Play again: back to the setup card for this region, with the numbers fetched afresh. */
    again() {
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run || run.status !== "FINISHED") return;
        this.setRun(region, null);
        this.feedback = null;
        this.setupError = null;
        this.dom.shareFallback.hidden = true;
        this.loadOptions(region);
        this.redraw({ aim: true });
        if (!this.dom.start.disabled) this.dom.start.focus({ preventScroll: true });
    }

    // ------------------------------------------------------------------ share

    async share() {
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run || run.status !== "FINISHED") return;
        const text = this.shareTextFor(run);
        if (this.h.coarsePointer && typeof navigator.share === "function") {
            try {
                await navigator.share({ text });
                return;
            } catch (error) {
                if (error && error.name === "AbortError") return;
            }
        }
        if (await copyText(text)) {
            translateText(this.dom.shareLabel, "end.copied");
            clearTimeout(this.copiedTimer);
            this.copiedTimer = setTimeout(() => translateText(this.dom.shareLabel, "end.share"), 2000);
            toast(t("toast.copied"));
        } else {
            toast(t("toast.copyFailed"), 4000);
            const box = this.dom.shareFallback;
            box.value = text;
            box.hidden = false;
            box.focus();
            box.select();
        }
    }

    /** What Share gives for a finished run, in the page's language. */
    shareTextFor(run) {
        return rollCallShareText(run, { site: `${window.location.host}/globle` });
    }

    /**
     * The language changed: a share box that is showing (copying failed) takes the
     * new words, still ready to copy, without taking focus from the language control.
     */
    relocalizeShare() {
        const box = this.dom.shareFallback;
        if (box.hidden) return;
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run || run.status !== "FINISHED") {
            box.hidden = true;
            return;
        }
        box.value = this.shareTextFor(run);
    }

    // ------------------------------------------------------------------ home

    /**
     * What home's row says: { resume, best } for modes.js rollCallChipText. The world
     * run in progress, from this page if it has heard about it since the home summary
     * was asked for, else from the summary; and the best whole-world run.
     */
    homeSummary(home, stamp) {
        let resume = null;
        const local = this.runs.world;
        if (local !== undefined && (this.touched.world || 0) > stamp) {
            if (local && local.status === "PLAYING") resume = this.shown("world").namedCount;
        } else {
            const rows = home && Array.isArray(home.rollcall) ? home.rollcall : [];
            const row = rows.find((r) => r && r.region === "world");
            if (row) resume = row.namedCount;
        }
        return { resume, best: this.bestFor("world") };
    }

    /** The best finished run in a region: the options' if on hand, else (world) the board's row. */
    bestFor(region) {
        const slot = this.options[region];
        if (slot && slot.data) return slot.data.best || null;
        if (region !== "world") return null;
        const row = this.h.boardRow();
        return row && row.played ? { named: row.best, total: row.total, seconds: row.bestSeconds, autoCount: row.bestAutoCount } : null;
    }

    // ------------------------------------------------------------------ round 2: requests and study

    /** Remembers a text that was not recognised in the run on screen (newest first, at most 5). */
    noteUnrecognised(text) {
        const region = this.region();
        const run = region ? this.runs[region] : null;
        if (!run) return;
        const slot = this.unrecognised[region];
        const before = slot && slot.runId === run.id ? slot.texts : [];
        this.unrecognised[region] = { runId: run.id, texts: rememberText(before, text) };
    }

    /** That run's recent "not recognised" texts, newest first ([] for another run). */
    recentUnrecognised(region, runId = null) {
        const slot = this.unrecognised[region];
        if (!slot || (runId != null && slot.runId !== runId)) return [];
        return [...slot.texts];
    }

    /** Drops a finished run kept for its end card, so the region shows its setup card (Play again from the study page). */
    forgetFinished(region) {
        const run = this.runs[region];
        if (run && run.status === "FINISHED") this.setRun(region, null);
    }
}

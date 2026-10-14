// Name them all, round 2: "Request a name change".
//
// Three pieces, all here:
//   - the pencil button beside a country (Name them all's named and missed lists,
//     every study card), and the in-page sheet it opens: "Suggest a name for
//     <country>", a box, quick picks from this run's recent "not recognised" texts,
//     Send / Cancel;
//   - the admin's discreet count after the home screen's credit line ("✎ 3"),
//     shown only to the admin and only when something is open;
//   - the admin's page, #/requests: the requests newest first, Done / Dismiss each,
//     "Show resolved", and "Copy all" (plain text made for pasting into a chat).
//
// The rules and words are in study-logic.js (pure). Everything a player typed is
// shown through textContent (and <bdi>), never as markup, and the copied text
// drops characters that print nothing.

import { api } from "./api.js";
import { t, tn, getLang, locale, translateText } from "./i18n.js";
import { placeName, nameInSentence } from "./names.js";
import { flagEmoji, formatInteger } from "./format.js";
import { el, icon, setupDialog, openDialog, toast, copyText } from "./ui.js";
import { isRequests } from "./modes.js";
import {
    checkSuggestion, requestErrorKey, visibleText, sourceText, langText, copyAllText, SUGGESTION_MAX,
} from "./study-logic.js";

const SOURCES = ["named", "missed", "study", "other"];

function isAuthError(error) {
    return !!error && (error.code === "signed-out" || error.code === "forbidden");
}

export class NameRequests {
    /**
     * hooks, from main.js:
     *   route() nav()                 the route on screen; bumped on every change
     *   byCode()                      the country list by code
     *   home()                        the home summary (admin, nameRequests), or null
     *   recentTexts(region, runId)    this run's recent "not recognised" texts, newest first
     *   signedOut() sessionExpired(code)
     *   notAdmin()                    #/requests is not for this player: go home
     *   countChanged()                a request was resolved: home's count is stale
     */
    constructor(hooks) {
        this.h = hooks;
        // The sheet: what it is open for.
        this.target = null;      // { code, source, region, runId, opener }
        this.sending = false;
        this.sheetError = null;  // an i18n key
        // The admin's page.
        this.list = null;        // the requests as last fetched (open, or all)
        this.listAll = false;    // whether that fetch included the resolved ones
        this.showResolved = false;
        this.loading = null;
        this.loadError = null;
        this.resolving = new Set();   // ids with a Done / Dismiss / Reopen in flight
        this.copiedTimer = 0;

        const id = (name) => document.getElementById(name);
        this.dom = {
            dialog: id("dlg-request"),
            form: id("request-form"),
            title: id("request-title"),
            input: id("request-input"),
            picks: id("request-picks"),
            chips: id("request-chips"),
            error: id("request-error"),
            send: id("request-send"),
            panel: id("req-panel"),
            title2: id("req-title"),
            status: id("req-status"),
            resolved: id("req-resolved"),
            copy: id("req-copy"),
            copyLabel: id("req-copy-label"),
            items: id("req-list"),
            empty: id("req-empty"),
            fallback: id("req-copy-fallback"),
            counter: id("home-requests"),
        };
        this.wire();
    }

    wire() {
        const d = this.dom;
        setupDialog(d.dialog);
        d.form.addEventListener("submit", (e) => {
            e.preventDefault();
            this.send();
        });
        d.input.addEventListener("input", () => {
            if (this.sheetError) this.showSheetError(null);
        });
        d.dialog.addEventListener("close", () => this.sheetClosed());
        d.resolved.addEventListener("click", () => {
            this.showResolved = !this.showResolved;
            this.render();
            if (this.showResolved && !this.listAll) this.load();
        });
        d.copy.addEventListener("click", () => this.copyAll());
    }

    // ------------------------------------------------------------------ the pencil button

    /**
     * The small "request a name change" button for a country. source is where it
     * sits ("named", "missed", "study"); ctx is { region, runId } for the quick picks.
     */
    button(code, source, ctx = {}) {
        const country = this.h.byCode().get(code);
        const name = country ? nameInSentence(country) : code;
        const label = t("request.button", { name });
        const b = el("button", {
            type: "button",
            class: "gl-req-btn",
            title: label,
            "aria-label": label,
            "data-request-code": code,
            "data-request-source": source,
        }, icon("bi-pencil-square"));
        b.addEventListener("click", () => this.open({ code, source, region: ctx.region || null, runId: ctx.runId == null ? null : ctx.runId, opener: b }));
        return b;
    }

    // ------------------------------------------------------------------ the sheet

    open({ code, source, region = null, runId = null, opener = null }) {
        const d = this.dom;
        this.target = { code, source: SOURCES.includes(source) ? source : "other", region, runId, opener };
        this.sheetError = null;
        d.input.value = "";
        d.input.setAttribute("maxlength", String(SUGGESTION_MAX));
        this.renderSheet();
        openDialog(d.dialog, "#request-input");
    }

    /** The sheet's words and quick picks, in the page's language (again after a language change). */
    renderSheet() {
        const d = this.dom;
        const target = this.target;
        if (!target) return;
        const country = this.h.byCode().get(target.code);
        const name = country ? nameInSentence(country) : target.code;
        d.title.removeAttribute("data-i18n");   // its words are drawn here, with the country
        d.title.replaceChildren(
            el("span", { "aria-hidden": "true", class: "gl-request-flag", text: flagEmoji(country ? country.iso2 : null) }),
            " ",
            el("span", { text: t("request.title", { name }) }));
        const texts = target.region ? this.h.recentTexts(target.region, target.runId) : [];
        d.picks.hidden = !texts.length;
        d.chips.replaceChildren(...texts.map((text) => {
            const chip = el("button", { type: "button", class: "gl-request-chip", title: t("request.use", { text }), "aria-label": t("request.use", { text }) },
                el("bdi", { text }));
            chip.addEventListener("click", () => {
                d.input.value = text;
                if (this.sheetError) this.showSheetError(null);
                d.input.focus();
            });
            return chip;
        }));
        this.showSheetError(this.sheetError, { quiet: true });
        this.applySending();
    }

    showSheetError(key, { quiet = false } = {}) {
        this.sheetError = key;
        const text = key ? t(key) : "";
        // A live region: written only when it changes, so a redraw does not repeat it.
        if (this.dom.error.textContent !== text || !quiet) this.dom.error.textContent = text;
        if (key) this.dom.input.setAttribute("aria-invalid", "true");
        else this.dom.input.removeAttribute("aria-invalid");
    }

    applySending() {
        const d = this.dom;
        d.send.disabled = this.sending;
        d.send.classList.toggle("is-busy", this.sending);
        d.input.readOnly = this.sending;
        d.form.setAttribute("aria-busy", this.sending ? "true" : "false");
    }

    async send() {
        const d = this.dom;
        const target = this.target;
        if (!target || this.sending) return;
        const check = checkSuggestion(d.input.value);
        if (!check.ok) {
            this.showSheetError(check.key);
            d.input.focus();
            return;
        }
        this.sending = true;
        this.showSheetError(null);
        this.applySending();
        try {
            await api.sendNameRequest({ code: target.code, suggestion: check.text, lang: getLang(), source: target.source });
            if (this.target === target) d.dialog.close("sent");
            toast(t("request.sent"), 3000);
        } catch (error) {
            const code = error && error.code;
            if (this.target === target && d.dialog.open) {
                this.showSheetError(requestErrorKey(code));
                d.input.focus();
            }
            if (isAuthError(error)) this.h.sessionExpired(code);
        } finally {
            this.sending = false;
            this.applySending();
        }
    }

    /**
     * The sheet closed (sent, cancelled, Esc, or the route changed). setupDialog puts
     * focus back on the pencil that opened it; if a redraw replaced that button, this
     * finds its successor.
     */
    sheetClosed() {
        const target = this.target;
        this.target = null;
        this.sheetError = null;
        if (!target || !target.opener || target.opener.isConnected) return;
        const again = document.querySelector(`[data-request-code="${target.code}"][data-request-source="${target.source}"]`);
        if (again && document.activeElement && !document.querySelector("dialog[open]")) again.focus({ preventScroll: true });
    }

    // ------------------------------------------------------------------ the home count

    /** "✎ 3" after the credit line: the admin's open requests, when there are any. */
    renderHomeCounter(home) {
        const node = this.dom.counter;
        if (!node) return;
        const n = home && home.admin === true && Number.isFinite(home.nameRequests) ? home.nameRequests : 0;
        node.hidden = !(n > 0);
        if (!(n > 0)) {
            node.replaceChildren();
            return;
        }
        const label = tn("requests.homeCount", n);
        node.title = label;
        node.replaceChildren(
            el("span", { "aria-hidden": "true", text: `✎ ${formatInteger(n)}` }),
            el("span", { class: "sr-only", text: label }));
    }

    // ------------------------------------------------------------------ the admin's page

    onScreen() {
        return isRequests(this.h.route());
    }

    /** Called after main.js has drawn #/requests: a non-admin goes home, the admin gets the list. */
    async enter() {
        const home = this.h.home();
        if (home && home.admin === false) {
            this.h.notAdmin();
            return;
        }
        await this.load();
    }

    /** The home summary arrived while #/requests is on screen. */
    homeChanged() {
        const home = this.h.home();
        if (this.onScreen() && home && home.admin === false) this.h.notAdmin();
    }

    load() {
        if (this.loading) return this.loading;
        const all = this.showResolved;
        this.loadError = null;
        this.loading = (async () => {
            try {
                const list = await api.nameRequests(all ? "all" : "open");
                this.list = Array.isArray(list) ? list : [];
                this.listAll = all;
            } catch (error) {
                const code = error && error.code;
                if (code === "not-found") {
                    // Not the admin: there is nothing here for them.
                    this.list = null;
                    if (this.onScreen()) this.h.notAdmin();
                    return;
                }
                this.loadError = error;
                if (isAuthError(error)) this.h.sessionExpired(code);
            } finally {
                this.loading = null;
            }
            if (this.onScreen()) {
                this.render();
                // Show resolved was switched on while the open list was loading.
                if (this.showResolved && !this.listAll && this.list) this.load();
            }
        })();
        if (this.onScreen()) this.render();
        return this.loading;
    }

    /** The requests this page shows now: open ones, or every one with Show resolved. */
    shownList() {
        const list = this.list || [];
        return this.showResolved ? list : list.filter((r) => r && r.status === "OPEN");
    }

    nameOf(code) {
        const country = this.h.byCode().get(code);
        return country ? placeName(country) : code;
    }

    render() {
        if (!this.onScreen()) return;
        const d = this.dom;
        d.resolved.setAttribute("aria-pressed", this.showResolved ? "true" : "false");
        const waiting = !this.list && !this.loadError;
        const failed = !this.list && !!this.loadError;
        d.status.hidden = !(waiting || failed || (this.showResolved && !this.listAll && this.loading));
        if (failed) {
            const retry = el("button", { type: "button", class: "gl-btn gl-btn-sm" }, t("common.tryAgain"));
            retry.addEventListener("click", () => this.load());
            d.status.replaceChildren(el("span", { text: `${t("requests.failed")} ` }), retry);
        } else if (!d.status.hidden) {
            d.status.replaceChildren(el("span", { class: "gl-spinner", "aria-hidden": "true" }), ` ${t("common.loading")}`);
        }
        const shown = this.list ? this.shownList() : [];
        d.copy.disabled = !shown.length;
        d.empty.hidden = !this.list || shown.length > 0;
        d.empty.textContent = this.showResolved ? t("requests.emptyAll") : t("requests.empty");
        d.title2.replaceChildren(
            el("span", { text: t("requests.title") }), " ",
            el("span", { class: "gl-count", text: formatInteger(this.list ? this.list.filter((r) => r.status === "OPEN").length : 0) }));
        d.items.replaceChildren(...shown.map((r) => this.item(r)));
    }

    when(instant) {
        const date = new Date(instant);
        if (Number.isNaN(date.getTime())) return "";
        return date.toLocaleString(locale(), { year: "numeric", month: "short", day: "numeric", hour: "numeric", minute: "2-digit" });
    }

    /** One request: the country, the suggestion, who and when, and its buttons. */
    item(r) {
        const country = this.h.byCode().get(r.code);
        const name = this.nameOf(r.code);
        const suggestion = visibleText(r.suggestion || "");
        const open = r.status === "OPEN";
        const busy = this.resolving.has(r.id);
        const meta = [
            t("requests.by", { name: visibleText(r.username || "").text }),
            this.when(r.createdAt),
            sourceText(r.source),
            langText(r.lang),
        ].filter(Boolean).join(" · ");
        let state = null;
        if (r.status === "DONE") {
            state = r.resolvedBy === "data" ? t("requests.state.data") : t("requests.state.done", { name: visibleText(r.resolvedBy || "").text });
        } else if (r.status === "DISMISSED") {
            state = t("requests.state.dismissed", { name: visibleText(r.resolvedBy || "").text });
        }
        const button = (status, key, labelKey, iconName, cls) => {
            const b = el("button", {
                type: "button",
                class: `gl-btn gl-btn-sm ${cls}`,
                "aria-label": t(labelKey, { name, suggestion: suggestion.text }),
                disabled: busy,
            }, icon(iconName), " ", el("span", { text: t(key) }));
            b.addEventListener("click", () => this.resolve(r, status, b));
            return b;
        };
        const actions = open
            ? [button("done", "requests.done", "requests.doneFor", "bi-check-lg", "gl-btn-primary"),
                button("dismissed", "requests.dismiss", "requests.dismissFor", "bi-x-lg", "gl-btn-ghost")]
            : [button("open", "requests.reopen", "requests.reopenFor", "bi-arrow-counterclockwise", "gl-btn-ghost")];
        return el("li", { class: `gl-req-item is-${String(r.status || "open").toLowerCase()}`, "data-id": r.id },
            el("p", { class: "gl-req-place" },
                el("span", { class: "gl-req-flag", "aria-hidden": "true", text: flagEmoji(country ? country.iso2 : null) }),
                el("strong", { text: name }),
                el("span", { class: "gl-req-code", text: r.code })),
            el("p", { class: "gl-req-suggestion" },
                el("span", { class: "sr-only", text: `${t("requests.suggested")} ` }),
                el("span", { "aria-hidden": "true", text: "← " }),
                "“", el("bdi", { text: suggestion.text }), "”",
                suggestion.removed ? el("span", { class: "gl-req-cleaned", text: ` ${t("requests.cleaned")}` }) : null),
            el("p", { class: "gl-req-meta", text: meta }),
            state ? el("p", { class: "gl-req-state", text: state }) : null,
            el("div", { class: "gl-req-actions" }, actions));
    }

    async resolve(r, status, buttonEl) {
        if (this.resolving.has(r.id)) return;
        const items = [...this.dom.items.children];
        const index = items.findIndex((li) => li.getAttribute("data-id") === String(r.id));
        this.resolving.add(r.id);
        this.render();
        let next = null;
        try {
            next = await api.resolveNameRequest(r.id, status);
        } catch (error) {
            const code = error && error.code;
            if (code === "not-found" && this.onScreen()) {
                // Gone (or this is no longer the admin): read the list again.
                toast(t("requests.gone"), 3600);
                this.resolving.delete(r.id);
                this.list = null;
                this.load();
                return;
            }
            if (isAuthError(error)) this.h.sessionExpired(code);
            else toast(t(code === "offline" ? "error.offline" : "error.generic"), 4000);
        } finally {
            this.resolving.delete(r.id);
        }
        if (next && this.list) {
            this.list = this.list.map((x) => (x.id === next.id ? next : x));
            const word = next.status === "DONE" ? "requests.toast.done" : next.status === "DISMISSED" ? "requests.toast.dismissed" : "requests.toast.reopened";
            toast(t(word, { name: this.nameOf(next.code) }), 2400);
            this.h.countChanged();
        }
        if (!this.onScreen()) return;
        const hadFocus = buttonEl && document.activeElement === buttonEl;
        this.render();
        if (hadFocus || (buttonEl && !buttonEl.isConnected)) this.refocus(index);
    }

    /** After an item left the list (or was redrawn): focus the one now in its place, or the title. */
    refocus(index) {
        const items = [...this.dom.items.children];
        const li = items[Math.min(Math.max(0, index), items.length - 1)];
        const target = li ? li.querySelector("button") : null;
        if (target) target.focus({ preventScroll: true });
        else this.dom.title2.focus({ preventScroll: true });
    }

    async copyAll() {
        const shown = this.shownList();
        if (!shown.length) return;
        const text = copyAllText(shown, { nameOf: (code) => this.nameOf(code) });
        const box = this.dom.fallback;
        if (await copyText(text)) {
            box.hidden = true;
            translateText(this.dom.copyLabel, "end.copied");
            clearTimeout(this.copiedTimer);
            this.copiedTimer = setTimeout(() => translateText(this.dom.copyLabel, "requests.copyAll"), 2000);
            toast(tn("requests.copied", shown.length));
        } else {
            toast(t("toast.copyFailed"), 4000);
            box.value = text;
            box.hidden = false;
            box.focus();
            box.select();
        }
    }

    /** The language changed: the sheet (if open) and a copy box showing take the new words. */
    relocalize() {
        if (this.target && this.dom.dialog.open) this.renderSheet();
        const box = this.dom.fallback;
        if (!box.hidden && this.list) box.value = copyAllText(this.shownList(), { nameOf: (code) => this.nameOf(code) });
        this.renderHomeCounter(this.h.home());
    }

    /** Start again from nothing (the page came back from the back/forward cache). */
    reset() {
        this.list = null;
        this.listAll = false;
        this.loadError = null;
    }
}

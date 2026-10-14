// The guess box: an ARIA 1.2 combobox with a listbox popup.
//
// Matching and ranking live in match.js; this is only the widget. It never sends
// a guess itself: it calls onPick(code) and the game decides what happens. Already
// guessed places stay in the list, marked, so typing one gives a clear "you
// already tried that" instead of silently showing nothing.
//
// Hard mode has no suggestions at all: setEnabled(false) takes the listbox out of
// the page and strips the combobox roles, leaving a plain text input.

import { search, findExact } from "./match.js";
import { flagEmoji } from "./format.js";
import { t } from "./i18n.js";

let uid = 0;

export class Autocomplete {
    /**
     *   input      the <input role="combobox">
     *   listbox    the <ul role="listbox"> it controls
     *   onPick(code)                 a place was chosen
     *   onProblem(kind, result)      "already-guessed" | "unknown" | "choose"
     *   onType()                     the text changed (clears stale errors)
     */
    constructor({ input, listbox, onPick, onProblem, onType }) {
        this.input = input;
        this.listbox = listbox;
        this.onPick = onPick;
        this.onProblem = onProblem || (() => {});
        this.onType = onType || (() => {});
        this.index = null;
        this.guessed = new Set();
        this.results = [];
        this.active = -1;
        this.idBase = `gl-opt-${++uid}`;
        this.locked = false;
        this.enabled = false;
        this.home = listbox.parentNode;

        listbox.setAttribute("role", "listbox");
        this.setEnabled(true);

        input.addEventListener("input", () => {
            this.onType();
            if (this.enabled) this.update();
        });
        input.addEventListener("keydown", (e) => {
            if (this.enabled) this.onKey(e);
        });
        input.addEventListener("focus", () => {
            if (this.enabled && this.input.value.trim()) this.update();
        });
        // Clicking an option must not blur the input first, or the list closes
        // under the pointer and the click lands on whatever was behind it.
        // mousedown (not pointerdown) is the event whose default moves focus,
        // and touch browsers fire it too, just before the click.
        listbox.addEventListener("mousedown", (e) => e.preventDefault());
        listbox.addEventListener("click", (e) => {
            const option = e.target.closest("[role=option]");
            if (!option) return;
            const result = this.results[Number(option.dataset.index)];
            if (result) this.choose(result);
        });
        input.addEventListener("blur", () => this.close());
    }

    /**
     * On: a combobox with suggestions. Off: a plain text box. The listbox leaves the
     * document, so nothing can pop up and nothing announces one.
     */
    setEnabled(on) {
        on = !!on;
        if (on === this.enabled) return;
        this.enabled = on;
        const input = this.input;
        if (on) {
            if (!this.listbox.isConnected && this.home) this.home.append(this.listbox);
            input.setAttribute("role", "combobox");
            input.setAttribute("aria-autocomplete", "list");
            input.setAttribute("aria-expanded", "false");
            input.setAttribute("aria-controls", this.listbox.id);
        } else {
            this.close();
            this.results = [];
            this.listbox.replaceChildren();
            this.listbox.remove();
            for (const name of ["role", "aria-autocomplete", "aria-expanded", "aria-controls", "aria-activedescendant"]) {
                input.removeAttribute(name);
            }
        }
    }

    /** Swap the search index (switching between countries and capitals). */
    setIndex(index) {
        this.index = index;
        this.clear();
    }

    setGuessed(codes) {
        this.guessed = new Set(codes);
        if (this.isOpen()) this.update();
    }

    /** While a guess is in flight: keep focus and the keyboard, refuse input. */
    setLocked(locked) {
        this.locked = locked;
        this.input.readOnly = locked;
        this.input.setAttribute("aria-busy", locked ? "true" : "false");
        if (locked) this.close();
    }

    clear() {
        this.input.value = "";
        this.results = [];
        this.close();
    }

    isOpen() {
        return this.enabled && this.input.getAttribute("aria-expanded") === "true";
    }

    update() {
        if (this.locked || !this.enabled) return;
        this.results = search(this.index, this.input.value, { limit: 8, guessed: this.guessed });
        this.active = -1;
        this.render();
    }

    render() {
        const list = this.listbox;
        list.replaceChildren();
        this.results.forEach((r, i) => {
            const li = document.createElement("li");
            li.id = `${this.idBase}-${i}`;
            li.className = "gl-opt";
            li.setAttribute("role", "option");
            li.setAttribute("aria-selected", i === this.active ? "true" : "false");
            li.dataset.index = String(i);
            if (r.guessed) {
                li.classList.add("is-guessed");
                li.setAttribute("aria-disabled", "true");
            }

            const flag = document.createElement("span");
            flag.className = "gl-opt-flag";
            flag.setAttribute("aria-hidden", "true");
            flag.textContent = flagEmoji(r.iso2);

            const text = document.createElement("span");
            text.className = "gl-opt-text";
            const name = document.createElement("span");
            name.className = "gl-opt-name";
            name.textContent = r.label;
            text.append(name);
            if (r.hint) {
                const hint = document.createElement("span");
                hint.className = "gl-opt-hint";
                hint.textContent = r.hint;
                text.append(hint);
            }
            li.append(flag, text);

            if (r.guessed || r.neverAnswer) {
                // A territory stays selectable: its distance still helps.
                const tag = document.createElement("span");
                tag.className = "gl-opt-tag";
                tag.textContent = t(r.guessed ? "ac.guessed" : "ac.neverAnswer");
                li.append(tag);
            }
            list.append(li);
        });

        const open = this.results.length > 0 && document.activeElement === this.input;
        list.hidden = !open;
        this.input.setAttribute("aria-expanded", open ? "true" : "false");
        this.syncActive();
    }

    syncActive() {
        const options = this.listbox.children;
        for (let i = 0; i < options.length; i++) {
            options[i].setAttribute("aria-selected", i === this.active ? "true" : "false");
        }
        if (this.active >= 0 && options[this.active]) {
            this.input.setAttribute("aria-activedescendant", options[this.active].id);
            options[this.active].scrollIntoView({ block: "nearest" });
        } else {
            this.input.removeAttribute("aria-activedescendant");
        }
    }

    close() {
        this.listbox.hidden = true;
        if (this.enabled) this.input.setAttribute("aria-expanded", "false");
        this.input.removeAttribute("aria-activedescendant");
        this.active = -1;
    }

    move(delta) {
        if (!this.results.length) return;
        if (!this.isOpen()) {
            this.render();
            if (!this.isOpen()) return;
        }
        const n = this.results.length;
        this.active = this.active < 0
            ? (delta > 0 ? 0 : n - 1)
            : (this.active + delta + n) % n;
        this.syncActive();
    }

    onKey(e) {
        if (this.locked) {
            if (e.key === "Enter") e.preventDefault();
            return;
        }
        switch (e.key) {
            case "ArrowDown":
                e.preventDefault();
                if (!this.results.length && this.input.value.trim()) this.update();
                this.move(1);
                break;
            case "ArrowUp":
                e.preventDefault();
                this.move(-1);
                break;
            case "Enter":
                e.preventDefault();
                this.submit();
                break;
            case "Escape":
                if (this.isOpen()) {
                    e.preventDefault();
                    this.close();
                } else if (this.input.value) {
                    e.preventDefault();
                    this.clear();
                    this.onType();
                }
                break;
            case "Tab":
                this.close();
                break;
            default:
                break;
        }
    }

    /**
     * Enter, or the submit button. A highlighted option wins; otherwise the text
     * has to name exactly one place. Anything else highlights the best match and
     * asks the player to confirm it, rather than guessing on their behalf.
     */
    submit() {
        if (this.locked) return;
        if (this.isOpen() && this.active >= 0 && this.results[this.active]) {
            this.choose(this.results[this.active]);
            return;
        }
        const text = this.input.value.trim();
        if (!text) return;
        const code = findExact(this.index, text);
        if (code) {
            const result = search(this.index, text, { limit: 50, guessed: this.guessed }).find((r) => r.code === code);
            this.choose(result || { code, label: text, guessed: this.guessed.has(code) });
            return;
        }
        this.update();
        if (this.results.length) {
            this.move(1);
            this.onProblem("choose", this.results[0]);
        } else {
            this.onProblem("unknown", null);
        }
    }

    choose(result) {
        if (result.guessed) {
            this.onProblem("already-guessed", result);
            this.close();
            return;
        }
        this.close();
        this.onPick(result.code, result);
    }
}

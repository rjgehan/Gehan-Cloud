// Keeps the globe in view while a phone's keyboard is open.
//
// The globe is what a guess is read against, but on a phone the page is taller
// than what is left above the keyboard. Left alone, the browser scrolls the
// focused box into view and pushes the globe off the top, so the player types
// while staring at the box. While a guess box (any mode) has the on-screen
// keyboard open on a narrow screen, this switches the page into a typing layout:
// the header, the game bar and anything else between the globe and the box are
// put away, the app is pinned to exactly the part of the screen above the
// keyboard, and the globe shrinks to fill what the box leaves. Closing the
// keyboard (or leaving the box) puts the page back as it was.
//
// "The keyboard is open" is read from the visual viewport: it shrinks when an
// on-screen keyboard appears, and stays put for a hardware keyboard or a narrow
// desktop window, which therefore keep the normal layout. Pinning to the visual
// viewport (rather than scrolling the page) is what works on iOS, where the
// keyboard pans the visible area over a layout viewport that does not shrink.
//
// The CSS side is the body.gl-typing block in globle.css. Nothing here changes
// what is typed or sent; it only moves things on screen.

/** How much the visible area must shrink, in CSS px, to count as a keyboard. */
const KEYBOARD_MIN_PX = 120;
/** The globe never shrinks below this, however little room the keyboard leaves. */
const MIN_STAGE_PX = 120;
/** Room left under the box (its feedback line included) above the keyboard. */
const BOTTOM_GAP_PX = 6;
/** Leaving the box for this long ends typing; a quick refocus does not flicker. */
const BLUR_GRACE_MS = 150;

const BOXES = "#guess-input, #rc-input";

function isBox(node) {
    return node instanceof HTMLInputElement && node.matches(BOXES);
}

/**
 * Starts watching. Returns a function that stops watching and restores the page.
 *
 *   stage  the globe's container (#stage)
 *   body   where the gl-typing class and the size variables go
 */
export function installTypingLayout({ stage, body = document.body } = {}) {
    const vv = window.visualViewport;
    if (!vv || !stage) return () => {};

    // The single-column layout is the only one where the box sits under the
    // globe; the two-column desktop layout keeps both in view already.
    const narrow = window.matchMedia("(max-width: 959.98px)");
    // The visible height with no keyboard: the tallest seen while nobody types,
    // started again whenever the width changes (the phone was turned). Taking the
    // tallest, not the latest, means a keyboard still closing when the box is
    // tapped again cannot lower it.
    let baseline = vv.height;
    let lastWidth = Math.round(vv.width);
    let turned = false;
    let box = null;             // the guess box being typed in, if any
    let blurTimer = 0;
    let frame = 0;

    const schedule = () => {
        if (!frame) frame = window.requestAnimationFrame(apply);
    };

    // The line under a box can grow while the keyboard is up (Name them all's "Did
    // you mean" buttons, an explanation that wraps): the globe then has to give up the
    // room, or the line would slip under the keyboard. The form being typed in is
    // watched for that while it has focus.
    const resized = typeof ResizeObserver === "function" ? new ResizeObserver(schedule) : null;
    const watch = (node) => {
        if (!resized) return;
        resized.disconnect();
        const form = node && (node.form || node.closest("form"));
        if (form) resized.observe(form);
    };

    function stop() {
        if (!body.classList.contains("gl-typing")) return;
        body.classList.remove("gl-typing");
        for (const name of ["--gl-vv-top", "--gl-vv-h", "--gl-typing-stage-h", "--gl-typing-list-h"]) {
            body.style.removeProperty(name);
        }
        // The page underneath was not scrolled while the app was pinned; start it
        // from the top, where the globe is.
        window.scrollTo(0, 0);
    }

    function wanted() {
        if (!box || !narrow.matches) return false;
        if (document.activeElement !== box || !box.isConnected) return false;
        // A box in a hidden form (the game ended under it) is not being typed in.
        if (box.closest("[hidden]")) return false;
        // Pinch-zoomed: the visible area is small because of the zoom, not a keyboard.
        if (vv.scale > 1.01) return false;
        return baseline - vv.height >= KEYBOARD_MIN_PX;
    }

    function apply() {
        frame = 0;
        // A box can hold focus without a focusin having reached us: focused before
        // this was installed, or while the page itself was not focused. Whatever has
        // focus when the keyboard moves the viewport is the box being typed in.
        if (!box && isBox(document.activeElement)) {
            box = document.activeElement;
            watch(box);
        }
        if (!wanted()) {
            stop();
            return;
        }
        body.style.setProperty("--gl-vv-top", `${Math.round(vv.offsetTop)}px`);
        body.style.setProperty("--gl-vv-h", `${Math.floor(vv.height)}px`);
        body.classList.add("gl-typing");

        // Measured with the typing layout in place: everything from the globe's
        // bottom to the box's bottom (its feedback line included) has to fit under
        // the globe, so the globe gets the rest. None of that depends on the
        // globe's own height, so one measurement is enough.
        const form = box.form || box.closest("form") || box;
        const app = stage.parentElement;
        const appTop = app.getBoundingClientRect().top;
        const stageRect = stage.getBoundingClientRect();
        const below = form.getBoundingClientRect().bottom - stageRect.bottom;
        const above = stageRect.top - appTop;
        const height = Math.max(MIN_STAGE_PX, Math.floor(vv.height - above - below - BOTTOM_GAP_PX));
        body.style.setProperty("--gl-typing-stage-h", `${height}px`);
        // Suggestions open upwards over the globe, since below the box is the
        // keyboard. They take at most a little over half of it (they scroll), so
        // the top of the globe stays in sight while picking.
        body.style.setProperty("--gl-typing-list-h", `${Math.max(96, Math.floor(height * 0.55))}px`);
    }

    function onFocusIn(e) {
        const target = e.target;
        if (!isBox(target)) return;
        window.clearTimeout(blurTimer);
        box = target;
        watch(box);
        schedule();
    }

    function onFocusOut(e) {
        if (e.target !== box) return;
        window.clearTimeout(blurTimer);
        blurTimer = window.setTimeout(() => {
            if (document.activeElement !== box) {
                box = null;
                watch(null);
            }
            schedule();
        }, BLUR_GRACE_MS);
    }

    function onViewport() {
        const width = Math.round(vv.width);
        if (width !== lastWidth) {
            lastWidth = width;
            turned = true;
        }
        if (!box) {
            baseline = turned ? vv.height : Math.max(baseline, vv.height);
            turned = false;
        }
        schedule();
    }

    document.addEventListener("focusin", onFocusIn);
    document.addEventListener("focusout", onFocusOut);
    vv.addEventListener("resize", onViewport);
    vv.addEventListener("scroll", schedule);
    narrow.addEventListener("change", schedule);

    return () => {
        document.removeEventListener("focusin", onFocusIn);
        document.removeEventListener("focusout", onFocusOut);
        vv.removeEventListener("resize", onViewport);
        vv.removeEventListener("scroll", schedule);
        narrow.removeEventListener("change", schedule);
        window.clearTimeout(blurTimer);
        if (frame) window.cancelAnimationFrame(frame);
        frame = 0;
        box = null;
        watch(null);
        stop();
    };
}

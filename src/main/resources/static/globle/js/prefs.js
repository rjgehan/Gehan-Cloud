// Per-device preferences: units, blind mode, last mode and region, whether the
// rules have been seen.
//
// localStorage throws in some private-browsing modes and when storage is full or
// blocked, and none of this is worth breaking the game over, so every access is
// wrapped and a failure just means "use the default".

const PREFIX = "globle.";

export function getPref(key, fallback) {
    try {
        const value = window.localStorage.getItem(PREFIX + key);
        return value === null ? fallback : value;
    } catch {
        return fallback;
    }
}

export function setPref(key, value) {
    try {
        window.localStorage.setItem(PREFIX + key, String(value));
    } catch {
        // Not remembered this time; nothing else to do.
    }
}

/** A stored value, only if it is one of the allowed ones. Guards against stale keys. */
export function getChoice(key, allowed, fallback) {
    const value = getPref(key, fallback);
    return allowed.includes(value) ? value : fallback;
}

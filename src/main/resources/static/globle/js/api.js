// The Globle HTTP API, as plain async functions.
//
// Every failure comes out as an ApiError with a short code, so the UI has one
// switch statement to write instead of inspecting responses. The codes are the
// server's own ("unknown-country", "finished", ...) plus a few for transport
// problems the server never gets to answer:
//
//   signed-out    401, or a redirect / HTML page where JSON was expected: the
//                 login page
//   forbidden     403 while still signed in: this page's CSRF token went stale
//                 (a sign-in in another tab starts a new session); a reload fixes it
//   offline       the request never got a response
//   timeout       it took too long; the guess may or may not have landed
//   server        5xx, or an error page that is not ours
//   bad-response  a 2xx that is not the JSON we expect

const TIMEOUT_MS = 15000;

export class ApiError extends Error {
    constructor(code, status = 0) {
        super(code);
        this.name = "ApiError";
        this.code = code;
        this.status = status;
    }
}

function meta(name) {
    const el = document.querySelector(`meta[name="${name}"]`);
    return el ? el.getAttribute("content") : null;
}

// Spring Security's token and header name, rendered into the page by Thymeleaf.
const csrfToken = meta("_csrf");
const csrfHeader = meta("_csrf_header");

async function request(method, url, body, { cache = "no-store" } = {}) {
    const headers = { Accept: "application/json" };
    if (method !== "GET") {
        headers["Content-Type"] = "application/json";
        if (csrfHeader && csrfToken) {
            headers[csrfHeader] = csrfToken;
        }
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), TIMEOUT_MS);
    let res;
    try {
        res = await fetch(url, {
            method,
            headers,
            body: body === undefined ? undefined : JSON.stringify(body),
            credentials: "same-origin",
            // A signed-out request is bounced to /login. Following that would hand
            // us the login page as if it were data; stopping at the redirect lets
            // us say "session expired" instead.
            redirect: "manual",
            cache,
            signal: controller.signal,
        });
    } catch (e) {
        throw new ApiError(e && e.name === "AbortError" ? "timeout" : "offline");
    } finally {
        clearTimeout(timer);
    }

    if (res.type === "opaqueredirect" || res.status === 401) {
        throw new ApiError("signed-out", res.status);
    }
    if (res.status === 403) {
        // The API says "forbidden" when the session is fine but the token is not;
        // any other 403 can only mean the session is gone.
        const body = await res.json().catch(() => null);
        throw new ApiError(body && body.error === "forbidden" ? "forbidden" : "signed-out", 403);
    }
    if (res.status === 204) {
        return null;
    }

    const type = res.headers.get("content-type") || "";
    if (!type.includes("json")) {
        // A 200 HTML page where JSON belongs is the login form arriving some other way.
        throw new ApiError(res.ok ? "signed-out" : "server", res.status);
    }

    let data;
    try {
        data = await res.json();
    } catch {
        throw new ApiError(res.ok ? "bad-response" : "server", res.status);
    }

    if (!res.ok) {
        const code = data && typeof data.error === "string" ? data.error
            : res.status >= 500 ? "server" : "bad-request";
        throw new ApiError(code, res.status);
    }
    return data;
}

const BASE = "/globle/api";

export const api = {
    countries: () => request("GET", `${BASE}/countries`),
    // A static file, served no-cache with an ETag (GlobleStaticCaching): the
    // browser keeps it and revalidates, getting a 304 until a deploy changes it.
    world: () => request("GET", "/globle/world.json", undefined, { cache: "default" }),

    daily: () => request("GET", `${BASE}/daily`),
    // date is the puzzle the page is showing. The server refuses the request with
    // 409 "new-day" if midnight has passed since, rather than apply it to the new
    // day's puzzle.
    guessDaily: (code, date) => request("POST", `${BASE}/daily/guess`, { code, date }),
    giveUpDaily: (date) => request("POST", `${BASE}/daily/give-up`, { date }),

    /** The in-progress game of this kind, or null when there is none (204). */
    current: (kind) => request("GET", `${BASE}/unlimited/current?kind=${encodeURIComponent(kind)}`),
    newGame: (kind, region) => request("POST", `${BASE}/unlimited/new`, { kind, region }),
    guessUnlimited: (id, code) => request("POST", `${BASE}/unlimited/${encodeURIComponent(id)}/guess`, { code }),
    giveUpUnlimited: (id) => request("POST", `${BASE}/unlimited/${encodeURIComponent(id)}/give-up`),

    leaderboard: () => request("GET", `${BASE}/leaderboard`),
};

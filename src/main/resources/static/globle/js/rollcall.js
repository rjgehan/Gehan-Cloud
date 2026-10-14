// "Name them all" (internally, roll call): the rules the page applies while a run is
// typed, as plain functions.
//
// Pure: no DOM, no fetch, no words. rollcall-view.js draws the run and talks to the
// server; this decides what a piece of typed text means, how the run on screen
// combines the server's state with names still on their way to it, how lists are
// grouped, and how the stopwatch counts. The node harness exercises all of it over
// the real country list, in both languages.
//
// The server is the source of truth for a run (RollCallState): what has been named,
// how many, how long. The page only ever adds names optimistically, ahead of the
// server's answer, and takes them back if the server refuses one.

import { normalize, findExact, exactEntries, longerNames, beginsName, buildIndex, closeNames } from "./match.js";

/** The continents a world run is broken down by, in the order the server sends them. */
export const CONTINENTS = ["africa", "americas", "asia", "europe", "oceania"];

/** The regions a run can be played in from the page. ("mainland" exists in the API only.) */
export const ROLLCALL_REGIONS = ["world", ...CONTINENTS];

/** What can be filled in when a run starts. */
export const AUTOFILLS = ["off", "easy", "known", "mine"];

/** An exact name that may still be the start of another country's waits this long for more typing. */
export const WAIT_MS = 700;

/**
 * ...and this long when the text ends in a space or a hyphen: "Sudán ", "Corea ",
 * "Guinea-" are on their way to "Sudán del Sur", "Corea del Norte", "Guinea-Bissau",
 * and a phone typist may take a moment over the next word. It still ends, since a
 * keyboard suggestion tapped ("Niger") arrives with a space after it.
 */
export const SEPARATOR_WAIT_MS = 2000;

/** Typing that names something that does not count is explained after this pause (Enter explains at once). */
export const HINT_MS = 900;

/** After a short name is accepted, the rest of its long form is swallowed while typing goes on this briskly. */
export const GUARD_MS = 2000;

/** How far the stopwatch may disagree with the server before it is reset to the server's time. */
export const CLOCK_SLACK_MS = 1500;

/** "mine" needs this many counted runs in the region. */
export const MINE_AFTER = 2;

/** ...and reads at most this many of the latest ones (the server's rule; the card says how many). */
export const MINE_LOOKBACK = 3;

/**
 * The codes a run in this region asks for: the answer countries (never a territory)
 * in that continent, or all of them for the world. The same rule the server's pools
 * follow (Region.includes on answer countries), so its totals and these agree.
 */
export function poolOf(countries, region = "world") {
    const pool = new Set();
    for (const c of countries || []) {
        if (!c || !c.code || c.canBeAnswer === false) continue;
        if (region === "world" || c.region === region) pool.add(c.code);
    }
    return pool;
}

/**
 * Names in the country list that never count in Name them all, though every other
 * mode still takes them (countries.json keeps them as aliases, so "England" still
 * finds the United Kingdom in classic, Hard and practice). Keyed by the country
 * they belong to, and matched through match.js normalize like any name, in either
 * language. tools/globle/check-rollcall.mjs fails if one stops being an alias of
 * its country.
 *
 *   part       a part of a country, not the country: "England is part of the
 *              United Kingdom, so it doesn't count"
 *   former     a name the country gave up last century, or the state it replaced:
 *              "Persia is an old name for Iran"
 *   ambiguous  names more than one thing: "America" is a continent too (in Spanish
 *              above all), so it only ever offers "Did you mean the United States?"
 *
 * Every other alias of the 197 still counts. Audited and kept on purpose: Great
 * Britain and Britain, Holland and Holanda (each country's everyday name); short
 * forms of a country's own name (Bosnia, Antigua, Trinidad, Saint Kitts, Saint
 * Vincent, São Tomé, Burkina); names still in use (Burma, Swaziland, Macedonia,
 * East Timor, Ivory Coast, Cape Verde, Czech Republic, Türkiye, Kirguisia); the
 * countries' own names for themselves (Deutschland, Nippon, Hellas, Eire...);
 * Palestinian Territories; and Korea, for South Korea.
 */
export const NOT_COUNTING = {
    part: {
        GBR: ["England", "Scotland", "Wales", "Northern Ireland", "Inglaterra", "Escocia", "Gales", "Irlanda del Norte"],
        PSE: ["West Bank", "Gaza", "Cisjordania", "Franja de Gaza"],
    },
    former: {
        BEN: ["Dahomey"],
        BFA: ["Upper Volta", "Alto Volta"],
        BGD: ["East Pakistan"],
        BLR: ["Byelorussia", "White Russia"],
        BLZ: ["British Honduras"],
        COD: ["Zaire"],
        ETH: ["Abyssinia", "Abisinia"],
        GUY: ["British Guiana", "Guayana Británica"],
        IRN: ["Persia"],
        KGZ: ["Kirghizia"],
        KHM: ["Kampuchea"],
        LKA: ["Ceylon", "Ceilán"],
        SUR: ["Dutch Guiana", "Guayana Holandesa"],
        THA: ["Siam"],
        TUV: ["Ellice Islands", "Islas Ellice"],
        TWN: ["Formosa"],
        VUT: ["New Hebrides", "Nuevas Hébridas"],
        WSM: ["Western Samoa", "Samoa Occidental"],
        ZWE: ["Rhodesia", "Rodesia"],
    },
    ambiguous: {
        USA: ["America", "América"],
    },
};

const squeezed = (text) => normalize(text).replace(/ /g, "");

const readings = new WeakMap();

/** The words joining the two halves of a name: "Trinidad and Tobago", "Antigua y Barbuda". */
const JOINED = /\s+(?:and|&|y|e)\s+/i;
const LEADING_ARTICLE = /^(?:the|el|la|los|las)\s+/i;

/**
 * The search index as Name them all reads it (memoised per index):
 *
 *   counting  the index without the names in NOT_COUNTING, for exact matches
 *   blocked   entry -> "part" | "former" | "ambiguous", for those names
 *   names     code -> the compact forms of its own names (English and Spanish), so a
 *             matched term can be told apart from an alias
 *   forms     code -> the compact forms of every name and alias that counts, for
 *             telling a nationality ("Russian") from a misspelling (isNationality)
 *   halves    compact form -> { code, term }: the second half of a country's name
 *             ("Tobago", "Barbuda", "Herzegovina", "Nevis", "the Grenadines"), a part
 *             of that country, which is not one of its names and never counts
 */
export function rollCallIndex(index) {
    if (!index) return null;
    let read = readings.get(index);
    if (read) return read;
    const listed = new Map();
    for (const [kind, byCode] of Object.entries(NOT_COUNTING)) {
        for (const [code, list] of Object.entries(byCode)) {
            for (const name of list) listed.set(`${code}|${squeezed(name)}`, kind);
        }
    }
    const counting = [];
    const blocked = new Map();
    const forms = new Map();
    const halves = new Map();
    for (const entry of index.entries) {
        const kind = listed.get(`${entry.code}|${entry.c}`);
        if (kind) {
            blocked.set(entry, kind);
            continue;
        }
        counting.push(entry);
        if (!forms.has(entry.code)) forms.set(entry.code, new Set());
        forms.get(entry.code).add(entry.c);
        const country = index.byCode && index.byCode.get(entry.code);
        const parts = String(entry.term).split(JOINED);
        if (parts.length > 1 && country && country.canBeAnswer !== false) {
            const half = parts[parts.length - 1].replace(LEADING_ARTICLE, "");
            const key = squeezed(half);
            if (key.length >= 4 && !halves.has(key)) halves.set(key, { code: entry.code, term: half });
        }
    }
    const names = new Map();
    for (const [code, country] of index.byCode || []) {
        names.set(code, new Set([country.name, country.nameEs].filter(Boolean).map(squeezed)));
    }
    read = { counting: { ...index, entries: counting }, blocked, names, forms, halves };
    readings.set(index, read);
    return read;
}

/**
 * A name in NOT_COUNTING that the text is, exactly, or the second half of a
 * country's name ("Tobago"): { kind, code, term }, or null.
 */
function blockedName(read, index, text) {
    for (const entry of exactEntries(index, text)) {
        const kind = read.blocked.get(entry);
        if (kind) return { kind, code: entry.code, term: entry.term };
    }
    const half = read.halves.get(squeezed(text));
    return half ? { kind: "part", code: half.code, term: half.term } : null;
}

/**
 * What the text typed so far means for this run:
 *
 *   empty      nothing typed
 *   unknown    not exactly a name of any place (perhaps still being typed)
 *   accept     names a country still to be named, and nothing longer could be meant
 *   wait       names one, but is also the start of another unnamed country's name
 *              ("Niger" while Nigeria is unnamed): accepted after waitFor(text), or on Enter
 *   already    names a country already named in this run
 *   territory  names a place that is never one of the countries (Greenland, Antarctica)
 *   outside    names a country outside this run's region
 *   part       names a part of a country (England): see NOT_COUNTING; term is the name typed.
 *              Also the second half of a country's name ("Tobago", "Barbuda")
 *   former     a former name (Persia, Zaire); term likewise
 *   ambiguous  "America": only ever a suggestion, on Enter (see decideEnter)
 *
 * waits says whether the text is the start of a longer name of some country still
 * to be named (other than the one it names), whatever the kind: an explanation of
 * "already" or "outside" waits until the player has clearly stopped there. Longer
 * names here include those in NOT_COUNTING ("Irlanda" still waits a moment for
 * "Irlanda del Norte"), so the wait rule is the same as it always was.
 *
 *   index   match.js buildIndex(countries, "country", lang): both languages are terms
 *   pool    poolOf(...) for the run's region
 *   named   the codes named so far, including any still on their way to the server
 *   byCode  code -> country, for canBeAnswer
 */
export function decide(text, { index, pool, named, byCode }) {
    if (!index || !normalize(text)) return { kind: "empty", code: null, waits: false };
    const read = rollCallIndex(index);
    const code = findExact(read.counting, text);
    const open = (c) => c !== code && pool.has(c) && !named.has(c);
    const waits = longerNames(index, text, open).length > 0;
    if (!code) {
        const blocked = blockedName(read, index, text);
        if (blocked) return { ...blocked, waits };
        return { kind: "unknown", code: null, waits };
    }
    if (named.has(code)) return { kind: "already", code, waits };
    if (pool.has(code)) return { kind: waits ? "wait" : "accept", code, waits };
    const place = byCode && byCode.get(code);
    if (place && place.canBeAnswer === false) return { kind: "territory", code, waits };
    return { kind: "outside", code, waits };
}

// ------------------------------------------------------------------ the lenient spell check

/**
 * How lenient the spell check is. Calibrated against a corpus of 1,100+ realistic
 * misspellings of all 197 countries in English and Spanish and 350+ things that
 * are not countries (see docs/globle.md for the rates, and
 * tools/globle/check-rollcall.mjs for the cases that must hold).
 *
 *   minLength  text shorter than this (letters, spaces out) is never spell-checked:
 *              no suggestions for "xq" or "abc"
 *   reach      the most slips closeNames looks at
 *   margin     counting at once needs every other place (and a part's or old name
 *              of another place) at least this many slips further away; see spellCheck
 */
export const SPELLING = { minLength: 4, reach: 3, margin: 1 };

/**
 * Slips a name may have and still count at once, by its length (compact letters):
 * none under 5 letters (only a spelling that folds the same: "Irann", "Chadd";
 * "Kuba" is offered), one up to 11 ("kyrigistan", "naru"), two from 12 ("papa new
 * guinia"). An alias under 8 letters, being so often a word of its own, needs a
 * spelling that folds the same: "Brittany" is not Britain, nor Spanish "norte"
 * Norway's "Norge".
 */
export function countLimit(length, alias = false) {
    if (length < 5) return 0;
    if (alias && length < 8) return 0;
    return length < 12 ? 1 : 2;
}

/**
 * Slips a name may have and still be offered as "Did you mean …?": 1 under 6
 * letters, 2 under 12, then 3. length is the shorter of the name and the text, so a
 * short word is not stretched to a long name ("hola" is not Holland).
 */
export function offerLimit(length) {
    if (length < 4) return -1;
    if (length < 6) return 1;
    return length < 12 ? 2 : 3;
}

/**
 * The other way to be offered: the text holds every letter of the name but one, in
 * order, starts with the same two and is about as long ("naruto" for Nauru).
 */
function lettersInOrder(m) {
    return m.folded.length >= 5 && m.common >= m.folded.length - 1
        && m.typed.slice(0, 2) === m.folded.slice(0, 2) && Math.abs(m.typed.length - m.folded.length) <= 1;
}

/**
 * The word for a country's people is not its name: "Russian", "Yemeni", Spanish
 * "chino", "uruguayo" or "sueca" are one slip from Russia, Yemen, China, Uruguay
 * and Suecia, so they are only ever offered ("Did you mean Russia?"; Enter again
 * takes it), never counted at once. A nationality is one of the place's names with
 * one of these endings: [the name's ending, the nationality's]. The irregular ones
 * one slip from a name are listed whole.
 */
const NATIONALITY_ENDINGS = [
    ["a", "an"],    // Russia → Russian, Kenya → Kenyan, Italia → Italian, Sri Lanka → Sri Lankan
    ["a", "o"],     // China → chino, Suiza → suizo, Argentina → argentino, Dominicana → dominicano
    ["ny", "n"],    // Germany → German
    ["ary", "aro"], // Hungary → húngaro
    ["ary", "ara"], // ... húngara
    ["ras", "ran"], // Honduras → Honduran
    ["ce", "ces"],  // France → francés
    ["y", "ya"],    // Uruguay → uruguaya
    ["n", "na"],    // Azerbaiyán → azerbaiyana (not "d": "Finlanda" is a misspelling)
];
/** After a name that ends in a consonant: Yemen → Yemeni, Catar → catarí, Uruguay → uruguayo, Jordan → jordano. */
const NATIONALITY_AFTER_CONSONANT = ["i", "o"];
/**
 * Listed whole: the irregular ones, and the Spanish feminine ones that drop the "i"
 * of a name in "-ia" (as a rule, "Australa" or "Libera" is a misspelling).
 */
const NATIONALITIES = new Set([
    "swiss", "swede", "croata", "somali", "malaya", "argelina",
    "rumana", "lituana", "letona", "mongola", "sueca", "tanzana", "mauritana", "jordana", "alemana", "bulgara",
    "eslovena", "eslovaca", "checa", "turca", "birmana", "rusa", "moldava", "bielorrusa", "kirguisa", "filipina",
]);

/**
 * Ordinary words that happen to sit one slip from a country's name: "hungry" is a
 * word, not a misspelling of Hungary, so it must not count on its own. Like a
 * nationality, each is only ever offered ("Did you mean Hungary?"), never counted at
 * once. Found by running word lists through the spell check; add to it when another
 * turns up.
 */
const ORDINARY_WORDS = new Set([
    "hungry", "chilly", "chili", "island", "kenny", "georgie", "argentine", "tunes",
]);

/** Whether the text is the word for the people of the place with these names (compact forms). */
export function isNationality(text, forms) {
    const typed = squeezed(text);
    if (NATIONALITIES.has(typed) || ORDINARY_WORDS.has(typed)) return true;
    for (const name of forms || []) {
        if (name.length < 4) continue;
        for (const [from, to] of NATIONALITY_ENDINGS) {
            if (name.endsWith(from) && typed === name.slice(0, name.length - from.length) + to) return true;
        }
        if (/[^aeiou]$/.test(name) && NATIONALITY_AFTER_CONSONANT.some((to) => typed === name + to)) return true;
    }
    return false;
}

/**
 * "Comoro Islands", "islas Filipinas": the name without the word for islands around
 * it, or null when there is none. Only the plural, for a country that is a group of
 * islands: one island ("Great Britain island", "Trinidad island") may be only part
 * of a country.
 */
function withoutIslands(text) {
    const words = normalize(text).split(" ");
    if (words.length < 2) return null;
    if (["islands", "isles"].includes(words[words.length - 1])) words.pop();
    else if (words[0] === "islas") words.shift();
    else return null;
    while (words.length > 1 && ["of", "de", "del"].includes(words[0])) words.shift();
    return words.join(" ");
}

const capitalIndexes = new WeakMap();

/** Whether the text is exactly a capital city's name ("Tunis"): a city never counts at once. */
function namesCapital(text, byCode) {
    if (!byCode) return false;
    let index = capitalIndexes.get(byCode);
    if (!index) {
        const all = buildIndex([...byCode.values()], "capital", "en");
        const capital = new Set(["capital", "capitalAlias", "otherCapital", "otherCapitalAlias"]);
        index = { ...all, entries: all.entries.filter((e) => capital.has(e.via)) };
        capitalIndexes.set(byCode, index);
    }
    return exactEntries(index, text).length > 0;
}

/**
 * Where a place stands in this run: "open" (still to be named), "suggest" (open,
 * but only ever offered: "America"), or why it does not count: already,
 * territory, outside, part, former.
 */
function standing(code, kind, { pool, named, byCode }) {
    if (kind === "part" || kind === "former") return kind;
    if (named.has(code)) return "already";
    const place = byCode && byCode.get(code);
    if (place && place.canBeAnswer === false) return "territory";
    if (!pool.has(code)) return "outside";
    return kind === "ambiguous" ? "suggest" : "open";
}

const OFFERED = new Set(["open", "suggest"]);
/** Ties go to a place that can be named, then to the longer name, then by code. */
const byCloseness = (a, b) => a.distance - b.distance
    || (OFFERED.has(b.standing) ? 1 : 0) - (OFFERED.has(a.standing) ? 1 : 0)
    || b.length - a.length || (a.code < b.code ? -1 : a.code > b.code ? 1 : 0);

/**
 * The places a typed text is close to, one per place and kind of name (the United
 * Kingdom and "England" are two), closest first: [{ code, kind, term, alias,
 * distance, raw, length, offerOnly, offered, standing }] (distance and raw as in
 * match.js closeNames, raw being the fewest unfolded slips to any of its names).
 */
export function closePlaces(text, ctx) {
    const { index } = ctx;
    const read = rollCallIndex(index);
    const typedLength = squeezed(text).length;
    const groups = new Map();
    const firstWords = new Map();
    for (const m of closeNames(index, text, { max: SPELLING.reach })) {
        const kind = read.blocked.get(m.entry) || null;
        if (m.partial && !kind) firstWords.set(m.code, m);
        // A name that does not count is only worth mentioning when it is nearly what
        // was typed: "Englnd", not "Africa" for America.
        if (m.distance > (kind ? 1 : SPELLING.reach)) continue;
        const key = `${m.code}|${kind || ""}`;
        const own = read.names.get(m.code);
        const candidate = {
            code: m.code, kind, term: m.term, alias: !(own && own.has(m.entry.c)), distance: m.distance, raw: m.raw,
            length: m.length, offerOnly: false,
            offered: m.distance <= offerLimit(Math.min(m.length, typedLength)) || lettersInOrder(m),
        };
        const before = groups.get(key);
        if (!before || candidate.distance < before.distance
            || (candidate.distance === before.distance && (candidate.offered && !before.offered || before.alias && !candidate.alias))) {
            groups.set(key, before ? { ...candidate, raw: Math.min(candidate.raw, before.raw) } : candidate);
        } else {
            before.raw = Math.min(before.raw, candidate.raw);
        }
    }
    // "Marshall", "Central African", "Saudi": the first word(s) of one place's name,
    // typed whole. Offered as if one slip away, never counted at once; and only
    // when no other place starts that way ("Costa", "North" stay unknown).
    if (firstWords.size === 1) {
        const [[code, m]] = [...firstWords];
        const key = `${code}|`;
        const before = groups.get(key);
        if (!before || before.distance > 1 || !before.offered) {
            groups.set(key, {
                code, kind: null, term: m.term, alias: false, distance: Math.min(1, before ? before.distance : 1),
                raw: Math.min(1, before ? before.raw : 1), length: m.length, offerOnly: true, offered: true,
            });
        }
    }
    return [...groups.values()].map((g) => ({ ...g, standing: standing(g.code, g.kind, ctx) })).sort(byCloseness);
}

/**
 * What Enter (or the ↵ button) does with the text in the box. Exact names keep
 * decide()'s rules; anything else goes through the lenient spell check:
 *
 *   empty       nothing typed
 *   accept      code: an exact name of a country still to be named (also one that was waiting)
 *   confident   code: misspelt, but surely this country: it counts at once, like an exact name
 *               (also "Comoro Islands": a country's name with the word for islands around it)
 *   suggest     codes: one or two countries to offer ("Did you mean …?"), closest first; a
 *               nationality ("Russian") is only ever offered (isNationality)
 *   already | territory | outside | part | former
 *               code (term for part and former): why it does not count, exactly typed or
 *               not (fuzzy says which). When the closest place cannot count, that is said
 *               rather than offering a worse one, unless one that can is as close.
 *   unknown     nothing close enough
 *
 * text is the text checked, for "from “kyrigistan”".
 */
export function decideEnter(text, ctx) {
    const now = decide(text, ctx);
    switch (now.kind) {
        case "empty":
            return { kind: "empty", code: null, text };
        case "accept":
        case "wait":
            return { kind: "accept", code: now.code, text };
        case "unknown": {
            const checked = spellCheck(text, ctx);
            if (checked.kind === "confident") return checked;
            // "Comoro Islands", "Fiji Islands", "islas Filipinas": the name inside counts,
            // when it would by itself. (The word for islands would otherwise make it
            // look like the Cocos Islands; "Canary Islands" is still no country.)
            // Its explanation, too ("You already named Comoros"), and its offer when the
            // whole text found nothing.
            const inner = withoutIslands(text);
            const found = inner ? decideEnter(inner, ctx) : null;
            if (!found || found.kind === "empty" || found.kind === "unknown") return checked;
            if (found.kind === "accept" || found.kind === "confident") {
                return { kind: "confident", code: found.code, term: found.term || null, text };
            }
            if (found.kind === "suggest" && checked.kind !== "unknown") return checked;
            return { ...found, text };
        }
        case "ambiguous": {
            const where = standing(now.code, now.kind, ctx);
            return where === "suggest" ? { kind: "suggest", codes: [now.code], text }
                : { kind: where, code: now.code, term: null, fuzzy: false, text };
        }
        default:
            return { kind: now.kind, code: now.code, term: now.term || null, fuzzy: false, text };
    }
}

function spellCheck(text, ctx) {
    const unknown = { kind: "unknown", code: null, text };
    if (squeezed(text).length < SPELLING.minLength) return unknown;
    const read = rollCallIndex(ctx.index);
    const places = closePlaces(text, ctx);
    const best = places[0];
    if (!best) return unknown;
    // Clearly ahead of every other place: by the margin, and with no rival as close on
    // the plain, unfolded spelling and close enough on it to count by itself
    // ("dominicaa" folds to Dominica, but is one plain slip from Dominicana too:
    // both are offered). A country's own names that do not count ("Byelorussia"
    // beside "Bielorrusia") are no rival.
    const ahead = (p) => p === best || (p.code === best.code && !best.kind)
        || (p.distance >= best.distance + SPELLING.margin && !(p.raw <= best.raw && p.raw <= countLimit(p.length, p.alias)));
    const countable = best.standing === "open" && !best.offerOnly && best.distance <= countLimit(best.length, best.alias)
        && !namesCapital(text, ctx.byCode) && !isNationality(text, read.forms.get(best.code));
    const rivals = places.filter((p) => !ahead(p));
    if (countable && !rivals.length) return { kind: "confident", code: best.code, term: best.term, text };
    const near = places.filter((p) => p.offered);
    const top = near[0];
    if (!top) return unknown;
    const open = near.filter((p) => OFFERED.has(p.standing));
    if (open.length && open[0].distance <= top.distance) {
        const codes = [];
        for (const p of open) {
            if (p.distance > open[0].distance || codes.length === 2) break;
            if (!codes.includes(p.code)) codes.push(p.code);
        }
        // Held back only by a rival about as close: offer that one too.
        if (countable) {
            for (const p of rivals) {
                if (codes.length < 2 && p.offered && OFFERED.has(p.standing) && !codes.includes(p.code)) codes.push(p.code);
            }
        }
        return { kind: "suggest", codes, text };
    }
    return { kind: top.standing, code: top.code, term: top.kind ? top.term : null, fuzzy: true, text };
}

/** Whether a decision names a country that may be accepted: at once, or after the wait (or Enter). */
export function acceptable(decision) {
    return !!decision && (decision.kind === "accept" || decision.kind === "wait");
}

/** How long a "wait" decision on this text waits for more typing before it is accepted. */
export function waitFor(text) {
    return /[\s\-‐-―]$/u.test(String(text || "")) ? SEPARATOR_WAIT_MS : WAIT_MS;
}

/**
 * Whether typing that goes on after a name was accepted is just the rest of that
 * name: "Bosnia" was accepted, and " and Herzegovina" is still arriving. accepted
 * is the text that was accepted, more is everything typed since.
 */
export function continuesName(index, code, accepted, more) {
    if (!more || !String(more).trim()) return true;
    return beginsName(index, code, `${accepted}${more}`);
}

/** The codes a run has named. */
export function namedCodes(state) {
    return new Set((state && state.named ? state.named : []).map((n) => n.code));
}

/**
 * The run as the page shows it: the server's state plus the names typed since and
 * not yet confirmed (pending, oldest first), each marked pending, counted in
 * namedCount and in its continent's progress. Never mutates its input.
 */
export function withPending(state, pending, byCode) {
    if (!state || !pending || !pending.length) return state;
    const have = namedCodes(state);
    const extra = [];
    for (const code of pending) {
        if (have.has(code)) continue;
        have.add(code);
        extra.push(code);
    }
    if (!extra.length) return state;
    const byRegion = (state.byRegion || []).map((r) => ({ ...r }));
    for (const code of extra) {
        const country = byCode && byCode.get(code);
        const row = country ? byRegion.find((r) => r.region === country.region) : null;
        if (row) row.named += 1;
    }
    return {
        ...state,
        named: [...(state.named || []), ...extra.map((code) => ({ code, auto: false, pending: true }))],
        namedCount: (state.namedCount || 0) + extra.length,
        byRegion,
    };
}

/** How many of a run's names were typed rather than filled in. */
export function typedCount(state) {
    if (!state) return 0;
    return Math.max(0, (state.namedCount || 0) - (state.autoCount || 0));
}

/** A run counts (best, the board, "mine") only when at least one country was typed. */
export function counts(state) {
    return typedCount(state) >= 1;
}

/**
 * Places grouped by continent for a list: [{ region, items: [{ code, iso2, name,
 * auto, pending }] }], continents in CONTINENTS order and only those with items,
 * each sorted by name with compare (i18n.js compareText, the page's collation).
 *
 *   entries  [{ code, auto?, pending? }] or plain codes
 *   name     country -> its name in the page's language
 *   compare  (a, b) -> number, for names
 */
export function groupByContinent(entries, byCode, { name, compare }) {
    const groups = new Map(CONTINENTS.map((region) => [region, []]));
    const other = [];
    for (const entry of entries || []) {
        const item = typeof entry === "string" ? { code: entry } : entry;
        const country = byCode && byCode.get(item.code);
        const row = {
            code: item.code,
            iso2: country ? country.iso2 || null : null,
            name: country ? name(country) : item.code,
            auto: !!item.auto,
            pending: !!item.pending,
        };
        const list = country ? groups.get(country.region) : null;
        (list || other).push(row);
    }
    const out = [];
    for (const [region, items] of groups) {
        if (items.length) out.push({ region, items: items.sort((a, b) => compare(a.name, b.name)) });
    }
    if (other.length) out.push({ region: null, items: other.sort((a, b) => compare(a.name, b.name)) });
    return out;
}

/**
 * The setup card's autofill choices from /rollcall/options:
 * [{ value, count, available, reason, remaining }].
 *
 *   reason  null; "loading" (no options yet); "locked" ("mine" before MINE_AFTER
 *           counted runs; remaining says how many more); "full" (it would fill in
 *           every country, leaving nothing to name: the server refuses it)
 *
 * failed: the options could not be loaded. "off", "easy" and "known" can still be
 * started (the server checks), "mine" cannot be known to be open.
 */
export function autofillChoices(options, { failed = false } = {}) {
    return AUTOFILLS.map((value) => {
        if (value === "off") return { value, count: null, available: true, reason: null, remaining: 0 };
        if (!options) {
            const open = failed && value !== "mine";
            return { value, count: null, available: open, reason: open ? null : "loading", remaining: 0 };
        }
        const count = options[value];
        if (count == null) {
            const remaining = Math.max(1, MINE_AFTER - (Number(options.finishedRuns) || 0));
            return { value, count: null, available: false, reason: "locked", remaining };
        }
        if (Number.isFinite(options.total) && count >= options.total) {
            return { value, count, available: false, reason: "full", remaining: 0 };
        }
        return { value, count, available: true, reason: null, remaining: 0 };
    });
}

/** The choice to select: the one wanted if it is open, else "off". */
export function pickAutofill(choices, wanted) {
    const choice = (choices || []).find((c) => c.value === wanted);
    return choice && choice.available ? wanted : "off";
}

/**
 * Where the stopwatch counts from, on the page's monotonic clock (performance.now):
 * the server's elapsedSeconds at the moment the response arrived, taken to be half
 * way through that second. A later response for the same run moves it only when it
 * disagrees by more than CLOCK_SLACK_MS (a phone that slept), so the display never
 * jitters by a second each time a name is saved.
 *
 *   previous  the start already in use for this run, or null
 */
export function clockStart(previous, elapsedSeconds, now) {
    const candidate = now - Math.max(0, Number(elapsedSeconds) || 0) * 1000 - 500;
    if (previous != null && Number.isFinite(previous) && Math.abs(previous - candidate) <= CLOCK_SLACK_MS) {
        return previous;
    }
    return candidate;
}

/** Whole seconds on the stopwatch at now. */
export function clockSeconds(start, now) {
    return Math.max(0, Math.floor((now - start) / 1000));
}

/** Milliseconds until the stopwatch next shows a new second (a little after, never 0). */
export function msToNextSecond(start, now) {
    const into = (((now - start) % 1000) + 1000) % 1000;
    return 1000 - into + 15;
}

/**
 * Whether a finished run beats a best ({ named, seconds }, from /rollcall/options
 * before it was played): more named, or as many in less time. A run that does not
 * count never does.
 */
export function beats(state, best) {
    if (!state || !counts(state)) return false;
    if (!best) return true;
    const named = state.namedCount || 0;
    const seconds = Number(state.elapsedSeconds) || 0;
    return named > best.named || (named === best.named && seconds < best.seconds);
}

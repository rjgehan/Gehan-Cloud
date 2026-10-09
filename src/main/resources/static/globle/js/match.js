// Finding the country (or capital) a player means from what they typed.
//
// Pure: builds a search index from the /globle/api/countries list and ranks
// matches. The autocomplete widget in autocomplete.js is just a view over this.
//
// People type "cote divoire", "Côte d'Ivoire", "ivory coast" and "st lucia" and
// expect all of them to work, so matching is on a normalised form: accents
// stripped, case folded, punctuation dropped, a few abbreviations expanded. Every
// term is also compared with its spaces squeezed out, which makes "cote d ivoire"
// and "cotedivoire" find the same place.

const COMBINING_MARKS = /[̀-ͯ]/g;

/** Whole-word abbreviations people use for the long form in country names. */
const TOKEN_SYNONYMS = { st: "saint", ste: "sainte", mt: "mount" };

/** The normalised, space-separated form of a name. */
export function normalize(text) {
    if (text == null) {
        return "";
    }
    const tokens = String(text)
        .normalize("NFD")
        .replace(COMBINING_MARKS, "")
        .toLowerCase()
        .replace(/[’'`´]/g, "")
        .replace(/&/g, " and ")
        .replace(/[^a-z0-9]+/g, " ")
        .trim()
        .split(" ")
        .filter(Boolean)
        .map((t) => TOKEN_SYNONYMS[t] || t);
    // "The Gambia", "the Netherlands": nobody should have to type the article.
    if (tokens.length > 1 && tokens[0] === "the") {
        tokens.shift();
    }
    return tokens.join(" ");
}

function compact(normalized) {
    return normalized.replace(/ /g, "");
}

const FILLER = new Set(["the", "of", "and"]);

/**
 * compact() without filler words, so "republic of congo" meets "republic of the
 * congo" and "trinidad tobago" meets "trinidad and tobago". A term that is nothing
 * but filler keeps its words, so it never shrinks to "" and matches everything.
 */
function bare(normalized) {
    const kept = normalized.split(" ").filter((w) => !FILLER.has(w));
    return kept.length ? kept.join("") : compact(normalized);
}

// Match tiers, best first.
const EXACT = 0;
const PREFIX = 1;
const WORD = 2;
const SUBSTRING = 3;

// Source of a term, best first, so a primary name beats an alias at the same tier
// and, in capitals mode, the capital beats the country it belongs to.
const SOURCE_RANK = { name: 0, alias: 1, capital: 0, capitalAlias: 1, country: 2, countryAlias: 3 };

/**
 * Builds the search index.
 *
 *   kind "country"  terms are country names and aliases
 *   kind "capital"  only countries that have a capital; terms are the capital's names
 *                   first, then the country's (so "France" still finds Paris)
 */
export function buildIndex(countries, kind = "country") {
    const entries = [];
    const byCode = new Map();
    const add = (country, term, via) => {
        const n = normalize(term);
        if (!n) return;
        entries.push({ code: country.code, term: String(term), n, c: compact(n), b: bare(n), via });
    };

    for (const country of countries || []) {
        if (!country || !country.code) continue;
        if (kind === "capital") {
            if (!country.capital || !country.capital.name) continue;
            add(country, country.capital.name, "capital");
            for (const alias of country.capital.aliases || []) add(country, alias, "capitalAlias");
            add(country, country.name, "country");
            for (const alias of country.aliases || []) add(country, alias, "countryAlias");
        } else {
            add(country, country.name, "name");
            for (const alias of country.aliases || []) add(country, alias, "alias");
        }
        byCode.set(country.code, country);
    }
    return { kind, entries, byCode };
}

/** How a normalised query matches one entry, or -1. qc and qb are its compact() and bare() forms. */
function tierOf(entry, qn, qc, qb) {
    if (entry.c === qc || entry.b === qb) return EXACT;
    if (entry.c.startsWith(qc) || entry.b.startsWith(qb)) return PREFIX;
    if ((" " + entry.n).includes(" " + qn)) return WORD;
    // Mid-word matches on one or two letters are noise ("an" is in half the world).
    if (qc.length >= 3 && entry.c.includes(qc)) return SUBSTRING;
    return -1;
}

/** The text a result shows: "France", or "Paris — France" in capitals mode. */
export function labelFor(index, code) {
    const country = index.byCode.get(code);
    if (!country) return code;
    return index.kind === "capital" && country.capital
        ? `${country.capital.name} — ${country.name}`
        : country.name;
}

/**
 * Ranked matches, one per place.
 *
 * Each result: { code, label, iso2, hint, guessed, exact, neverAnswer }. hint is the
 * alias that matched when it is not the name shown ("Burma" for Myanmar), so the
 * player can see why something unexpected came up. neverAnswer marks a territory
 * (Greenland, Puerto Rico...): guessable for its distance, but never the mystery.
 */
export function search(index, query, { limit = 8, guessed = new Set() } = {}) {
    const qn = normalize(query);
    if (!qn || !index) return [];
    const qc = compact(qn);
    const qb = bare(qn);

    const best = new Map();
    for (const entry of index.entries) {
        const tier = tierOf(entry, qn, qc, qb);
        if (tier < 0) continue;
        const score = tier * 10 + SOURCE_RANK[entry.via];
        const current = best.get(entry.code);
        if (!current || score < current.score) {
            best.set(entry.code, { entry, score, tier });
        }
    }

    const results = [];
    for (const [code, { entry, score, tier }] of best) {
        const country = index.byCode.get(code);
        const label = labelFor(index, code);
        const shown = entry.via === "name" || entry.via === "capital" || entry.via === "country";
        results.push({
            code,
            label,
            iso2: country.iso2 || null,
            hint: shown ? null : entry.term,
            guessed: guessed.has(code),
            exact: tier === EXACT,
            neverAnswer: country.canBeAnswer === false,
            score,
        });
    }
    results.sort((a, b) => a.score - b.score || a.label.localeCompare(b.label));
    return results.slice(0, limit);
}

/**
 * The single place the query names exactly, or null. Used for "type it and press
 * Enter". A term two places share is not exact: the player has to pick one from
 * the list. (The real data has no such term: "Congo" is deliberately the Republic
 * of the Congo's alone, as on globle-game.com; see tools/globle/README.md.)
 */
export function findExact(index, query) {
    const qn = normalize(query);
    if (!qn || !index) return null;
    const qc = compact(qn);
    const qb = bare(qn);
    let found = null;
    for (const entry of index.entries) {
        if (entry.c !== qc && entry.b !== qb) continue;
        if (found && found !== entry.code) return null;
        found = entry.code;
    }
    return found;
}

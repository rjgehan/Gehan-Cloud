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
//
// Two languages. Every place has an English and a Spanish name (nameEs), with
// aliases in each (aliases, aliasesEs), and both languages are always accepted,
// whichever one the page shows: a Spanish speaker may type "Germany" and an
// English speaker "Alemania". The display language only decides which name is
// the label and which counts as an alias (shown as a hint when it is what matched).

const COMBINING_MARKS = /[̀-ͯ]/g;

/** Whole-word abbreviations people use for the long form in place names: "St Lucia". */
const TOKEN_SYNONYMS = { st: "saint", ste: "sainte", mt: "mount" };

/**
 * The Spanish ones, "Sta. Lucía" and "Sto. Tomé", are read only before another
 * word. As the last word they are far more often a longer word still being typed
 * ("United Sta…", "Sto…" for Stockholm), which must keep finding what it always did.
 */
const INNER_SYNONYMS = { sta: "santa", sto: "santo" };

/**
 * A leading article nobody should have to type: "The Gambia", "the Netherlands",
 * and in Spanish "los Países Bajos", "el Perú", "las Bahamas", "la India".
 */
const ENGLISH_ARTICLE = "the";
const SPANISH_ARTICLES = new Set(["el", "la", "los", "las"]);

/**
 * The normalised words of a name, before any leading article is dropped.
 * lastAsTyped leaves the last word unexpanded: for text still being typed, where
 * "St" may be the start of "State" rather than "Saint".
 */
function tokensOf(text, lastAsTyped = false) {
    if (text == null) {
        return [];
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
        .filter(Boolean);
    const last = tokens.length - 1;
    return tokens.map((t, i) => (lastAsTyped && i === last ? t : TOKEN_SYNONYMS[t] || (i < last && INNER_SYNONYMS[t]) || t));
}

/** True for "el salvador", "la paz", "los paises bajos": a Spanish article, then more. */
function spanishArticleFirst(tokens) {
    return tokens.length > 1 && SPANISH_ARTICLES.has(tokens[0]);
}

/**
 * The normalised, space-separated form of a name, without a leading article
 * ("the" or a Spanish one) when more follows. ("ñ" folds to "n" like any accent.)
 */
export function normalize(text) {
    return withoutArticle(tokensOf(text));
}

function withoutArticle(tokens) {
    if (tokens.length > 1 && (tokens[0] === ENGLISH_ARTICLE || SPANISH_ARTICLES.has(tokens[0]))) {
        tokens.shift();
    }
    return tokens.join(" ");
}

function compact(normalized) {
    return normalized.replace(/ /g, "");
}

const FILLER = new Set(["the", "of", "and"]);

/**
 * Spanish filler counts as absent only inside a name: never as its first word (a
 * name's own article, as in "El Salvador", is handled apart; see buildIndex), and
 * never as the last word of what is typed, which is usually the start of a longer
 * word ("Saint-De…" for Saint-Denis, "New Del…" for New Delhi).
 */
const SPANISH_FILLER = new Set(["de", "del", "y", "el", "la", "los", "las"]);

/**
 * compact() without filler words, so "republic of congo" meets "republic of the
 * congo", "trinidad tobago" meets "trinidad and tobago", and "republica congo"
 * meets "República del Congo". A term that is nothing but filler keeps its words,
 * so it never shrinks to "" and matches everything. check-data.mjs proves that no
 * name or alias of any place, in either language, becomes ambiguous by it.
 */
function bare(normalized) {
    const words = normalized.split(" ");
    const last = words.length - 1;
    const kept = words.filter((w, i) => !FILLER.has(w) && !(i > 0 && i < last && SPANISH_FILLER.has(w)));
    return kept.length ? kept.join("") : compact(normalized);
}

// Match tiers, best first.
const EXACT = 0;
const PREFIX = 1;
const WORD = 2;
const SUBSTRING = 3;

// Source of a term, best first, so a primary name beats an alias at the same tier
// and, in capitals mode, the capital beats the country it belongs to. Names in the
// other language come after every name in the display language: on an English page
// "Arg" in capitals mode lists Buenos Aires — Argentina before Algiers, whose
// Spanish name is Argel, and on a Spanish page the reverse holds. They still match,
// and still show as a hint, so typing the other language always works.
const SOURCE_RANK = {
    name: 0, alias: 1, otherName: 2, otherAlias: 3,
    capital: 0, capitalAlias: 1, country: 2, countryAlias: 3,
    otherCapital: 4, otherCapitalAlias: 5, otherCountry: 6, otherCountryAlias: 7,
};

/** "es", "es-MX" and the like are Spanish; anything else is English. */
function isSpanish(lang) {
    return String(lang || "").toLowerCase().split("-")[0] === "es";
}

/**
 * A place's name in the display language: nameEs for Spanish, falling back to
 * the English name for a list from before Spanish names.
 */
export function displayName(country, lang = "en") {
    if (!country) {
        return "";
    }
    return (isSpanish(lang) && country.nameEs) || country.name || "";
}

/** Its capital's name in the display language (same fallback), or null where there is none. */
export function capitalName(country, lang = "en") {
    const capital = country && country.capital;
    if (!capital) {
        return null;
    }
    return (isSpanish(lang) && capital.nameEs) || capital.name || null;
}

/**
 * A place's (or capital's) names, display language first: [[name, ...aliases] in
 * the display language, [name, ...aliases] in the other one].
 */
function namesOf(item, spanish) {
    const en = [item.name, ...(item.aliases || [])];
    const es = [item.nameEs || item.name, ...(item.aliasesEs || [])];
    return spanish ? [es, en] : [en, es];
}

/**
 * Builds the search index.
 *
 *   kind "country"  terms are country names and aliases
 *   kind "capital"  only countries that have a capital; terms are the capital's names
 *                   first, then the country's (so "France" still finds Paris)
 *   lang            "en" (default) or "es": which language's names are the labels
 *                   and rank as names. Both languages' names are terms either way.
 */
export function buildIndex(countries, kind = "country", lang = "en") {
    const spanish = isSpanish(lang);
    const entries = [];
    const byCode = new Map();
    for (const country of countries || []) {
        if (!country || !country.code) continue;
        // A place often has a term twice (Chile is Chile in both languages); the
        // first is the best ranked, and enough.
        const seen = new Set();
        const push = (term, n, via, exactOnly) => {
            const key = (exactOnly ? "=" : "") + n;
            if (!n || seen.has(key)) return;
            seen.add(key);
            const c = compact(n);
            const b = bare(n);
            // f and fb: the folded forms closeNames() compares (see fold()).
            entries.push({ code: country.code, term: String(term), n, c, b, via, exactOnly, f: fold(c), fb: fold(b) });
        };
        const add = (term, via) => {
            const tokens = tokensOf(term);
            if (spanishArticleFirst(tokens)) {
                // A name's own Spanish article ("El Salvador", "La Paz") stays, so it
                // ranks and completes as it always has: "el sal…", "lapaz". Without the
                // article it only has to be found whole, for "Salvador", or for "El
                // Salvador" once normalize() has dropped the article; a prefix match on
                // "s…" would put El Salvador among every place that starts with S.
                push(term, tokens.join(" "), via, false);
                push(term, tokens.slice(1).join(" "), via, true);
            } else {
                push(term, normalize(term), via, false);
            }
        };
        const addAll = ([first, ...rest], firstVia, restVia) => {
            add(first, firstVia);
            for (const term of rest) add(term, restVia);
        };

        if (kind === "capital") {
            if (!country.capital || !country.capital.name) continue;
            const [shownCapital, otherCapital] = namesOf(country.capital, spanish);
            addAll(shownCapital, "capital", "capitalAlias");
            const [shown, other] = namesOf(country, spanish);
            addAll(shown, "country", "countryAlias");
            addAll(otherCapital, "otherCapital", "otherCapitalAlias");
            addAll(other, "otherCountry", "otherCountryAlias");
        } else {
            const [shown, other] = namesOf(country, spanish);
            addAll(shown, "name", "alias");
            addAll(other, "otherName", "otherAlias");
        }
        byCode.set(country.code, country);
    }
    return { kind, lang: spanish ? "es" : "en", entries, byCode };
}

/** A query's forms as tierOf compares them. */
function queryOf(normalized) {
    return { n: normalized, c: compact(normalized), b: bare(normalized) };
}

/** How a query matches one entry, or -1. */
function tierOf(entry, q) {
    if (entry.c === q.c || entry.b === q.b) return EXACT;
    if (entry.exactOnly) return -1;
    if (entry.c.startsWith(q.c) || entry.b.startsWith(q.b)) return PREFIX;
    if ((" " + entry.n).includes(" " + q.n)) return WORD;
    // Mid-word matches on one or two letters are noise ("an" is in half the world).
    if (q.c.length >= 3 && entry.c.includes(q.c)) return SUBSTRING;
    return -1;
}

/** The best match per place: code -> { entry, score, tier }, merged into best. */
function bestMatches(index, q, best = new Map()) {
    for (const entry of index.entries) {
        const tier = tierOf(entry, q);
        if (tier < 0) continue;
        const score = tier * 10 + SOURCE_RANK[entry.via];
        const current = best.get(entry.code);
        if (!current || score < current.score) {
            best.set(entry.code, { entry, score, tier });
        }
    }
    return best;
}

/**
 * The text a result shows, in the index's language: "France" or "Francia", and in
 * capitals mode "Paris — France" or "París — Francia".
 */
export function labelFor(index, code) {
    const country = index.byCode.get(code);
    if (!country) return code;
    return index.kind === "capital" && country.capital
        ? `${capitalName(country, index.lang)} — ${displayName(country, index.lang)}`
        : displayName(country, index.lang);
}

/**
 * Ranked matches, one per place.
 *
 * Each result: { code, label, iso2, hint, guessed, exact, neverAnswer }. hint is the
 * alias that matched when it is not the name shown ("Burma" for Myanmar, or
 * "Germany" for Alemania on a page in Spanish), so the player can see why
 * something unexpected came up. neverAnswer marks a territory (Greenland, Puerto
 * Rico...): guessable for its distance, but never the mystery.
 */
export function search(index, query, { limit = 8, guessed = new Set() } = {}) {
    const qn = normalize(query);
    if (!qn || !index) return [];

    // A query that starts with a Spanish article is read whole first: "el sal…" is
    // El Salvador typed from the start, "la hab…" La Habana. Only when that names
    // nothing from its start is the article taken as one nobody needs ("la arg…"
    // for la Argentina, "los est…" for los Estados Unidos).
    let best;
    const tokens = tokensOf(query);
    if (spanishArticleFirst(tokens)) {
        best = bestMatches(index, queryOf(tokens.join(" ")));
        if (![...best.values()].some((m) => m.tier <= PREFIX)) {
            best = bestMatches(index, queryOf(qn), best);
        }
    } else {
        best = bestMatches(index, queryOf(qn));
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
    const q = queryOf(qn);
    let found = null;
    for (const entry of index.entries) {
        if (entry.c !== q.c && entry.b !== q.b) continue;
        if (found && found !== entry.code) return null;
        found = entry.code;
    }
    return found;
}

/**
 * Every name or alias the query names exactly, compared as findExact compares:
 * [{ code, term, via, ... }], in index order. Name them all uses it to tell which
 * of a place's names was typed ("England", not "United Kingdom").
 */
export function exactEntries(index, query) {
    const qn = normalize(query);
    if (!qn || !index) return [];
    const q = queryOf(qn);
    return index.entries.filter((entry) => entry.c === q.c || entry.b === q.b);
}

/** Whether a normalised form starts with another and goes on past it. */
function goesOnFrom(form, start) {
    return form.length > start.length && form.startsWith(start);
}

/**
 * Text still being typed, in the forms a longer name could start with: as findExact
 * reads it, and, when its last word has a long form, with that word as typed, since
 * it may be the start of another word ("Vatican City St" is on its way to "State",
 * not to "Saint").
 */
function typingForms(query) {
    const qn = normalize(query);
    if (!qn) return [];
    const forms = [queryOf(qn)];
    const asTyped = withoutArticle(tokensOf(query, true));
    if (asTyped && asTyped !== qn) forms.push(queryOf(asTyped));
    return forms;
}

/**
 * The places with a name or alias that the query is the beginning of, and that
 * goes on past it: "Niger" is on the way to "Nigeria", "Guinea" to "Guinea-Bissau"
 * and "Guinea Ecuatorial", "UK" to "Ukraine". Compared the way findExact compares,
 * on the normalised forms with and without filler words, in both languages, and
 * including the article-less forms ("Salvador") since those can be typed whole.
 * Name them all holds an exact name back while it might still be the start of
 * another country's (see rollcall.js).
 *
 *   only(code)  which places count; every place by default
 *
 * Returns their codes, each once, in index order.
 */
export function longerNames(index, query, only = null) {
    const forms = index ? typingForms(query) : [];
    if (!forms.length) return [];
    const codes = new Set();
    for (const entry of index.entries) {
        if (codes.has(entry.code) || (only && !only(entry.code))) continue;
        if (forms.some((q) => goesOnFrom(entry.c, q.c) || goesOnFrom(entry.b, q.b))) codes.add(entry.code);
    }
    return [...codes];
}

/**
 * Whether the query is the beginning (or the whole) of one of this place's names
 * or aliases: "Bosnia and Herz" of Bosnia and Herzegovina. Name them all uses it to
 * recognise the rest of a long name still being typed after its short form was
 * already accepted.
 */
export function beginsName(index, code, query) {
    const forms = index ? typingForms(query) : [];
    if (!forms.length) return false;
    return index.entries.some((entry) => entry.code === code
        && forms.some((q) => entry.c.startsWith(q.c) || entry.b.startsWith(q.b)));
}

// ------------------------------------------------------------------ the lenient spell check
//
// Name them all forgives spelling: "kyrigistan" is Kyrgyzstan, "naru" Nauru and
// "filipnas" Filipinas. These are the measuring tools, pure like the rest of this
// file; how close is close enough to count, or to ask "Did you mean …?", is decided
// in rollcall.js, calibrated against a corpus of real misspellings of all 197
// countries in both languages (see docs/globle.md). Nothing else uses them: the
// other modes still need a name or alias exactly.

/**
 * A light phonetic fold, so that spellings which sound alike compare equal: ph → f;
 * h dropped, except in "ch" and "sh"; qu and q → k; c → s before e, i or y, else k;
 * z → s; y → i; v → b (one sound in Spanish); w → u; then doubled letters collapsed.
 * "Kyrgyzstan" and "kirgistan" both fold to "kirgistan", "Philippines" and
 * "filipines" to "filipines", "Volivia" and "Bolivia" to "bolibia". form is a
 * compact normalised name (a–z and digits, as normalize() leaves it, spaces out).
 */
export function fold(form) {
    return String(form || "")
        .replace(/ph/g, "f")
        .replace(/([^cs])h/g, "$1")
        .replace(/^h/, "")
        .replace(/qu/g, "k")
        .replace(/q/g, "k")
        .replace(/c(?=[eiy])/g, "s")
        .replace(/c/g, "k")
        .replace(/z/g, "s")
        .replace(/y/g, "i")
        .replace(/v/g, "b")
        .replace(/w/g, "u")
        .replace(/(.)\1+/g, "$1");
}

/**
 * The optimal string alignment distance: the fewest insertions, deletions,
 * substitutions and swaps of two neighbouring letters that turn a into b
 * (Damerau–Levenshtein where no stretch is edited twice). "isreal" is 1 from
 * "israel". Anything over max comes back as max + 1, and is given up on early.
 */
export function editDistance(a, b, max = Infinity) {
    const s = String(a == null ? "" : a);
    const t = String(b == null ? "" : b);
    const n = s.length;
    const m = t.length;
    if (Math.abs(n - m) > max) return max + 1;
    if (!n || !m) return Math.max(n, m);
    let before = null;
    let prev = Array.from({ length: m + 1 }, (_, j) => j);
    for (let i = 1; i <= n; i++) {
        const row = new Array(m + 1);
        row[0] = i;
        let low = i;
        for (let j = 1; j <= m; j++) {
            let v = Math.min(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + (s[i - 1] === t[j - 1] ? 0 : 1));
            if (i > 1 && j > 1 && s[i - 1] === t[j - 2] && s[i - 2] === t[j - 1]) v = Math.min(v, before[j - 2] + 1);
            row[j] = v;
            if (v < low) low = v;
        }
        // Every way through passes this row at or above its lowest value.
        if (low > max) return max + 1;
        before = prev;
        prev = row;
    }
    return prev[m] > max ? max + 1 : prev[m];
}

/** The length of the longest common subsequence: how many letters two strings share, in order. */
export function commonLength(a, b) {
    const s = String(a == null ? "" : a);
    const t = String(b == null ? "" : b);
    let prev = new Array(t.length + 1).fill(0);
    for (let i = 1; i <= s.length; i++) {
        const row = new Array(t.length + 1).fill(0);
        for (let j = 1; j <= t.length; j++) {
            row[j] = s[i - 1] === t[j - 1] ? prev[j - 1] + 1 : Math.max(prev[j], row[j - 1]);
        }
        prev = row;
    }
    return prev[t.length];
}

/**
 * An abbreviation ("UK", "DPRK", "EE. UU.") or a name under four letters: these
 * are only ever matched exactly. One letter off an abbreviation is another word.
 */
function exactOnlyTerm(entry) {
    return entry.c.length < 4 || entry.term === entry.term.toUpperCase();
}

/**
 * editDistance between two folded forms, with the first letter weighing more: a
 * slip there costs 2, and half a slip more when only the fold makes the first
 * letters agree (rawA and rawB are the unfolded forms): "Kuba" is half a slip from
 * Cuba, "Volivia" half a slip from Bolivia, and "Venice" one and a half from Belice.
 */
function slips(a, b, rawA, rawB, max) {
    if (!a || !b) return max + 1;
    const first = a[0] !== b[0] ? 1 : rawA[0] !== rawB[0] ? 0.5 : 0;
    return Math.min(max + 1, editDistance(a, b, Math.max(0, Math.floor(max - first))) + first);
}

/**
 * How close a typed text is to each place's names and aliases, in both languages,
 * for Name them all's lenient spell check.
 *
 * The text and each name are compared folded (fold()), whole and without filler
 * words ("republic congo"), and a slip in the first letter costs double: people
 * rarely get the first letter wrong, and "Prussia" is not Russia (see slips(), which
 * also makes some distances end in a half). Abbreviations and names under four
 * letters are left out; they are only matched exactly.
 *
 * Returns one result per name or alias that is within max slips, or that the text
 * is the first word or words of ("Marshall" for the Marshall Islands, "Central
 * African"), closest first:
 *
 *   { entry, code, term, via, distance, raw, length, common, folded, typed, partial }
 *
 *   distance  slips between the folded forms, or the unfolded ones if fewer (the first letter weighs more)
 *   raw       slips between the unfolded forms alone (max + 1 when over max)
 *   length    the name's own length, compact and unfolded: how many slips it can take
 *   common    letters the folded forms share in order (commonLength)
 *   folded    the folded name;  typed  the folded text
 *   partial   the text is the name's first word(s), typed whole
 */
export function closeNames(index, query, { max = 3 } = {}) {
    const qn = normalize(query);
    if (!qn || !index) return [];
    const q = queryOf(qn);
    const typed = fold(q.c);
    const typedBare = fold(q.b);
    const words = qn.split(" ");
    const out = [];
    for (const entry of index.entries) {
        if (exactOnlyTerm(entry)) continue;
        const folded = entry.f != null ? entry.f : fold(entry.c);
        const foldedBare = entry.fb != null ? entry.fb : fold(entry.b);
        // Unfolded too: a swap around a c ("jamacia") changes how it folds.
        const raw = slips(q.c, entry.c, q.c, entry.c, max);
        const distance = Math.min(slips(typed, folded, q.c, entry.c, max), slips(typedBare, foldedBare, q.b, entry.b, max), raw);
        const names = entry.n.split(" ");
        const partial = names.length > words.length && words.every((w, i) => names[i] === w);
        if (distance > max && !partial) continue;
        out.push({
            entry, code: entry.code, term: entry.term, via: entry.via, distance, raw, length: entry.c.length,
            common: commonLength(typed, folded), folded, typed, partial,
        });
    }
    out.sort((a, b) => a.distance - b.distance);
    return out;
}

// Place names in the page's language.
//
// The server's GameState names places in English, and stays that way: one
// language on the wire, and every stored game reads the same. The country list
// (/globle/api/countries) carries each place's Spanish name beside its English
// one (nameEs, and capital.nameEs), so the page localises by code. A list from
// before the Spanish names existed has no nameEs, and then the English name shows.
//
// Pure: no DOM. The page passes in its countries-by-code map. Which name is which
// language's is decided in one place, match.js (displayName, capitalName), the same
// rule the search labels follow; this only defaults the language to the page's.

import { getLang, has, t } from "./i18n.js";
import { displayName, capitalName as capitalIn } from "./match.js";

/** A country's name: "Germany", or "Alemania" in Spanish. */
export function placeName(country, lang = getLang()) {
    return displayName(country, lang);
}

/**
 * A country's name inside a sentence: with the article some names need there ("the
 * United States", "el Reino Unido"; i18n place.inSentence.<code>), else as placeName.
 * For sentences only ("bordering the Netherlands"): a label or a heading keeps placeName.
 */
export function nameInSentence(country, lang = getLang()) {
    if (!country) return "";
    const key = `place.inSentence.${country.code}`;
    return has(key) ? t(key, undefined, lang) : placeName(country, lang);
}

/** A country's capital: "London", or "Londres" in Spanish; "" if it has none. */
export function capitalName(country, lang = getLang()) {
    return capitalIn(country, lang) || "";
}

/**
 * One guess or answer from a GameState with its names in the language: name and
 * capital.name are swapped for the country list's, by code. Places the list does
 * not know keep the server's names. Never mutates its input.
 */
export function localisePlace(place, byCode, lang = getLang()) {
    if (!place || lang === "en") return place;
    const country = byCode && byCode.get(place.code);
    if (!country) return place;
    const local = { ...place, name: placeName(country, lang) || place.name };
    if (place.capital) {
        local.capital = { ...place.capital, name: capitalName(country, lang) || place.capital.name };
    }
    return local;
}

/**
 * A GameState with every place name in the language: each guess, and the answer
 * once the game is over. English is the server's own, so it comes back as is.
 */
export function localiseState(state, byCode, lang = getLang()) {
    if (!state || lang === "en") return state;
    return {
        ...state,
        guesses: (state.guesses || []).map((g) => localisePlace(g, byCode, lang)),
        answer: state.answer ? localisePlace(state.answer, byCode, lang) : state.answer,
    };
}

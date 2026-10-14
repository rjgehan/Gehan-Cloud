package cloud.gehan.globle.catalog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * The server's copy of how the page compares a typed name with a place's names: the
 * normalised form of {@code normalize()} in {@code static/globle/js/match.js}, and the
 * two ways its {@code findExact()} calls a name exact. Globle's server never matches
 * what a player types (the page sends codes); this exists so a "request a name change"
 * can be closed by itself once the data names the country that way (see
 * NameRequestService).
 *
 * <p>The rules, as in match.js:
 * <ul>
 * <li>accents off (NFD, combining marks dropped, so "ñ" is "n"), case folded,
 *     apostrophes dropped ("d'Ivoire" is "divoire"), "&amp;" read as "and", every other
 *     run of characters outside a-z and 0-9 a single space, the ends trimmed;</li>
 * <li>the whole words "st", "ste" and "mt" are "saint", "sainte" and "mount", and
 *     "sta" and "sto" are "santa" and "santo" before another word;</li>
 * <li>a leading article, "the" or Spanish "el", "la", "los" or "las", is dropped when
 *     more words follow ("the Gambia", "los Estados Unidos").</li>
 * </ul>
 * Two forms of a name are then compared: with its spaces squeezed out ("cote d
 * ivoire" is "cotedivoire"), and "bare", without the filler words "the", "of" and
 * "and", nor Spanish "de", "del", "y", "el", "la", "los" and "las" inside the name
 * ("trinidad tobago", "republica congo"). A name whose own first word is a Spanish
 * article ("El Salvador") is also known whole without it ("Salvador"), and still
 * with it run together ("elsalvador").
 *
 * <p>Characters that do not fold to a-z or 0-9 ("日本", "Ørsted"'s Ø) become spaces,
 * as on the page, so text in another script normalises to nothing and names nothing.
 *
 * <p>Pure and stateless. PlaceNamesTest pins it to match.js's outputs.
 */
public final class PlaceNames {

    private static final Pattern COMBINING_MARKS = Pattern.compile("[\\u0300-\\u036f]");
    private static final Pattern APOSTROPHES = Pattern.compile("[\\u2019'`\\u00b4]");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    /** Whole-word abbreviations, read anywhere: "St Lucia". */
    private static final Map<String, String> TOKEN_SYNONYMS = Map.of("st", "saint", "ste", "sainte", "mt", "mount");
    /** Read only before another word: "Sta. Lucía", "Sto. Tomé". */
    private static final Map<String, String> INNER_SYNONYMS = Map.of("sta", "santa", "sto", "santo");

    private static final String ENGLISH_ARTICLE = "the";
    private static final Set<String> SPANISH_ARTICLES = Set.of("el", "la", "los", "las");
    private static final Set<String> FILLER = Set.of("the", "of", "and");
    private static final Set<String> SPANISH_FILLER = Set.of("de", "del", "y", "el", "la", "los", "las");

    private PlaceNames() {
    }

    /**
     * The normalised, space-separated form of a name, without a leading article when
     * more follows: "Côte d'Ivoire" is "cote divoire", "los Estados Unidos" is "estados
     * unidos", "St. Lucia" is "saint lucia". Empty for null, blank, or text with no
     * letter or digit that folds to a-z or 0-9.
     */
    public static String normalize(String text) {
        return withoutArticle(tokensOf(text));
    }

    /** {@link #normalize} with the spaces squeezed out: "cotedivoire". */
    public static String compact(String text) {
        return squeeze(normalize(text));
    }

    /**
     * Whether {@code typed} names a place with these names exactly, as the page's
     * findExact() would call it for that place: its squeezed or its bare form is a
     * name's. Nothing normalises to nothing is ever a match.
     *
     * @param names the place's name and aliases, in any language
     */
    public static boolean names(String typed, Collection<String> names) {
        String query = normalize(typed);
        if (query.isEmpty()) {
            return false;
        }
        String squeezed = squeeze(query);
        String bare = bare(query);
        for (String name : names) {
            for (String form : formsOf(name)) {
                if (squeeze(form).equals(squeezed) || bare(form).equals(bare)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The code of the one place {@code typed} names exactly, as findExact() answers over
     * the page's whole country list: empty when it names no place, and when it names
     * more than one, since a name two places share is no name the page accepts. To ask
     * this of the same places many times, build their {@link #index} once.
     */
    public static Optional<String> namedPlace(String typed, Collection<Country> places) {
        return index(places).namedPlace(typed);
    }

    /** The places' names in the two forms findExact() compares, worked out once. */
    public static Index index(Collection<Country> places) {
        return new Index(places);
    }

    /**
     * Every place's names by their squeezed and their bare forms, as match.js
     * buildIndex() keeps them, so that finding the place a text names is two lookups.
     */
    public static final class Index {
        private final Map<String, Set<String>> bySqueezed = new HashMap<>();
        private final Map<String, Set<String>> byBare = new HashMap<>();

        private Index(Collection<Country> places) {
            for (Country place : places) {
                for (String name : allNames(place)) {
                    for (String form : formsOf(name)) {
                        bySqueezed.computeIfAbsent(squeeze(form), key -> new TreeSet<>()).add(place.code());
                        byBare.computeIfAbsent(bare(form), key -> new TreeSet<>()).add(place.code());
                    }
                }
            }
        }

        /** As {@link PlaceNames#namedPlace}: the one place named exactly, or empty. */
        public Optional<String> namedPlace(String typed) {
            String query = normalize(typed);
            if (query.isEmpty()) {
                return Optional.empty();
            }
            Set<String> codes = new TreeSet<>(bySqueezed.getOrDefault(squeeze(query), Set.of()));
            codes.addAll(byBare.getOrDefault(bare(query), Set.of()));
            return codes.size() == 1 ? Optional.of(codes.iterator().next()) : Optional.empty();
        }
    }

    /** Every name and alias of a place, English and Spanish. */
    public static List<String> allNames(Country country) {
        List<String> names = new ArrayList<>();
        names.add(country.name());
        names.add(country.nameEs());
        names.addAll(country.aliases());
        names.addAll(country.aliasesEs());
        return names;
    }

    // ------------------------------------------------------------------ internals

    /**
     * The normalised forms a name is known by. As in match.js's buildIndex: normally its
     * normalize() form alone; a name whose first word is a Spanish article keeps it
     * ("el salvador") and is known without it too ("salvador").
     */
    static Set<String> formsOf(String name) {
        List<String> tokens = tokensOf(name);
        Set<String> forms = new LinkedHashSet<>();
        if (tokens.size() > 1 && SPANISH_ARTICLES.contains(tokens.get(0))) {
            forms.add(String.join(" ", tokens));
            forms.add(String.join(" ", tokens.subList(1, tokens.size())));
        } else {
            forms.add(withoutArticle(tokens));
        }
        forms.remove("");
        return forms;
    }

    /** The normalised words, before any leading article is dropped. */
    static List<String> tokensOf(String text) {
        if (text == null) {
            return List.of();
        }
        String folded = COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        folded = folded.toLowerCase(Locale.ROOT);
        folded = APOSTROPHES.matcher(folded).replaceAll("");
        folded = folded.replace("&", " and ");
        folded = NOT_ALPHANUMERIC.matcher(folded).replaceAll(" ").trim();
        if (folded.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>(Arrays.asList(folded.split(" ")));
        int last = tokens.size() - 1;
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String expanded = TOKEN_SYNONYMS.get(token);
            if (expanded == null && i < last) {
                expanded = INNER_SYNONYMS.get(token);
            }
            if (expanded != null) {
                tokens.set(i, expanded);
            }
        }
        return tokens;
    }

    private static String withoutArticle(List<String> tokens) {
        if (tokens.size() > 1 && (tokens.get(0).equals(ENGLISH_ARTICLE) || SPANISH_ARTICLES.contains(tokens.get(0)))) {
            return String.join(" ", tokens.subList(1, tokens.size()));
        }
        return String.join(" ", tokens);
    }

    private static String squeeze(String normalized) {
        return normalized.replace(" ", "");
    }

    /**
     * The squeezed form without filler words; the Spanish ones only inside the name,
     * never its first or last word. A name of nothing but filler keeps its words.
     */
    static String bare(String normalized) {
        String[] words = normalized.split(" ");
        int last = words.length - 1;
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            boolean filler = FILLER.contains(word) || (i > 0 && i < last && SPANISH_FILLER.contains(word));
            if (!filler) {
                kept.append(word);
            }
        }
        return kept.isEmpty() ? squeeze(normalized) : kept.toString();
    }
}

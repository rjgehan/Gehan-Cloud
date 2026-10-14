package cloud.gehan.globle.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server's copy of the page's name matching (match.js normalize() and findExact()).
 * The expected values below are what match.js itself returns for the same text, taken
 * by running it under Node; at the time of writing the two also agreed on every name
 * and alias of every place, and on nine variants of each (upper case, squeezed, "the"
 * and "los" in front, "&amp;" for "and", "St." for "Saint", decomposed accents, a
 * trailing "s"), matched against every place: 3,946 texts, 248 places, no difference.
 */
class PlaceNamesTest {

    private static final CountryCatalog CATALOG = new CountryCatalog();

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "Côte d'Ivoire          | cote divoire",
            "CÔTE D'IVOIRE          | cote divoire",
            "Côte d’Ivoire          | cote divoire",
            "ivory coast            | ivory coast",
            "los Estados Unidos     | estados unidos",
            "St Lucia               | saint lucia",
            "St. Lucia              | saint lucia",
            "Sta. Lucía             | santa lucia",
            "Sto. Tomé y Príncipe   | santo tome y principe",
            "sta                    | sta",
            "United Sta             | united sta",
            "Ste Lucie              | sainte lucie",
            "Mt Athos               | mount athos",
            "St                     | saint",
            "Trinidad & Tobago      | trinidad and tobago",
            "The Gambia             | gambia",
            "the                    | the",
            "the the                | the",
            "El Salvador            | salvador",
            "el                     | el",
            "la paz                 | paz",
            "\"  Great   Britain  \" | great britain",
            "Guinea-Bissau          | guinea bissau",
            "Bosnia–Herzegovina     | bosnia herzegovina",
            "ÅLAND                  | aland",
            "Türkiye                | turkiye",
            "Curaçao                | curacao",
            "Peñón                  | penon",
            "R.D. Congo             | r d congo",
            "EE. UU.                | ee uu",
            "las Bahamas y          | bahamas y",
            "rock ’n’ roll          | rock n roll",
            "Straße                 | stra e",
            "Ørsted                 | rsted",
            "İstanbul               | istanbul",
            "x_y                    | x y",
            "a1b2                   | a1b2",
    })
    void normalizesAsTheMatcherDoes(String text, String expected) {
        assertThat(PlaceNames.normalize(text)).isEqualTo(expected);
    }

    @Test
    void textWithNothingToReadNormalisesToNothingAndNamesNothing() {
        for (String text : new String[]{null, "", "   ", "!!!", "日本", "Ελλάδα", "—"}) {
            assertThat(PlaceNames.normalize(text)).as("%s", text).isEmpty();
            assertThat(PlaceNames.compact(text)).as("%s", text).isEmpty();
            for (Country country : CATALOG.all()) {
                assertThat(PlaceNames.names(text, PlaceNames.allNames(country))).as("%s", text).isFalse();
            }
        }
    }

    @Test
    void compactSqueezesTheSpacesOut() {
        assertThat(PlaceNames.compact("Côte d'Ivoire")).isEqualTo("cotedivoire");
        assertThat(PlaceNames.compact("the Great-Britain")).isEqualTo("greatbritain");
    }

    @Test
    void aNameIsMatchedWholeInEitherFormNeverByAPart() {
        List<String> uk = PlaceNames.allNames(country("GBR"));
        assertThat(PlaceNames.names("great britain", uk)).isTrue();
        assertThat(PlaceNames.names("GREAT-BRITAIN", uk)).isTrue();
        assertThat(PlaceNames.names("GreatBritain", uk)).isTrue();
        assertThat(PlaceNames.names("the Great Britain", uk)).isTrue();
        assertThat(PlaceNames.names("Gran Bretaña", uk)).isTrue();
        assertThat(PlaceNames.names("Great Britain and NI", uk)).isFalse();
        assertThat(PlaceNames.names("Great", uk)).as("a prefix is not a name").isFalse();
        assertThat(PlaceNames.names("Ireland", uk)).isFalse();

        // Filler words count as absent, the Spanish ones only inside a name.
        List<String> trinidad = PlaceNames.allNames(country("TTO"));
        assertThat(PlaceNames.names("Trinidad Tobago", trinidad)).isTrue();
        assertThat(PlaceNames.names("Trinidad & Tobago", trinidad)).isTrue();
        List<String> congo = PlaceNames.allNames(country("COG"));
        assertThat(PlaceNames.names("Republica Congo", congo)).isTrue();
        assertThat(PlaceNames.names("Republic of Congo", congo)).isTrue();

        // A name's own Spanish article: whole, squeezed with it, or without it.
        List<String> salvador = PlaceNames.allNames(country("SLV"));
        assertThat(PlaceNames.names("El Salvador", salvador)).isTrue();
        assertThat(PlaceNames.names("elsalvador", salvador)).isTrue();
        assertThat(PlaceNames.names("Salvador", salvador)).isTrue();

        // Articles, abbreviations, accents and case, in both languages.
        assertThat(PlaceNames.names("los Estados Unidos", PlaceNames.allNames(country("USA")))).isTrue();
        assertThat(PlaceNames.names("EE UU", PlaceNames.allNames(country("USA")))).isTrue();
        assertThat(PlaceNames.names("Sta. Lucía", PlaceNames.allNames(country("LCA")))).isTrue();
        assertThat(PlaceNames.names("st lucia", PlaceNames.allNames(country("LCA")))).isTrue();
        assertThat(PlaceNames.names("COTE D IVOIRE", PlaceNames.allNames(country("CIV")))).isTrue();
        assertThat(PlaceNames.names("kirguistan", PlaceNames.allNames(country("KGZ")))).isTrue();
        assertThat(PlaceNames.names("kyrigistan", PlaceNames.allNames(country("KGZ"))))
                .as("exact names only: the page's spelling slack is not the data's").isFalse();
    }

    /** Every name and alias of every place, in both languages, names that place: the data and the rule agree. */
    @Test
    void everyNameInTheDataNamesItsOwnPlace() {
        List<String> failures = new ArrayList<>();
        for (Country country : CATALOG.all()) {
            List<String> names = PlaceNames.allNames(country);
            for (String name : names) {
                if (PlaceNames.normalize(name).isEmpty() || !PlaceNames.names(name, names)) {
                    failures.add(country.code() + ": " + name);
                }
            }
        }
        assertThat(failures).isEmpty();
    }

    /** Adding an alias is all it takes for a suggestion to become a name of the place: how a request closes itself. */
    @Test
    void aSuggestionNamesAPlaceOnceTheDataHasIt() {
        Country nauru = country("NRU");
        assertThat(PlaceNames.names("Naoero", PlaceNames.allNames(nauru))).isFalse();
        List<String> aliases = new ArrayList<>(nauru.aliases());
        aliases.add("Naoero");
        Country corrected = new Country(nauru.code(), nauru.name(), nauru.nameEs(), nauru.iso2(), aliases,
                nauru.aliasesEs(), nauru.answer(), nauru.region(), nauru.difficulty(), nauru.smallIsland(),
                nauru.lat(), nauru.lng(), nauru.capital());
        assertThat(PlaceNames.names("naoero", PlaceNames.allNames(corrected))).isTrue();
        assertThat(PlaceNames.names("  NAOERO! ", PlaceNames.allNames(corrected))).isTrue();
    }

    /**
     * The one place a text names, as match.js findExact() reads it over every place: a
     * name two places share names neither (the player would have to pick), and a text
     * that names nothing has no place.
     */
    @Test
    void theNamedPlaceIsTheOnlyOneATextNamesExactly() {
        assertThat(PlaceNames.namedPlace("the Gambia", CATALOG.all())).contains("GMB");
        assertThat(PlaceNames.namedPlace("Congo", CATALOG.all())).contains("COG");
        assertThat(PlaceNames.namedPlace("Groenlandia", CATALOG.all())).contains("GRL");
        assertThat(PlaceNames.namedPlace("Gambi", CATALOG.all())).isEmpty();
        assertThat(PlaceNames.namedPlace("日本", CATALOG.all())).isEmpty();

        // Were the data to give DR Congo "Congo" too, "Congo" would name neither.
        Country drc = country("COD");
        List<String> aliases = new ArrayList<>(drc.aliases());
        aliases.add("Congo");
        Country sharing = new Country(drc.code(), drc.name(), drc.nameEs(), drc.iso2(), aliases, drc.aliasesEs(),
                drc.answer(), drc.region(), drc.difficulty(), drc.smallIsland(), drc.lat(), drc.lng(), drc.capital());
        List<Country> places = new ArrayList<>(CATALOG.all());
        places.replaceAll(place -> place.code().equals("COD") ? sharing : place);
        assertThat(PlaceNames.namedPlace("Congo", places)).isEmpty();
        assertThat(PlaceNames.namedPlace("DRC", places)).contains("COD");
    }

    @Test
    void allNamesIsBothLanguagesNamesAndAliases() {
        Country germany = country("DEU");
        assertThat(PlaceNames.allNames(germany)).containsExactly("Germany", "Alemania", "Deutschland");
    }

    private static Country country(String code) {
        return CATALOG.find(code).orElseThrow();
    }
}

package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.catalog.PlaceNames;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** How a "request a name change" suggestion is cleaned and compared, without Spring. */
class NameRequestRulesTest {

    /**
     * The names Name them all refuses are each an answer country's, and each one, where
     * the data has it, names that country and no other: the list can only ever keep a
     * request for that country open, never another's.
     */
    @Test
    void theNamesNameThemAllRefusesBelongToTheirOwnCountry() {
        CountryCatalog catalog = new CountryCatalog();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.entrySet()) {
            String code = entry.getKey();
            if (catalog.find(code).filter(Country::answer).isEmpty()) {
                wrong.add(code + ": not an answer country");
            }
            for (String name : entry.getValue()) {
                if (PlaceNames.normalize(name).isEmpty()
                        || !PlaceNames.namedPlace(name, catalog.all()).map(code::equals).orElse(true)) {
                    wrong.add(code + ": " + name);
                }
            }
        }
        assertThat(wrong).isEmpty();
        // The spec's own examples are there, in both languages.
        assertThat(PlaceNames.names("England", NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.get("GBR"))).isTrue();
        assertThat(PlaceNames.names("Irlanda del Norte", NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.get("GBR")))
                .isTrue();
        assertThat(PlaceNames.names("AMERICA", NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.get("USA"))).isTrue();
        assertThat(PlaceNames.names("St. Kitts", NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.get("KNA"))).isTrue();
        assertThat(PlaceNames.names("Great Britain", NameRequestService.NOT_COUNTED_IN_NAME_THEM_ALL.get("GBR")))
                .isFalse();
    }

    @Test
    void cleaningCollapsesEveryGapAndTrims() {
        assertThat(NameRequestService.clean("  Great Britain\r\n\tand  NI  ")).contains("Great Britain and NI");
        assertThat(NameRequestService.clean("Côte\u0000d'Ivoire")).contains("Côte d'Ivoire");
        assertThat(NameRequestService.clean("São Tomé")).contains("São Tomé");
    }

    @Test
    void nothingToReadIsNoSuggestion() {
        assertThat(NameRequestService.clean(null)).isEmpty();
        assertThat(NameRequestService.clean("")).isEmpty();
        assertThat(NameRequestService.clean(" \n\t ")).isEmpty();
        assertThat(NameRequestService.clean("?!. -")).isEmpty();
        assertThat(NameRequestService.clean("7")).contains("7");
        assertThat(NameRequestService.clean("日本")).contains("日本");
    }

    @Test
    void theLimitIsEightyCharactersAfterCleaning() {
        assertThat(NameRequestService.clean("x".repeat(80))).isPresent();
        assertThat(NameRequestService.clean("x".repeat(81))).isEmpty();
        assertThat(NameRequestService.clean("   " + "x".repeat(80) + "\n")).isPresent();
        assertThat(NameRequestService.clean("🇬🇧 " + "x".repeat(77))).as("80 code points")
                .isPresent();
        assertThat(NameRequestService.clean("🇬🇧 " + "x".repeat(78))).isEmpty();
    }

    @Test
    void suggestionsAreTheSameAsTheMatcherReadsThem() {
        assertThat(NameRequestService.sameKey("Great Britain", "great-britain")).isTrue();
        assertThat(NameRequestService.sameKey("Great Britain", "GreatBritain")).isTrue();
        assertThat(NameRequestService.sameKey("the Great Britain", "Great Britain")).isTrue();
        assertThat(NameRequestService.sameKey("Sta. Lucía", "santa lucia")).isTrue();
        assertThat(NameRequestService.sameKey("Trinidad & Tobago", "Trinidad and Tobago")).isTrue();
        assertThat(NameRequestService.sameKey("Great Britain", "Great Britain and NI")).isFalse();
        assertThat(NameRequestService.sameKey("日本", "日本")).isTrue();
        assertThat(NameRequestService.sameKey("日本", "日本国")).isFalse();
        // Nothing the matcher reads is never the same as something it does.
        assertThat(NameRequestService.sameKey("日本", "=日本")).isFalse();
    }
}

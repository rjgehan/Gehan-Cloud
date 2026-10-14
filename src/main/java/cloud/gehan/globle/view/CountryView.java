package cloud.gehan.globle.view;

import cloud.gehan.globle.catalog.Country;

import java.util.List;

/**
 * A guessable place for the autocomplete. {@code canBeAnswer} lets the list mark
 * territories, which can be guessed for their distance but are never the mystery.
 * That is no clue: the rule (the UN members plus four) is public. Coordinates are
 * left out, apart from the capital's, which the capitals globe draws.
 *
 * <p>Names come in English and Spanish ({@code nameEs}, {@code aliasesEs}, and the
 * same on the capital). The page shows the one in its language, localising places by
 * code since game states name them in English, and its matcher accepts both
 * languages' names whichever one it shows.
 */
public record CountryView(String code, String name, String nameEs, String iso2, List<String> aliases,
                          List<String> aliasesEs, String region, boolean canBeAnswer, CapitalView capital) {

    public record CapitalView(String name, String nameEs, List<String> aliases, List<String> aliasesEs,
                              double lat, double lng) {
    }

    public static CountryView of(Country country) {
        Country.Capital capital = country.capital();
        return new CountryView(country.code(), country.name(), country.nameEs(), country.iso2(), country.aliases(),
                country.aliasesEs(), country.region(), country.answer(),
                capital == null ? null
                        : new CapitalView(capital.name(), capital.nameEs(), capital.aliases(), capital.aliasesEs(),
                                capital.lat(), capital.lng()));
    }
}

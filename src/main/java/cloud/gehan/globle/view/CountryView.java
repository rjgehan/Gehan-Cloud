package cloud.gehan.globle.view;

import cloud.gehan.globle.catalog.Country;

import java.util.List;

/**
 * A guessable place for the autocomplete. {@code canBeAnswer} lets the list mark
 * territories, which can be guessed for their distance but are never the mystery.
 * That is no clue: the rule (the UN members plus four) is public. Coordinates are
 * left out, apart from the capital's, which the capitals globe draws.
 */
public record CountryView(String code, String name, String iso2, List<String> aliases, String region,
                          boolean canBeAnswer, CapitalView capital) {

    public record CapitalView(String name, List<String> aliases, double lat, double lng) {
    }

    public static CountryView of(Country country) {
        Country.Capital capital = country.capital();
        return new CountryView(country.code(), country.name(), country.iso2(), country.aliases(), country.region(),
                country.answer(),
                capital == null ? null
                        : new CapitalView(capital.name(), capital.aliases(), capital.lat(), capital.lng()));
    }
}

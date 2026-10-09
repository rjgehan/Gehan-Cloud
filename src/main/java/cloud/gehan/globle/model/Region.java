package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The region an unlimited game draws its answer from. WORLD means anywhere.
 *
 * <p>Antarctica is a region in the data but not a choice here: nothing there can be
 * an answer, so a game confined to it could never start.
 */
public enum Region {
    WORLD, AFRICA, AMERICAS, ASIA, EUROPE, OCEANIA;

    /** The lowercase name the API and countries.json use. */
    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether a country whose data region is {@code countryRegion} belongs to this filter. */
    public boolean includes(String countryRegion) {
        return this == WORLD || api().equals(countryRegion);
    }

    /** Empty for anything that is not one of the API names; blank means the whole world. */
    public static Optional<Region> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(WORLD);
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Region region : values()) {
            if (region.api().equals(wanted)) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }
}

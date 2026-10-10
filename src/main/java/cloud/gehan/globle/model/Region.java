package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Where an unlimited game draws its answer from. WORLD means anywhere.
 *
 * <p>MAINLAND is the world without the small island nations: Tonga, Malta, Barbados
 * and the like. Japan, the UK, New Zealand and other large islands stay in. Which
 * countries count as small islands is worked out by tools/globle from the shapes
 * themselves (see its README) and arrives as each country's {@code smallIsland} flag.
 *
 * <p>Antarctica is a region in the data but not a choice here: nothing there can be
 * an answer, so a game confined to it could never start.
 */
public enum Region {
    WORLD, AFRICA, AMERICAS, ASIA, EUROPE, OCEANIA, MAINLAND;

    /** The lowercase name the API and countries.json use. */
    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Whether a country belongs to this filter, given its data region and whether it
     * is a small island nation.
     */
    public boolean includes(String countryRegion, boolean smallIsland) {
        return switch (this) {
            case WORLD -> true;
            case MAINLAND -> !smallIsland;
            default -> api().equals(countryRegion);
        };
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

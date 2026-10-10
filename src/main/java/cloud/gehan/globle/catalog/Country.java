package cloud.gehan.globle.catalog;

import java.util.List;

/**
 * One entry of countries.json: a country or territory that can be guessed.
 *
 * @param code        ISO 3166-1 alpha-3, or a stable made-up code where there is none
 * @param iso2        alpha-2 for the flag emoji, or null
 * @param answer      true for the 197 that can be the mystery; territories can only be guesses
 * @param region      africa, americas, asia, europe, oceania or antarctica
 * @param difficulty  1 (everyone could find it) to 5 (obscure); Hard's draw is weighted
 *                    by it. Curated in tools/globle; places that are never the answer
 *                    are 5. Server-only: the country list the page gets leaves it out.
 * @param smallIsland a small island nation, left out of the Mainland pool. Worked out
 *                    by tools/globle from the shapes; see Region.MAINLAND. Server-only.
 * @param lat         a label point on the main landmass, where the globe aims
 * @param capital     null only where there genuinely is none
 */
public record Country(String code, String name, String iso2, List<String> aliases, boolean answer,
                      String region, int difficulty, boolean smallIsland, double lat, double lng,
                      Capital capital) {

    /** Difficulty tiers run from this to {@link #HARDEST}. */
    public static final int EASIEST = 1;
    public static final int HARDEST = 5;

    public Country {
        aliases = List.copyOf(aliases);
    }

    public boolean hasCapital() {
        return capital != null;
    }

    public record Capital(String name, List<String> aliases, double lat, double lng) {
        public Capital {
            aliases = List.copyOf(aliases);
        }
    }
}

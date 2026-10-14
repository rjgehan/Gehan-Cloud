package cloud.gehan.globle.catalog;

import java.util.List;

/**
 * One entry of countries.json: a country or territory that can be guessed.
 *
 * @param code        ISO 3166-1 alpha-3, or a stable made-up code where there is none
 * @param name        the English name, which the server uses everywhere it names a place
 * @param nameEs      the Spanish name ("Alemania"). The page shows it when it is in
 *                    Spanish, localising by code; the server itself stays English.
 * @param iso2        alpha-2 for the flag emoji, or null
 * @param aliases     other English names people type
 * @param aliasesEs   other Spanish names people type ("Holanda"). The page accepts both
 *                    languages' names whichever language it shows.
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
public record Country(String code, String name, String nameEs, String iso2, List<String> aliases,
                      List<String> aliasesEs, boolean answer, String region, int difficulty,
                      boolean smallIsland, double lat, double lng, Capital capital) {

    /** Difficulty tiers run from this to {@link #HARDEST}. */
    public static final int EASIEST = 1;
    public static final int HARDEST = 5;

    public Country {
        aliases = List.copyOf(aliases);
        aliasesEs = List.copyOf(aliasesEs);
    }

    public boolean hasCapital() {
        return capital != null;
    }

    /**
     * @param name      the English name
     * @param nameEs    the Spanish name ("Londres"); the same as the English one where
     *                  Spanish uses it unchanged ("Lima"), never missing
     * @param aliases   other English names people type
     * @param aliasesEs other Spanish names people type ("La Haya")
     */
    public record Capital(String name, String nameEs, List<String> aliases, List<String> aliasesEs,
                          double lat, double lng) {
        public Capital {
            aliases = List.copyOf(aliases);
            aliasesEs = List.copyOf(aliasesEs);
        }
    }
}

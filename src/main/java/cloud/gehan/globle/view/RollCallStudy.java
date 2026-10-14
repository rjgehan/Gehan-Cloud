package cloud.gehan.globle.view;

import java.util.List;

/**
 * "Study what you missed": the countries the player's most recent finished "Name them
 * all" run in a region did not name, each with what helps place it and learn it, most
 * worth learning first. Worked out on each request from the runs stored; nothing is
 * stored for it.
 *
 * <p>The run is over, so nothing is hidden: which countries were missed is on its end
 * card already. Each one carries a coarse familiarity from its difficulty tier, the
 * tiers being public in the repository; the tier itself stays on the server.
 *
 * @param runId       the run studied: the player's newest finished run in the region,
 *                    whether or not it counts
 * @param region      as requested ("world", a continent, or "mainland")
 * @param finishedAt  when it finished, an ISO-8601 instant to the millisecond
 * @param namedCount  how many it named, filled-in ones included (as on its end card)
 * @param total       how many countries the region holds
 * @param missedCount how many it missed: {@code missed}'s length
 * @param missed      every country it missed, in {@code order}
 * @param order       how {@code missed} is sorted: "named-rate" when the region has
 *                    enough finished runs to say how often people name each country
 *                    (most named first, then by familiarity, then by code), or
 *                    "familiarity" when it has not (well-known, then familiar, then
 *                    obscure, then by code)
 */
public record RollCallStudy(long runId, String region, String finishedAt, int namedCount, int total,
                            int missedCount, List<Missed> missed, String order) {

    /**
     * One missed country.
     *
     * @param code         its code
     * @param region       its continent: "africa", "americas", "asia", "europe" or "oceania"
     * @param lat          its label point, where the globe aims
     * @param lng          its label point, where the globe aims
     * @param nearby       every answer country it borders, then the nearest others, to at
     *                     least 4 (any continent), nearest first by the border-to-border
     *                     distance, 0 km meaning they share a border; ties go to the one
     *                     whose label point is closer
     * @param nearestNamed the country the run named that is nearest to it, or null when
     *                     the run named none
     * @param namedRate    of {@code sampleRuns}, the share that named it, to four decimal
     *                     places; null when there are fewer than 3
     * @param sampleRuns   every run in the region that counts (finished, with at least
     *                     one country typed), by every player who still exists, this
     *                     player included; filled-in countries count as named, as they
     *                     do on a run's own end card
     * @param yourRuns     this player's runs in the region that count
     * @param yourMisses   how many of those did not name it
     * @param familiarity  "well-known" (difficulty tier 1 or 2), "familiar" (3) or
     *                     "obscure" (4 or 5)
     */
    public record Missed(String code, String region, double lat, double lng, List<Near> nearby, Near nearestNamed,
                         Double namedRate, int sampleRuns, int yourRuns, int yourMisses, String familiarity) {
    }

    /** @param km the border-to-border distance; 0 for a shared border */
    public record Near(String code, int km) {
    }
}

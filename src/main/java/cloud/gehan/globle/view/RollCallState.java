package cloud.gehan.globle.view;

import java.util.List;

/**
 * A roll call run ("Name them all") as the client sees it. There is no mystery, so
 * nothing here is hidden while playing except the one thing the client must not
 * learn early: which countries are still missing. {@code missed} stays null until the
 * run is over. Difficulty tiers never appear; a filled-in country is just
 * {@code auto: true}. Times are strings, as in {@link GameState}.
 *
 * @param id             the run's id, for the name and finish routes
 * @param mode           always "rollcall"
 * @param region         "world", "africa", "americas", "asia", "europe", "oceania" or "mainland"
 * @param status         PLAYING or FINISHED
 * @param autofill       what was filled in at the start: "off", "easy", "known" or "mine"
 * @param total          how many countries this run's region holds
 * @param named          the countries named, in the order named, the filled-in ones first
 * @param namedCount     how many have been named, filled-in ones included
 * @param autoCount      how many of those were filled in
 * @param byRegion       progress per continent present in the run's pool, always in the
 *                       order africa, americas, asia, europe, oceania
 * @param elapsedSeconds whole seconds from the start to the finish, or to now while
 *                       playing, by the server's clock
 * @param startedAt      ISO-8601 instant
 * @param finishedAt     ISO-8601 instant, or null while playing
 * @param missed         null while playing; once FINISHED, every code of the pool not
 *                       named, sorted by code (empty when all were named)
 */
public record RollCallState(long id, String mode, String region, String status, String autofill, int total,
                            List<Named> named, int namedCount, int autoCount, List<RegionProgress> byRegion,
                            long elapsedSeconds, String startedAt, String finishedAt, List<String> missed) {

    /** @param auto filled in when the run started, rather than typed */
    public record Named(String code, boolean auto) {
    }

    /** @param region a continent: "africa", "americas", "asia", "europe" or "oceania" */
    public record RegionProgress(String region, int named, int total) {
    }
}

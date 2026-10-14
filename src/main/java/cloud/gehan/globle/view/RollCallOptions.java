package cloud.gehan.globle.view;

/**
 * What the "Name them all" setup card needs for one region: how many countries each
 * autofill would fill in, and the player's record there. Counts only; which countries
 * are easy stays on the server until a run is started with that autofill.
 *
 * <p>The record is made of the player's runs here that count: finished, with at least
 * one name typed. A run with nothing typed (ended empty, or only filled in) is in none
 * of these numbers; only {@code studyable} sees it.
 *
 * <p>An autofill whose count equals {@code total} would leave nothing to name, and
 * starting a run with it is refused (400 bad-request); the card shows it disabled.
 *
 * @param region       as requested ("world", a continent, or "mainland")
 * @param total        the countries to name in this region
 * @param easy         how many "easy" would fill in (difficulty tier 1)
 * @param known        how many "known" would fill in (tiers 1 and 2)
 * @param mine         how many "mine" would fill in; null until the player has two runs
 *                     here that count, when "mine" is refused
 * @param finishedRuns the player's runs here that count
 * @param studyable    the player has a finished run here, counting or not: what
 *                     GET /rollcall/study would study
 * @param best         the player's best run here that counts (most named, then
 *                     fastest), or null with none
 */
public record RollCallOptions(String region, int total, int easy, int known, Integer mine, int finishedRuns,
                              boolean studyable, Best best) {

    /**
     * @param named     countries named in that run, filled-in ones included
     * @param total     the region's countries, as now
     * @param seconds   how long it took, start to finish
     * @param autoCount how many were filled in
     */
    public record Best(int named, int total, long seconds, int autoCount) {
    }
}

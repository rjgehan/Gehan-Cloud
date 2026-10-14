package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.Region;

import java.time.Duration;
import java.time.Instant;

/**
 * A finished roll call run without its list of names: everything the "Name them all"
 * board counts, selected column by column like {@link GameSummary}.
 *
 * @param namedCount    how many countries were named, filled-in ones included
 * @param autofillCount how many of them were filled in; null only on a row written
 *                      by hand without one, read as 0
 */
public record RollCallSummary(Long userId, String username, Region region, Integer namedCount,
                              Integer autofillCount, Instant startedAt, Instant finishedAt) {

    public int named() {
        return namedCount == null ? 0 : namedCount;
    }

    public int autoCount() {
        return autofillCount == null ? 0 : Math.max(0, Math.min(autofillCount, named()));
    }

    /** How many the player typed: everything named after what was filled in. */
    public int typed() {
        return named() - autoCount();
    }

    /**
     * Whether the run counts on the board: at least one name typed. A summary is of a
     * finished run, so this is the rule of {@code GlobleGame#counts()}; a run with
     * nothing typed, ended empty or only filled in, is kept but counts nowhere.
     */
    public boolean counts() {
        return typed() >= 1;
    }

    /** Whole seconds from start to finish; the same rule as the run's own stopwatch. */
    public long seconds() {
        return secondsBetween(startedAt, finishedAt);
    }

    /** Whole seconds from {@code from} to {@code to}, rounded down; 0 if either is missing or out of order. */
    public static long secondsBetween(Instant from, Instant to) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        return Duration.between(from, to).getSeconds();
    }
}

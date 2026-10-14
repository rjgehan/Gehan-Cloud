package cloud.gehan.globle.model;

import java.util.Locale;

/**
 * Which game a row belongs to. Capitals is an UNLIMITED game of kind CAPITAL.
 *
 * <p>Every lookup and every board names the mode it wants, so rows of one mode never
 * reach another's: a roll call run is never a practice game in progress, and never
 * counts on a daily or practice board.
 */
public enum Mode {
    /** Today's shared puzzle. Counts toward streaks and the daily leaderboard. */
    DAILY,
    /** Practice: a fresh random answer whenever you like. */
    UNLIMITED,
    /**
     * "Name them all": no mystery, name as many countries of a region as you can. Its
     * rows ({@link GlobleGame#rollCall}) end FINISHED, never WON or GAVE_UP, and have
     * a board of their own.
     */
    ROLLCALL;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }
}

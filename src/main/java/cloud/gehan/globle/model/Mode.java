package cloud.gehan.globle.model;

import java.util.Locale;

/** Which game a row belongs to. Capitals is an UNLIMITED game of kind CAPITAL. */
public enum Mode {
    /** Today's shared puzzle. Counts toward streaks and the daily leaderboard. */
    DAILY,
    /** Practice: a fresh random answer whenever you like. */
    UNLIMITED;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }
}

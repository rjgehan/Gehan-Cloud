package cloud.gehan.globle.model;

/**
 * Where a game stands. Only PLAYING accepts moves.
 *
 * <p>A daily or practice game ends WON or GAVE_UP, and only PLAYING hides its answer.
 * A roll call run ("Name them all", {@link Mode#ROLLCALL}) has no answer to hide and
 * nothing to win or lose: it ends FINISHED, whether the player said they were done,
 * started over after naming something, or named every country in its region. The
 * two sets never mix; {@link GlobleGame#finish} refuses a mismatch.
 */
public enum Status {
    PLAYING, WON, GAVE_UP,
    /** A roll call run that is over. Never a daily or practice game's status. */
    FINISHED
}

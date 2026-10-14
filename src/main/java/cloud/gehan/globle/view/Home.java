package cloud.gehan.globle.view;

import java.util.List;

/**
 * The home screen in one response: where the player stands on today's two dailies,
 * and which practice games and "Name them all" runs they can pick up again. Reading it
 * draws no answer, and for a player stores nothing. For the admin it counts the open
 * "request a name change"s, which first closes those the data already names (see
 * NameRequestService).
 *
 * @param puzzle       today's puzzle number, shared by the classic and Hard dailies
 * @param date         today's puzzle date (yyyy-MM-dd)
 * @param nextPuzzleAt the next local midnight, with its offset
 * @param inProgress   unlimited games in progress, newest first, at most one per slot.
 *                     Never a roll call run: those are in {@code rollcall}.
 * @param rollcall     "Name them all" runs in progress, newest first, at most one per region
 * @param admin        whether the session has the admin role (ROLE_ADMIN, as for /users)
 * @param nameRequests the admin only: how many "request a name change"s are open; null
 *                     for everybody else
 */
public record Home(int puzzle, String date, String nextPuzzleAt, Daily classic, Daily hard,
                   List<InProgress> inProgress, List<RollCall> rollcall, boolean admin, Integer nameRequests) {

    /**
     * @param status        NEW (not started today), PLAYING, WON or GAVE_UP
     * @param guessCount    today's guesses so far
     * @param currentStreak as on the leaderboard for this variant
     */
    public record Daily(String status, int guessCount, int currentStreak) {
    }

    /** A practice game to resume; its slot is (kind, region, variant). */
    public record InProgress(long id, String kind, String region, String variant, int guessCount) {
    }

    /**
     * A "Name them all" run to resume.
     *
     * @param namedCount countries named so far, filled-in ones included
     * @param total      the countries in the run's region
     */
    public record RollCall(long id, String region, int namedCount, int total) {
    }
}

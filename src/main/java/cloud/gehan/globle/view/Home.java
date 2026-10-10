package cloud.gehan.globle.view;

import java.util.List;

/**
 * The home screen in one response: where the player stands on today's two dailies,
 * and which practice games they can pick up again. Reading it stores nothing and
 * draws no answer.
 *
 * @param puzzle       today's puzzle number, shared by the classic and Hard dailies
 * @param date         today's puzzle date (yyyy-MM-dd)
 * @param nextPuzzleAt the next local midnight, with its offset
 * @param inProgress   unlimited games in progress, newest first, at most one per slot
 */
public record Home(int puzzle, String date, String nextPuzzleAt, Daily classic, Daily hard,
                   List<InProgress> inProgress) {

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
}

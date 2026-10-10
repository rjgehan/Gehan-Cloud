package cloud.gehan.globle.view;

import java.util.List;
import java.util.Map;

/**
 * Everything the leaderboard and stats dialogs show, in one response.
 *
 * <p>Averages are rounded to one decimal and win rates to two, the precision the
 * client displays, so it never rounds a second time; sorting happens on the exact
 * values before rounding.
 *
 * <p>A request names one daily variant, classic or Hard; they are separate puzzles
 * with separate boards and streaks. Today, Week, All time and Me are that variant's.
 * The practice boards are always all three.
 *
 * @param variant   "classic" or "hard": which daily the daily sections describe
 * @param today     today's daily, as a race
 * @param week      daily results over the last seven puzzle dates, today included
 * @param allTime   daily results ever
 * @param unlimited finished practice games, per kind and variant
 * @param me        the viewer's own daily record
 */
public record Leaderboard(String variant, Today today, List<WeekRow> week, List<AllTimeRow> allTime,
                          Unlimited unlimited, Me me) {

    /**
     * @param viewerFinished whether the viewer has won or given up today
     * @param entries        winners first (fewest guesses, then earliest), then those still
     *                       playing, then those who gave up; nobody without a guess today
     */
    public record Today(int puzzle, String date, boolean viewerFinished, List<TodayEntry> entries) {
    }

    /**
     * @param finishedAt ISO-8601 instant, or null while playing
     * @param rank       winners only; equal guess counts share a rank (1, 1, 3)
     */
    public record TodayEntry(String username, String status, int guessCount, String finishedAt, Integer rank,
                             boolean you) {
    }

    /** @param avgGuesses over wins only; null with none */
    public record WeekRow(String username, int played, int wins, Double avgGuesses, boolean you) {
    }

    /** @param best fewest guesses in a win; null with none */
    public record AllTimeRow(String username, int played, int wins, double winRate, Double avgGuesses,
                             int currentStreak, int maxStreak, Integer best, boolean you) {
    }

    /**
     * @param country classic country games of every region, Mainland included
     * @param capital capital games
     * @param hard    Hard practice games
     */
    public record Unlimited(List<UnlimitedRow> country, List<UnlimitedRow> capital, List<UnlimitedRow> hard) {
    }

    public record UnlimitedRow(String username, int played, int wins, Double avgGuesses, Integer best,
                               boolean you) {
    }

    /** @param distribution daily wins by guess count, keyed "1".."6", "7-9", "10-14", "15+" */
    public record Me(int played, int wins, double winRate, Double avgGuesses, int currentStreak, int maxStreak,
                     Map<String, Integer> distribution) {
    }
}

package cloud.gehan.globle.service;

import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.repository.GameSummary;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.view.Leaderboard;
import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Works out the leaderboard from every game's summary.
 *
 * <p>A family plays a handful of games a day, so this reads the summaries (no guess
 * lists) and counts in memory rather than pushing each statistic into SQL: the rules
 * below stay readable as code, and the cost is a few thousand small rows.
 *
 * <p>The rules:
 * <ul>
 * <li><b>Played</b> means a daily that was won, given up, or left unfinished on a past
 *     date. Today's game in progress is not counted until it ends; otherwise win rates
 *     would dip with every first guess and recover with the last.</li>
 * <li><b>Average guesses</b> are over wins only; a give-up has no meaningful count.</li>
 * <li><b>Current streak</b> is consecutive puzzle dates won, ending today, or ending
 *     yesterday while today is unplayed or still in progress - so a streak survives
 *     until midnight, but a give-up today ends it at once.</li>
 * <li><b>Only users who still exist</b> are listed, matched by id and username both
 *     (see {@link Player}).</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LeaderboardService {

    /** Distribution buckets, in display order. The client has the same list. */
    static final List<String> BUCKETS = List.of("1", "2", "3", "4", "5", "6", "7-9", "10-14", "15+");

    private static final int WEEK_DAYS = 7;

    private static final Comparator<Double> AVERAGE_ASC = Comparator.nullsLast(Comparator.naturalOrder());

    private final GlobleGameRepository games;
    private final UserRepository users;
    private final DailyPuzzles puzzles;
    private final GlobleClock clock;

    public LeaderboardService(GlobleGameRepository games, UserRepository users, DailyPuzzles puzzles,
                              GlobleClock clock) {
        this.games = games;
        this.users = users;
        this.puzzles = puzzles;
        this.clock = clock;
    }

    public Leaderboard leaderboard(Player viewer) {
        LocalDate today = clock.today();
        Map<Long, String> existing = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        List<GameSummary> daily = owned(games.summaries(Mode.DAILY), existing);
        List<GameSummary> unlimited = owned(games.summaries(Mode.UNLIMITED), existing);

        List<GameSummary> played = daily.stream().filter(g -> countsAsPlayed(g, today)).toList();
        Map<String, List<GameSummary>> playedBy = byUser(played);

        List<GameSummary> mine = playedBy.getOrDefault(viewer.username(), List.of());

        return new Leaderboard(
                today(daily, today, viewer),
                week(played, today, viewer),
                allTime(playedBy, today, viewer),
                new Leaderboard.Unlimited(unlimited(unlimited, Kind.COUNTRY, viewer),
                        unlimited(unlimited, Kind.CAPITAL, viewer)),
                me(mine, today));
    }

    // ------------------------------------------------------------------ sections

    private Leaderboard.Today today(List<GameSummary> daily, LocalDate today, Player viewer) {
        List<GameSummary> todays = daily.stream()
                .filter(g -> today.equals(g.puzzleDate()))
                .filter(g -> guesses(g) > 0 || g.status() != Status.PLAYING)
                .toList();

        List<GameSummary> won = todays.stream().filter(GameSummary::won)
                .sorted(Comparator.comparingInt(LeaderboardService::guesses)
                        .thenComparing(GameSummary::finishedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(GameSummary::username))
                .toList();
        List<GameSummary> playing = todays.stream().filter(g -> g.status() == Status.PLAYING)
                .sorted(Comparator.comparingInt(LeaderboardService::guesses).thenComparing(GameSummary::username))
                .toList();
        List<GameSummary> gaveUp = todays.stream().filter(g -> g.status() == Status.GAVE_UP)
                .sorted(Comparator.comparingInt(LeaderboardService::guesses).thenComparing(GameSummary::username))
                .toList();

        List<Leaderboard.TodayEntry> entries = new ArrayList<>();
        // Standard competition ranking: equal guess counts share a place and the next
        // place is skipped (1, 1, 3). Finishing time only orders the tied rows.
        int rank = 0;
        int previous = -1;
        for (int i = 0; i < won.size(); i++) {
            GameSummary g = won.get(i);
            if (guesses(g) != previous) {
                rank = i + 1;
                previous = guesses(g);
            }
            entries.add(entry(g, rank, viewer));
        }
        playing.forEach(g -> entries.add(entry(g, null, viewer)));
        gaveUp.forEach(g -> entries.add(entry(g, null, viewer)));

        boolean viewerFinished = todays.stream()
                .anyMatch(g -> g.username().equals(viewer.username()) && g.status() != Status.PLAYING);
        return new Leaderboard.Today(puzzles.puzzleNumber(today), today.toString(), viewerFinished,
                List.copyOf(entries));
    }

    private static Leaderboard.TodayEntry entry(GameSummary g, Integer rank, Player viewer) {
        Instant finished = g.finishedAt();
        return new Leaderboard.TodayEntry(g.username(), g.status().name(), guesses(g),
                finished == null ? null : finished.toString(), rank, g.username().equals(viewer.username()));
    }

    private static List<Leaderboard.WeekRow> week(List<GameSummary> played, LocalDate today, Player viewer) {
        LocalDate from = today.minusDays(WEEK_DAYS - 1);
        Map<String, List<GameSummary>> byUser = byUser(played.stream()
                .filter(g -> !g.puzzleDate().isBefore(from) && !g.puzzleDate().isAfter(today))
                .toList());
        record Row(Leaderboard.WeekRow view, Double average) {
        }
        return byUser.entrySet().stream()
                .map(e -> {
                    Tally t = Tally.of(e.getValue());
                    return new Row(new Leaderboard.WeekRow(e.getKey(), t.played, t.wins, round(t.average(), 1),
                            e.getKey().equals(viewer.username())), t.average());
                })
                .sorted(Comparator.comparingInt((Row r) -> r.view.wins()).reversed()
                        .thenComparing(Row::average, AVERAGE_ASC)
                        .thenComparing(r -> r.view.username()))
                .map(Row::view)
                .toList();
    }

    private static List<Leaderboard.AllTimeRow> allTime(Map<String, List<GameSummary>> playedBy, LocalDate today,
                                                         Player viewer) {
        record Row(Leaderboard.AllTimeRow view, double rate, Double average) {
        }
        return playedBy.entrySet().stream()
                .map(e -> {
                    Tally t = Tally.of(e.getValue());
                    Streaks s = Streaks.of(e.getValue(), today);
                    return new Row(new Leaderboard.AllTimeRow(e.getKey(), t.played, t.wins, round(t.rate(), 2),
                            round(t.average(), 1), s.current, s.max, t.best, e.getKey().equals(viewer.username())),
                            t.rate(), t.average());
                })
                .sorted(Comparator.comparingInt((Row r) -> r.view.wins()).reversed()
                        .thenComparing(Comparator.comparingDouble(Row::rate).reversed())
                        .thenComparing(Row::average, AVERAGE_ASC)
                        .thenComparing(r -> r.view.username()))
                .map(Row::view)
                .toList();
    }

    /** Finished practice games only: one still in progress is neither won nor lost yet. */
    private static List<Leaderboard.UnlimitedRow> unlimited(List<GameSummary> unlimited, Kind kind, Player viewer) {
        Map<String, List<GameSummary>> byUser = byUser(unlimited.stream()
                .filter(g -> g.kind() == kind && g.status() != Status.PLAYING)
                .toList());
        record Row(Leaderboard.UnlimitedRow view, Double average) {
        }
        return byUser.entrySet().stream()
                .map(e -> {
                    Tally t = Tally.of(e.getValue());
                    return new Row(new Leaderboard.UnlimitedRow(e.getKey(), t.played, t.wins, round(t.average(), 1),
                            t.best, e.getKey().equals(viewer.username())), t.average());
                })
                .sorted(Comparator.comparingInt((Row r) -> r.view.wins()).reversed()
                        .thenComparing(Row::average, AVERAGE_ASC)
                        .thenComparing(r -> r.view.username()))
                .map(Row::view)
                .toList();
    }

    private static Leaderboard.Me me(List<GameSummary> mine, LocalDate today) {
        Tally t = Tally.of(mine);
        Streaks s = Streaks.of(mine, today);
        Map<String, Integer> distribution = new LinkedHashMap<>();
        BUCKETS.forEach(bucket -> distribution.put(bucket, 0));
        mine.stream().filter(GameSummary::won)
                .forEach(g -> distribution.merge(bucket(guesses(g)), 1, Integer::sum));
        return new Leaderboard.Me(t.played, t.wins, round(t.rate(), 2), round(t.average(), 1), s.current, s.max,
                distribution);
    }

    // ------------------------------------------------------------------ helpers

    /** Which distribution bar a win in {@code n} guesses belongs to. */
    static String bucket(int n) {
        if (n <= 6) {
            return String.valueOf(Math.max(1, n));
        }
        if (n <= 9) {
            return "7-9";
        }
        return n <= 14 ? "10-14" : "15+";
    }

    /** Daily games that count as played as of {@code today}; see the class comment. */
    private static boolean countsAsPlayed(GameSummary g, LocalDate today) {
        if (g.status() != Status.PLAYING) {
            return true;
        }
        return g.puzzleDate() != null && g.puzzleDate().isBefore(today) && guesses(g) > 0;
    }

    /** Games whose owner still exists under the same id and username. */
    private static List<GameSummary> owned(List<GameSummary> all, Map<Long, String> existing) {
        return all.stream()
                .filter(g -> g.userId() != null && Objects.equals(existing.get(g.userId()), g.username()))
                .toList();
    }

    private static Map<String, List<GameSummary>> byUser(List<GameSummary> games) {
        return games.stream().collect(Collectors.groupingBy(GameSummary::username, HashMap::new,
                Collectors.toList()));
    }

    private static int guesses(GameSummary g) {
        return g.guessCount() == null ? 0 : g.guessCount();
    }

    private static Double round(Double value, int places) {
        if (value == null) {
            return null;
        }
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }

    /** Counts over one player's games. */
    private record Tally(int played, int wins, Double average, Integer best) {

        static Tally of(List<GameSummary> games) {
            List<Integer> wins = games.stream().filter(GameSummary::won).map(LeaderboardService::guesses).toList();
            Double average = wins.isEmpty() ? null : wins.stream().mapToInt(Integer::intValue).average().orElse(0);
            Integer best = wins.stream().min(Integer::compare).orElse(null);
            return new Tally(games.size(), wins.size(), average, best);
        }

        double rate() {
            return played == 0 ? 0.0 : (double) wins / played;
        }
    }

    /** Runs of consecutive won puzzle dates. */
    private record Streaks(int current, int max) {

        static Streaks of(List<GameSummary> games, LocalDate today) {
            Set<LocalDate> won = games.stream()
                    .filter(GameSummary::won)
                    .map(GameSummary::puzzleDate)
                    .filter(Objects::nonNull)
                    .filter(date -> !date.isAfter(today))
                    .collect(Collectors.toCollection(TreeSet::new));

            // A streak waits until midnight for today's puzzle, but not after giving it up.
            boolean gaveUpToday = games.stream()
                    .anyMatch(g -> today.equals(g.puzzleDate()) && g.status() == Status.GAVE_UP);
            LocalDate day = won.contains(today) ? today : today.minusDays(1);
            int current = 0;
            while (!gaveUpToday && won.contains(day)) {
                current++;
                day = day.minusDays(1);
            }

            int max = 0;
            int run = 0;
            LocalDate last = null;
            for (LocalDate date : won) {
                run = last != null && last.plusDays(1).equals(date) ? run + 1 : 1;
                max = Math.max(max, run);
                last = date;
            }
            return new Streaks(current, max);
        }
    }
}

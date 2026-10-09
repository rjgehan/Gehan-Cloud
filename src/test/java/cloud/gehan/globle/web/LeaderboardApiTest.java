package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class LeaderboardApiTest extends GlobleTestBase {

    @Test
    void todayRanksWinnersByGuessesThenTimeAndTiesShareARank() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var ryan = player("ryan");
        var sam = player("sam");
        var alex = player("alex");
        var bob = player("bob");

        winDailyIn(colleen, 2);                 // first to finish on 2 guesses
        clock.advance(Duration.ofMinutes(5));
        winDailyIn(jen, 2);                     // same count, five minutes later
        clock.advance(Duration.ofMinutes(5));
        winDailyIn(ryan, 4);
        guessDaily(sam, farFrom(answerToday()));
        giveUpDaily(alex);
        getOk("/globle/api/daily", bob);        // looked, never guessed

        JsonNode today = board(jen).get("today");

        assertThat(today.get("puzzle").asInt()).isEqualTo(1);
        assertThat(today.get("date").asText()).isEqualTo("2026-10-09");
        assertThat(today.get("viewerFinished").asBoolean()).isTrue();
        assertThat(names(today.get("entries"))).containsExactly("colleen", "jen", "ryan", "sam", "alex");

        JsonNode entries = today.get("entries");
        assertThat(entries.get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(entries.get(1).get("rank").asInt()).isEqualTo(1);
        assertThat(entries.get(2).get("rank").asInt()).isEqualTo(3);
        assertThat(entries.get(3).get("rank").isNull()).isTrue();
        assertThat(entries.get(4).get("rank").isNull()).isTrue();

        assertThat(entries.get(0).get("status").asText()).isEqualTo("WON");
        assertThat(entries.get(3).get("status").asText()).isEqualTo("PLAYING");
        assertThat(entries.get(4).get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(entries.get(2).get("guessCount").asInt()).isEqualTo(4);
        assertThat(entries.get(0).get("finishedAt").asText()).endsWith("Z");
        assertThat(entries.get(3).get("finishedAt").isNull()).isTrue();

        assertThat(entries.get(1).get("you").asBoolean()).isTrue();
        assertThat(entries.get(0).get("you").asBoolean()).isFalse();

        assertThat(board(sam).get("today").get("viewerFinished").asBoolean()).isFalse();
        assertThat(board(bob).get("today").get("viewerFinished").asBoolean()).isFalse();
    }

    @Test
    void theWeekIsTheLastSevenPuzzleDates() throws Exception {
        var jen = player("jen");
        for (int puzzle : new int[]{1, 3, 9}) {
            clock.setDay(day(puzzle));
            winDailyIn(jen, 3);
        }
        // Today is puzzle 9, so the week runs 3..9: puzzle 1 has dropped off.
        JsonNode board = board(jen);
        JsonNode week = only(board.get("week"));
        assertThat(week.get("username").asText()).isEqualTo("jen");
        assertThat(week.get("played").asInt()).isEqualTo(2);
        assertThat(week.get("wins").asInt()).isEqualTo(2);
        assertThat(week.get("avgGuesses").asDouble()).isEqualTo(3.0);
        assertThat(week.get("you").asBoolean()).isTrue();

        assertThat(only(board.get("allTime")).get("played").asInt()).isEqualTo(3);
    }

    @Test
    void weekAndAllTimeSortByWinsThenRateThenAverage() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var ryan = player("ryan");

        clock.setDay(day(1));
        winDailyIn(jen, 5);
        winDailyIn(colleen, 2);
        winDailyIn(ryan, 3);
        clock.setDay(day(2));
        winDailyIn(jen, 5);
        winDailyIn(colleen, 2);
        giveUpDaily(ryan);
        clock.setDay(day(3));
        winDailyIn(ryan, 1);

        JsonNode board = board(jen);
        // jen and colleen both 2 wins from 2 at 100%: colleen's lower average wins.
        // ryan has 2 wins from 3.
        assertThat(names(board.get("allTime"))).containsExactly("colleen", "jen", "ryan");
        assertThat(names(board.get("week"))).containsExactly("colleen", "ryan", "jen");

        JsonNode ryanRow = board.get("allTime").get(2);
        assertThat(ryanRow.get("played").asInt()).isEqualTo(3);
        assertThat(ryanRow.get("wins").asInt()).isEqualTo(2);
        assertThat(ryanRow.get("winRate").asDouble()).isEqualTo(0.67);
        assertThat(ryanRow.get("avgGuesses").asDouble()).isEqualTo(2.0);
        assertThat(ryanRow.get("best").asInt()).isEqualTo(1);
        assertThat(ryanRow.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(ryanRow.get("maxStreak").asInt()).isEqualTo(1);
    }

    @Test
    void streaksRunAcrossConsecutiveWonDatesAndSurviveUntilMidnight() throws Exception {
        var jen = player("jen");
        for (int puzzle = 1; puzzle <= 6; puzzle++) {
            clock.setDay(day(puzzle));
            if (puzzle == 4) {
                giveUpDaily(jen);
            } else {
                winDailyIn(jen, 2);
            }
        }
        JsonNode me = board(jen).get("me");
        assertThat(me.get("currentStreak").asInt()).isEqualTo(2);   // puzzles 5 and 6
        assertThat(me.get("maxStreak").asInt()).isEqualTo(3);       // puzzles 1 to 3

        // Puzzle 7, not played yet: yesterday's streak still stands.
        clock.setDay(day(7));
        assertThat(board(jen).get("me").get("currentStreak").asInt()).isEqualTo(2);

        // Puzzle 8 without having played 7: the streak is gone, the record is not.
        clock.setDay(day(8));
        JsonNode later = board(jen).get("me");
        assertThat(later.get("currentStreak").asInt()).isZero();
        assertThat(later.get("maxStreak").asInt()).isEqualTo(3);

        JsonNode row = only(board(jen).get("allTime"));
        assertThat(row.get("currentStreak").asInt()).isZero();
        assertThat(row.get("maxStreak").asInt()).isEqualTo(3);
    }

    /** Waiting until midnight is for a puzzle not finished yet, not for one given up. */
    @Test
    void givingUpTodayEndsTheStreakAtOnce() throws Exception {
        var jen = player("jen");
        for (int puzzle = 1; puzzle <= 2; puzzle++) {
            clock.setDay(day(puzzle));
            winDailyIn(jen, 2);
        }
        clock.setDay(day(3));
        guessDaily(jen, farFrom(answerToday()));
        assertThat(board(jen).get("me").get("currentStreak").asInt())
                .as("still playing today").isEqualTo(2);

        giveUpDaily(jen);

        JsonNode board = board(jen);
        JsonNode me = board.get("me");
        assertThat(me.get("currentStreak").asInt()).isZero();
        assertThat(me.get("maxStreak").asInt()).isEqualTo(2);
        JsonNode row = only(board.get("allTime"));
        assertThat(row.get("currentStreak").asInt()).isZero();
        assertThat(row.get("maxStreak").asInt()).isEqualTo(2);
    }

    @Test
    void anUnfinishedDailyCountsAsALossOnlyOnceItsDayIsOver() throws Exception {
        var jen = player("jen");
        guessDaily(jen, farFrom(answerToday()));

        JsonNode today = board(jen);
        assertThat(today.get("me").get("played").asInt()).isZero();
        assertThat(today.get("allTime")).isEmpty();
        assertThat(only(today.get("today").get("entries")).get("status").asText()).isEqualTo("PLAYING");

        clock.setDay(day(2));
        JsonNode tomorrow = board(jen);
        assertThat(tomorrow.get("me").get("played").asInt()).isEqualTo(1);
        assertThat(tomorrow.get("me").get("wins").asInt()).isZero();
        assertThat(tomorrow.get("me").get("winRate").asDouble()).isZero();
        assertThat(only(tomorrow.get("allTime")).get("played").asInt()).isEqualTo(1);
        assertThat(tomorrow.get("today").get("entries")).isEmpty();
    }

    @Test
    void theDistributionBucketsWinsByGuessCount() throws Exception {
        var jen = player("jen");
        int[] counts = {1, 3, 7, 12, 20, 6};
        for (int i = 0; i < counts.length; i++) {
            clock.setDay(day(i + 1));
            winDailyIn(jen, counts[i]);
        }
        clock.setDay(day(counts.length + 1));
        giveUpDaily(jen);

        JsonNode me = board(jen).get("me");
        JsonNode distribution = me.get("distribution");
        List<String> keys = new ArrayList<>();
        distribution.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactly("1", "2", "3", "4", "5", "6", "7-9", "10-14", "15+");
        assertThat(distribution.get("1").asInt()).isEqualTo(1);
        assertThat(distribution.get("2").asInt()).isZero();
        assertThat(distribution.get("3").asInt()).isEqualTo(1);
        assertThat(distribution.get("6").asInt()).isEqualTo(1);
        assertThat(distribution.get("7-9").asInt()).isEqualTo(1);
        assertThat(distribution.get("10-14").asInt()).isEqualTo(1);
        assertThat(distribution.get("15+").asInt()).isEqualTo(1);

        assertThat(me.get("played").asInt()).isEqualTo(7);
        assertThat(me.get("wins").asInt()).isEqualTo(6);
        assertThat(me.get("winRate").asDouble()).isEqualTo(0.86);
        assertThat(me.get("avgGuesses").asDouble()).isEqualTo(8.2);   // 49 / 6 = 8.17
        assertThat(only(board(jen).get("allTime")).get("best").asInt()).isEqualTo(1);
    }

    @Test
    void aNewPlayerHasAnEmptyRecord() throws Exception {
        var jen = player("jen");
        JsonNode board = board(jen);
        JsonNode me = board.get("me");
        assertThat(me.get("played").asInt()).isZero();
        assertThat(me.get("wins").asInt()).isZero();
        assertThat(me.get("winRate").asDouble()).isZero();
        assertThat(me.get("avgGuesses").isNull()).isTrue();
        assertThat(me.get("currentStreak").asInt()).isZero();
        assertThat(me.get("distribution").get("1").asInt()).isZero();
        assertThat(board.get("today").get("entries")).isEmpty();
        assertThat(board.get("week")).isEmpty();
        assertThat(board.get("allTime")).isEmpty();
        assertThat(board.get("unlimited").get("country")).isEmpty();
        assertThat(board.get("unlimited").get("capital")).isEmpty();
    }

    @Test
    void deletedUsersAreLeftOffEveryBoard() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        winDailyIn(colleen, 2);
        long id = newGame(colleen, "country", "world").get("id").asLong();
        guessUnlimited(colleen, id, answerOf(id));
        winDailyIn(jen, 3);

        users.delete(users.findByUsername("colleen").orElseThrow());

        JsonNode board = board(jen);
        assertThat(names(board.get("today").get("entries"))).containsExactly("jen");
        assertThat(names(board.get("week"))).containsExactly("jen");
        assertThat(names(board.get("allTime"))).containsExactly("jen");
        assertThat(board.get("unlimited").get("country")).isEmpty();
        // Her games are still there, just not shown: nothing cascades into Globle.
        assertThat(games.count()).isEqualTo(3);
    }

    /**
     * SQLite can hand a deleted account's id to the next account created. Games stored
     * against the old username must not show up as the newcomer's, nor block them.
     */
    @Test
    void anAccountThatReusesAnIdDoesNotInheritItsGames() throws Exception {
        var jen = player("jen");
        long jensId = users.findByUsername("jen").orElseThrow().getId();
        GlobleGame ghost = GlobleGame.daily(new Player(jensId, "ghost"), DAY_ONE, clock.instant());
        ghost.addGuess(answerToday());
        ghost.finish(Status.WON, clock.instant());
        games.save(ghost);

        JsonNode daily = getOk("/globle/api/daily", jen);
        assertThat(daily.get("guesses")).isEmpty();
        assertThat(board(jen).get("today").get("entries")).isEmpty();

        // And Jen can still play today: the ghost's row does not collide with hers.
        assertThat(guessDaily(jen, answerToday()).get("status").asText()).isEqualTo("WON");
        assertThat(names(board(jen).get("today").get("entries"))).containsExactly("jen");
    }

    @Test
    void unlimitedBoardsCountFinishedGamesPerKind() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");

        winUnlimitedIn(jen, "country", 3);
        winUnlimitedIn(jen, "country", 5);
        long gaveUp = newGame(jen, "country", "world").get("id").asLong();
        postOk("/globle/api/unlimited/" + gaveUp + "/give-up", null, jen);
        newGame(jen, "country", "world");                // still in progress: not counted
        winUnlimitedIn(jen, "capital", 2);
        winUnlimitedIn(colleen, "country", 1);

        JsonNode unlimited = board(jen).get("unlimited");
        JsonNode country = unlimited.get("country");
        assertThat(names(country)).containsExactly("jen", "colleen");
        JsonNode jens = country.get(0);
        assertThat(jens.get("played").asInt()).isEqualTo(3);
        assertThat(jens.get("wins").asInt()).isEqualTo(2);
        assertThat(jens.get("avgGuesses").asDouble()).isEqualTo(4.0);
        assertThat(jens.get("best").asInt()).isEqualTo(3);
        assertThat(jens.get("you").asBoolean()).isTrue();

        JsonNode capital = only(unlimited.get("capital"));
        assertThat(capital.get("username").asText()).isEqualTo("jen");
        assertThat(capital.get("played").asInt()).isEqualTo(1);
        assertThat(capital.get("best").asInt()).isEqualTo(2);

        // Practice never feeds the daily boards.
        JsonNode board = board(jen);
        assertThat(board.get("allTime")).isEmpty();
        assertThat(board.get("me").get("played").asInt()).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private void winUnlimitedIn(RequestPostProcessor who, String kind, int guesses) throws Exception {
        long id = newGame(who, kind, "world").get("id").asLong();
        String answer = answerOf(id);
        List<String> wrong = catalog.all().stream()
                .filter(c -> !c.code().equals(answer) && c.hasCapital())
                .map(c -> c.code())
                .limit(guesses - 1L)
                .toList();
        for (String code : wrong) {
            guessUnlimited(who, id, code);
        }
        assertThat(guessUnlimited(who, id, answer).get("status").asText()).isEqualTo("WON");
    }

    private static List<String> names(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false).map(r -> r.get("username").asText()).toList();
    }

    private static JsonNode only(JsonNode rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}

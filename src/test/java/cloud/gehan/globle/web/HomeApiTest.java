package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /globle/api/home: today's two dailies, both streaks, and practice to resume. */
class HomeApiTest extends GlobleTestBase {

    @Test
    void aNewPlayersHomeIsAllNewAndStoresNothing() throws Exception {
        var jen = player("jen");
        JsonNode home = home(jen);

        List<String> fields = new ArrayList<>();
        home.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("puzzle", "date", "nextPuzzleAt", "classic", "hard", "inProgress");
        assertThat(home.get("puzzle").asInt()).isEqualTo(1);
        assertThat(home.get("date").asText()).isEqualTo("2026-10-09");
        assertThat(home.get("nextPuzzleAt").asText()).isEqualTo("2026-10-10T00:00:00-04:00");
        for (String variant : new String[]{"classic", "hard"}) {
            JsonNode daily = home.get(variant);
            assertThat(daily.get("status").asText()).as(variant).isEqualTo("NEW");
            assertThat(daily.get("guessCount").asInt()).isZero();
            assertThat(daily.get("currentStreak").asInt()).isZero();
        }
        assertThat(home.get("inProgress")).isEmpty();

        assertThat(games.count()).isZero();
        assertThat(days.count()).isZero();
        assertThat(hardDays.count()).isZero();
    }

    @Test
    void eachDailyShowsWhereThePlayerLeftIt() throws Exception {
        var jen = player("jen");
        guessDaily(jen, farFrom(answerToday()));
        JsonNode playing = home(jen);
        assertThat(playing.get("classic").get("status").asText()).isEqualTo("PLAYING");
        assertThat(playing.get("classic").get("guessCount").asInt()).isEqualTo(1);
        assertThat(playing.get("hard").get("status").asText()).isEqualTo("NEW");

        guessDaily(jen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        giveUpDaily(jen, Variant.HARD);
        guessDaily(jen, answerToday());

        JsonNode later = home(jen);
        assertThat(later.get("classic").get("status").asText()).isEqualTo("WON");
        assertThat(later.get("classic").get("guessCount").asInt()).isEqualTo(2);
        assertThat(later.get("hard").get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(later.get("hard").get("guessCount").asInt()).isEqualTo(1);
    }

    /** Each streak is the leaderboard's, for its own daily. */
    @Test
    void streaksArePerDailyAndMatchTheLeaderboard() throws Exception {
        var jen = player("jen");
        for (int puzzle = 1; puzzle <= 3; puzzle++) {
            clock.setDay(day(puzzle));
            winDailyIn(jen, 3);
            if (puzzle >= 2) {
                winDailyIn(jen, 4, Variant.HARD);
            }
        }
        JsonNode home = home(jen);
        assertThat(home.get("classic").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(home.get("hard").get("currentStreak").asInt()).isEqualTo(2);
        assertThat(home.get("classic").get("currentStreak"))
                .isEqualTo(board(jen, Variant.CLASSIC).get("me").get("currentStreak"));
        assertThat(home.get("hard").get("currentStreak"))
                .isEqualTo(board(jen, Variant.HARD).get("me").get("currentStreak"));

        // The next day, before playing: both streaks stand until midnight.
        clock.setDay(day(4));
        JsonNode tomorrow = home(jen);
        assertThat(tomorrow.get("puzzle").asInt()).isEqualTo(4);
        assertThat(tomorrow.get("classic").get("status").asText()).isEqualTo("NEW");
        assertThat(tomorrow.get("classic").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(tomorrow.get("hard").get("currentStreak").asInt()).isEqualTo(2);

        // Giving up Hard today ends that streak at once, and only that one.
        giveUpDaily(jen, Variant.HARD);
        JsonNode gaveUp = home(jen);
        assertThat(gaveUp.get("hard").get("currentStreak").asInt()).isZero();
        assertThat(gaveUp.get("classic").get("currentStreak").asInt()).isEqualTo(3);
    }

    @Test
    void practiceInProgressIsListedNewestFirst() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        long europe = newGame(jen, "country", "europe").get("id").asLong();
        guessUnlimited(jen, europe, farFrom(answerOf(europe)));
        guessUnlimited(jen, europe, wrongGuesses(answerOf(europe), 3).stream()
                .filter(code -> !code.equals(farFrom(answerOf(europe)))).findFirst().orElseThrow());
        long capitals = newGame(jen, "capital", "world").get("id").asLong();
        long hard = newGame(jen, "country", "world", Variant.HARD).get("id").asLong();
        guessUnlimited(jen, hard, farFrom(answerOf(hard)));
        long finished = newGame(jen, "country", "mainland").get("id").asLong();
        postOk("/globle/api/unlimited/" + finished + "/give-up", null, jen);
        newGame(colleen, "country", "world");
        guessDaily(jen, farFrom(answerToday()));

        JsonNode inProgress = home(jen).get("inProgress");

        assertThat(inProgress).hasSize(3);
        assertThat(inProgress).extracting(g -> g.get("id").asLong()).containsExactly(hard, capitals, europe);
        JsonNode first = inProgress.get(0);
        assertThat(first.get("kind").asText()).isEqualTo("country");
        assertThat(first.get("region").asText()).isEqualTo("world");
        assertThat(first.get("variant").asText()).isEqualTo("hard");
        assertThat(first.get("guessCount").asInt()).isEqualTo(1);
        JsonNode second = inProgress.get(1);
        assertThat(second.get("kind").asText()).isEqualTo("capital");
        assertThat(second.get("variant").asText()).isEqualTo("classic");
        assertThat(second.get("guessCount").asInt()).isZero();
        JsonNode third = inProgress.get(2);
        assertThat(third.get("region").asText()).isEqualTo("europe");
        assertThat(third.get("guessCount").asInt()).isEqualTo(2);
        List<String> fields = new ArrayList<>();
        third.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("id", "kind", "region", "variant", "guessCount");
    }

    /**
     * The home screen and the boards are read while games are in progress, so neither
     * may name an answer still in play: not the day's two, nor any practice game's.
     */
    @Test
    void neitherHomeNorTheBoardsNameAnAnswerInPlay() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        guessDaily(jen, farFrom(answerToday()));
        guessDaily(jen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        guessDaily(colleen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        long europe = newGame(jen, "country", "europe").get("id").asLong();
        long mainland = newGame(jen, "country", "mainland").get("id").asLong();
        long hard = newGame(jen, "country", "world", Variant.HARD).get("id").asLong();
        long capital = newGame(colleen, "capital", "world").get("id").asLong();
        List<String> inPlay = List.of(answerToday(), answerToday(Variant.HARD), answerOf(europe), answerOf(mainland),
                answerOf(hard), answerOf(capital));

        for (var who : List.of(jen, colleen)) {
            for (JsonNode body : List.of(home(who), board(who, Variant.CLASSIC), board(who, Variant.HARD))) {
                for (String code : inPlay) {
                    assertThat(body.toString()).doesNotContain("\"" + code + "\"");
                }
            }
        }
    }

    @Test
    void homeNeedsASignedInPlayer() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/home"))
                .andExpect(status().isUnauthorized());
    }
}

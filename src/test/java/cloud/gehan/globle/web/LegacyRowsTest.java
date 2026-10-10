package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production already has games from before Hard. Adding the variant column leaves it
 * empty on every one of them, and each must go on behaving as the classic game it was:
 * streaks, boards, resuming, the one-daily-a-day key. These tests play through the API
 * and then blank the column, which is exactly how those rows read after the upgrade.
 */
class LegacyRowsTest extends GlobleTestBase {

    @Test
    void oldDailyGamesAreClassicGames() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        for (int puzzle = 1; puzzle <= 3; puzzle++) {
            clock.setDay(day(puzzle));
            winDailyIn(jen, 2);
        }
        giveUpDaily(colleen);
        forgetVariants();
        assertThat(jdbc.queryForObject("select count(*) from globle_game where variant is null", Integer.class))
                .isEqualTo(4);

        JsonNode classic = board(jen, Variant.CLASSIC);
        assertThat(classic.get("variant").asText()).isEqualTo("classic");
        assertThat(classic.get("me").get("wins").asInt()).isEqualTo(3);
        assertThat(classic.get("me").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(classic.get("me").get("distribution").get("2").asInt()).isEqualTo(3);
        assertThat(classic.get("today").get("entries")).hasSize(2);
        assertThat(classic.get("allTime")).hasSize(2);
        assertThat(board(jen)).isEqualTo(classic);

        JsonNode hard = board(jen, Variant.HARD);
        assertThat(hard.get("today").get("entries")).isEmpty();
        assertThat(hard.get("allTime")).isEmpty();
        assertThat(hard.get("me").get("played").asInt()).isZero();

        JsonNode today = getOk("/globle/api/daily", jen);
        assertThat(today.get("status").asText()).isEqualTo("WON");
        assertThat(today.get("variant").asText()).isEqualTo("classic");
        assertThat(daily(jen, Variant.HARD).get("guesses")).isEmpty();

        JsonNode home = home(jen);
        assertThat(home.get("classic").get("status").asText()).isEqualTo("WON");
        assertThat(home.get("classic").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(home.get("hard").get("status").asText()).isEqualTo("NEW");
        assertThat(home.get("hard").get("currentStreak").asInt()).isZero();
    }

    /**
     * The classic daily key has the same form it always had, so an old row still stops
     * a second classic row for its day, while Hard gets a row of its own.
     */
    @Test
    void anOldDailyRowStillHoldsItsDay() throws Exception {
        var jen = player("jen");
        Player me = new Player(idOf("jen"), "jen");
        String guess = farFrom(answerToday());
        guessDaily(jen, guess);
        forgetVariants();

        JsonNode next = guessDaily(jen, farFrom(guess));
        assertThat(next.get("guessCount").asInt()).isEqualTo(2);
        assertThat(games.count()).isEqualTo(1);
        assertThatThrownBy(() -> games.saveAndFlush(GlobleGame.daily(me, DAY_ONE, clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);

        guessDaily(jen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        assertThat(games.count()).isEqualTo(2);
        assertThat(games.findDaily(me, DAY_ONE, Variant.CLASSIC).orElseThrow().getGuessCount()).isEqualTo(2);
    }

    /**
     * Before slots, a player had one game in progress per kind, in whatever region they
     * picked. It resumes by the old request (kind only) and by its slot.
     */
    @Test
    void anOldPracticeGameResumesByTheOldRequestAndByItsSlot() throws Exception {
        var jen = player("jen");
        long europe = newGame(jen, "country", "europe").get("id").asLong();
        guessUnlimited(jen, europe, farFrom(answerOf(europe)));
        long capitals = newGame(jen, "capital", "asia").get("id").asLong();
        forgetVariants();

        JsonNode old = getOk("/globle/api/unlimited/current?kind=country", jen);
        assertThat(old.get("id").asLong()).isEqualTo(europe);
        assertThat(old.get("variant").asText()).isEqualTo("classic");
        assertThat(old.get("guessCount").asInt()).isEqualTo(1);
        assertThat(old.get("answer").isNull()).isTrue();
        assertThat(getOk("/globle/api/unlimited/current?kind=country&region=europe&variant=classic", jen)
                .get("id").asLong()).isEqualTo(europe);
        assertThat(getOk("/globle/api/unlimited/current?kind=capital", jen).get("id").asLong()).isEqualTo(capitals);
        assertThat(getOk("/globle/api/unlimited/current?kind=capital&region=asia", jen).get("id").asLong())
                .isEqualTo(capitals);
        noGame(jen, "kind=country&region=europe&variant=hard");
        noGame(jen, "kind=country&region=world");

        JsonNode home = home(jen);
        assertThat(home.get("inProgress")).hasSize(2);
        assertThat(home.get("inProgress").get(1).get("id").asLong()).isEqualTo(europe);
        assertThat(home.get("inProgress").get(1).get("variant").asText()).isEqualTo("classic");
        assertThat(home.get("inProgress").get(1).get("region").asText()).isEqualTo("europe");

        // It can still be played, and starting its slot again abandons it as before.
        guessUnlimited(jen, europe, secondFarFrom(answerOf(europe)));
        long fresh = newGame(jen, "country", "europe", Variant.CLASSIC).get("id").asLong();
        GlobleGame abandoned = games.findById(europe).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(Status.GAVE_UP);
        assertThat(abandoned.getGuessCount()).isEqualTo(2);
        assertThat(getOk("/globle/api/unlimited/current?kind=country", jen).get("id").asLong()).isEqualTo(fresh);
        // The Asia capitals game was in another slot and is untouched.
        assertThat(games.findById(capitals).orElseThrow().isPlaying()).isTrue();
    }

    @Test
    void oldFinishedPracticeGamesCountOnTheClassicBoards() throws Exception {
        var jen = player("jen");
        long country = newGame(jen, "country", "europe").get("id").asLong();
        guessUnlimited(jen, country, answerOf(country));
        long capital = newGame(jen, "capital", "world").get("id").asLong();
        guessUnlimited(jen, capital, answerOf(capital));
        forgetVariants();
        long hard = newGame(jen, "country", "world", Variant.HARD).get("id").asLong();
        guessUnlimited(jen, hard, answerOf(hard));

        JsonNode unlimited = board(jen).get("unlimited");
        assertThat(unlimited.get("country")).hasSize(1);
        assertThat(unlimited.get("country").get(0).get("played").asInt()).isEqualTo(1);
        assertThat(unlimited.get("capital")).hasSize(1);
        assertThat(unlimited.get("hard")).hasSize(1);
        assertThat(unlimited.get("hard").get(0).get("best").asInt()).isEqualTo(1);
    }

    /** "Never the previous answer" reads old games too, in small Oceania where it matters. */
    @Test
    void aNewGameAvoidsTheAnswerOfAnOldGameInItsSlot() throws Exception {
        var jen = player("jen");
        String previous = null;
        for (int i = 0; i < 30; i++) {
            long id = newGame(jen, "country", "oceania").get("id").asLong();
            String answer = answerOf(id);
            assertThat(answer).as("game %d", i).isNotEqualTo(previous);
            postOk("/globle/api/unlimited/" + id + "/give-up", null, jen);
            forgetVariants();
            previous = answer;
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Another place that is neither the answer, a neighbour of it, nor {@link #farFrom}'s pick. */
    private String secondFarFrom(String answer) {
        String first = farFrom(answer);
        return catalog.all().stream()
                .map(c -> c.code())
                .filter(code -> !code.equals(answer) && !code.equals(first) && catalog.km(code, answer) > 0)
                .findFirst()
                .orElseThrow();
    }

    private void noGame(RequestPostProcessor who, String query) throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current?" + query).with(who))
                .andExpect(status().isNoContent());
    }
}

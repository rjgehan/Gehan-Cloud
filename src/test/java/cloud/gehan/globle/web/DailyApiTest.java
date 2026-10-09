package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.MovableClock;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DailyApiTest extends GlobleTestBase {

    @Test
    void lookingAtTodaysPuzzleStoresNothing() throws Exception {
        var jen = player("jen");
        JsonNode state = getOk("/globle/api/daily", jen);

        assertThat(state.get("id").isNull()).isTrue();
        assertThat(state.get("mode").asText()).isEqualTo("daily");
        assertThat(state.get("kind").asText()).isEqualTo("country");
        assertThat(state.get("region").asText()).isEqualTo("world");
        assertThat(state.get("puzzle").asInt()).isEqualTo(1);
        assertThat(state.get("date").asText()).isEqualTo("2026-10-09");
        assertThat(state.get("status").asText()).isEqualTo("PLAYING");
        assertThat(state.get("guesses")).isEmpty();
        assertThat(state.get("guessCount").asInt()).isZero();
        assertThat(state.get("closestKm").isNull()).isTrue();
        assertThat(state.get("answer").isNull()).isTrue();
        assertThat(state.get("nextPuzzleAt").asText()).isEqualTo("2026-10-10T00:00:00-04:00");

        assertThat(games.count()).isZero();
        assertThat(days.count()).isZero();
    }

    @Test
    void aWrongGuessIsScoredBorderToBorderAndTheAnswerStaysHidden() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        String guess = farFrom(answer);

        JsonNode state = guessDaily(jen, guess);

        assertThat(state.get("status").asText()).isEqualTo("PLAYING");
        assertThat(state.get("answer").isNull()).isTrue();
        assertThat(state.get("guessCount").asInt()).isEqualTo(1);
        JsonNode first = state.get("guesses").get(0);
        assertThat(first.get("code").asText()).isEqualTo(guess);
        assertThat(first.get("name").asText()).isEqualTo(catalog.require(guess).name());
        assertThat(first.get("km").asInt()).isEqualTo(catalog.km(guess, answer)).isPositive();
        assertThat(first.get("order").asInt()).isEqualTo(1);
        assertThat(first.get("capital").isNull()).isTrue();
        assertThat(state.get("closestKm").asInt()).isEqualTo(first.get("km").asInt());
    }

    @Test
    void guessesKeepTheirOrderAndTheClosestIsReported() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        var wrong = wrongGuesses(answer, 3);
        wrong.forEach(code -> {
            try {
                guessDaily(jen, code);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        });
        JsonNode state = getOk("/globle/api/daily", jen);

        assertThat(state.get("guesses")).extracting(g -> g.get("code").asText()).containsExactlyElementsOf(wrong);
        assertThat(state.get("guesses")).extracting(g -> g.get("order").asInt()).containsExactly(1, 2, 3);
        int closest = wrong.stream().mapToInt(code -> catalog.km(code, answer)).min().orElseThrow();
        assertThat(state.get("closestKm").asInt()).isEqualTo(closest);
    }

    @Test
    void theRightGuessWinsAndRevealsTheAnswer() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        guessDaily(jen, farFrom(answer));

        JsonNode state = guessDaily(jen, answer);

        assertThat(state.get("status").asText()).isEqualTo("WON");
        assertThat(state.get("guessCount").asInt()).isEqualTo(2);
        JsonNode winning = state.get("guesses").get(1);
        assertThat(winning.get("code").asText()).isEqualTo(answer);
        assertThat(winning.get("km").asInt()).isZero();
        assertThat(state.get("closestKm").asInt()).isZero();

        JsonNode revealed = state.get("answer");
        var country = catalog.require(answer);
        assertThat(revealed.get("code").asText()).isEqualTo(answer);
        assertThat(revealed.get("name").asText()).isEqualTo(country.name());
        assertThat(revealed.get("lat").asDouble()).isEqualTo(country.lat());
        assertThat(revealed.get("lng").asDouble()).isEqualTo(country.lng());
        assertThat(revealed.get("capital").get("name").asText()).isEqualTo(country.capital().name());

        GlobleGame stored = games.findAll().get(0);
        assertThat(stored.getStatus()).isEqualTo(Status.WON);
        assertThat(stored.getFinishedAt()).isNotNull();
    }

    @Test
    void aFinishedGameTakesNoMoreGuesses() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        guessDaily(jen, answer);

        post("/globle/api/daily/guess", Map.of("code", farFrom(answer)), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("finished"));
        post("/globle/api/daily/give-up", null, jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("finished"));
    }

    @Test
    void anUnknownCodeIsRefusedAndNothingIsStored() throws Exception {
        var jen = player("jen");
        post("/globle/api/daily/guess", Map.of("code", "XYZ"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"unknown-country\"}", true));
        assertThat(games.count()).isZero();
    }

    @Test
    void theSameGuessTwiceIsRefused() throws Exception {
        var jen = player("jen");
        String guess = farFrom(answerToday());
        guessDaily(jen, guess);

        post("/globle/api/daily/guess", Map.of("code", guess), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("already-guessed"));
        assertThat(getOk("/globle/api/daily", jen).get("guessCount").asInt()).isEqualTo(1);
    }

    @Test
    void codesAreForgivingAboutCaseAndSpaces() throws Exception {
        var jen = player("jen");
        String guess = farFrom(answerToday());
        JsonNode state = guessDaily(jen, "  " + guess.toLowerCase() + " ");
        assertThat(state.get("guesses").get(0).get("code").asText()).isEqualTo(guess);
    }

    @Test
    void malformedRequestsAreBadRequests() throws Exception {
        var jen = player("jen");
        post("/globle/api/daily/guess", Map.of("code", " "), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/daily/guess", Map.of(), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/daily/guess", null, jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/guess").with(jen).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();
    }

    @Test
    void givingUpRevealsTheAnswerAndCountsEvenWithoutAGuess() throws Exception {
        var jen = player("jen");
        String answer = answerToday();

        JsonNode state = giveUpDaily(jen);

        assertThat(state.get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(state.get("answer").get("code").asText()).isEqualTo(answer);
        assertThat(state.get("guessCount").asInt()).isZero();
        assertThat(games.count()).isEqualTo(1);
        assertThat(getOk("/globle/api/daily", jen).get("status").asText()).isEqualTo("GAVE_UP");
    }

    @Test
    void everybodyGetsTheSameCountry() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        guessDaily(jen, farFrom(answerToday()));
        String answer = days.findById(DAY_ONE.toString()).orElseThrow().getCountryCode();

        assertThat(guessDaily(colleen, answer).get("status").asText()).isEqualTo("WON");
        // And Jen's game is untouched by Colleen's.
        assertThat(getOk("/globle/api/daily", jen).get("status").asText()).isEqualTo("PLAYING");
    }

    /** The write lock is the first line; the schema is the backstop. */
    @Test
    void theDatabaseItselfRefusesASecondDailyRowForTheSameDay() {
        userService.create("jen", "USER");
        Player jen = new Player(users.findByUsername("jen").orElseThrow().getId(), "jen");
        games.saveAndFlush(GlobleGame.daily(jen, DAY_ONE, clock.instant()));

        assertThatThrownBy(() -> games.saveAndFlush(GlobleGame.daily(jen, DAY_ONE, clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);
        // Another day, or another player, is a different key.
        games.saveAndFlush(GlobleGame.daily(jen, day(2), clock.instant()));
        games.saveAndFlush(GlobleGame.daily(new Player(jen.id(), "ghost"), DAY_ONE, clock.instant()));
        assertThat(games.count()).isEqualTo(3);
    }

    @Test
    void aNewDayIsAFreshPuzzle() throws Exception {
        var jen = player("jen");
        guessDaily(jen, answerToday());

        clock.setDay(day(2));
        JsonNode tomorrow = getOk("/globle/api/daily", jen);

        assertThat(tomorrow.get("status").asText()).isEqualTo("PLAYING");
        assertThat(tomorrow.get("puzzle").asInt()).isEqualTo(2);
        assertThat(tomorrow.get("guesses")).isEmpty();
        assertThat(tomorrow.get("answer").isNull()).isTrue();
    }

    /**
     * A tab left open across midnight still shows yesterday's puzzle. Its next move
     * must not land on today's, which the player has not seen: a give-up would reveal
     * today's answer and end their streak.
     */
    @Test
    void aMoveMeantForYesterdaysPuzzleIsRefusedAfterMidnight() throws Exception {
        var jen = player("jen");
        Player me = new Player(users.findByUsername("jen").orElseThrow().getId(), "jen");
        clock.set(DAY_ONE.atTime(23, 59).atZone(MovableClock.PUZZLE_ZONE).toInstant());
        String guess = farFrom(answerToday());
        postOk("/globle/api/daily/guess", Map.of("code", guess, "date", "2026-10-09"), jen);

        clock.advance(Duration.ofMinutes(2));

        post("/globle/api/daily/give-up", Map.of("date", "2026-10-09"), jen)
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"new-day\"}", true));
        post("/globle/api/daily/guess", Map.of("code", farFrom(guess), "date", "2026-10-09"), jen)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("new-day"));

        // Nothing was stored for today, not even its answer, and yesterday is as it was.
        assertThat(games.findDaily(me, day(2))).isEmpty();
        assertThat(days.findById(day(2).toString())).isEmpty();
        GlobleGame yesterday = games.findDaily(me, DAY_ONE).orElseThrow();
        assertThat(yesterday.getStatus()).isEqualTo(Status.PLAYING);
        assertThat(yesterday.getGuessCount()).isEqualTo(1);

        // A move that names today, or names no date at all, plays today as usual.
        String todaysGuess = farFrom(answerToday());
        JsonNode today = postOk("/globle/api/daily/guess", Map.of("code", todaysGuess, "date", "2026-10-10"), jen);
        assertThat(today.get("date").asText()).isEqualTo("2026-10-10");
        assertThat(today.get("guessCount").asInt()).isEqualTo(1);
        JsonNode gaveUp = giveUpDaily(jen);
        assertThat(gaveUp.get("date").asText()).isEqualTo("2026-10-10");
        assertThat(gaveUp.get("status").asText()).isEqualTo("GAVE_UP");
    }

    @Test
    void aDailyMoveWithAnUnreadableDateIsABadRequest() throws Exception {
        var jen = player("jen");
        post("/globle/api/daily/give-up", Map.of("date", "yesterday"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/daily/guess", Map.of("code", "FRA", "date", "10/09/2026"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();
    }

    /** The answer's code must not appear anywhere in a response while the game is on. */
    @Test
    void theAnswerNeverLeaksWhilePlaying() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        String quoted = "\"" + answer + "\"";

        String looked = mvc.perform(MockMvcRequestBuilders.get("/globle/api/daily").with(jen))
                .andReturn().getResponse().getContentAsString();
        assertThat(looked).doesNotContain(quoted);

        for (String wrong : wrongGuesses(answer, 5)) {
            String body = post("/globle/api/daily/guess", Map.of("code", wrong), jen)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(quoted).contains("\"answer\":null");
        }
    }
}

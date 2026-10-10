package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Status;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnlimitedApiTest extends GlobleTestBase {

    @Test
    void withNoGameInProgressThereIsNothingToResume() throws Exception {
        var jen = player("jen");
        for (String kind : new String[]{"country", "capital"}) {
            mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current").param("kind", kind).with(jen))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Test
    void aNewGameStartsHiddenAndResumesFromAnyDevice() throws Exception {
        var jen = player("jen");
        JsonNode started = newGame(jen, "country", "world");

        assertThat(started.get("id").asLong()).isPositive();
        assertThat(started.get("mode").asText()).isEqualTo("unlimited");
        assertThat(started.get("kind").asText()).isEqualTo("country");
        assertThat(started.get("region").asText()).isEqualTo("world");
        assertThat(started.get("puzzle").isNull()).isTrue();
        assertThat(started.get("date").isNull()).isTrue();
        assertThat(started.get("nextPuzzleAt").isNull()).isTrue();
        assertThat(started.get("status").asText()).isEqualTo("PLAYING");
        assertThat(started.get("guesses")).isEmpty();
        assertThat(started.get("answer").isNull()).isTrue();

        long id = started.get("id").asLong();
        String guess = farFrom(answerOf(id));
        guessUnlimited(jen, id, guess);

        JsonNode resumed = getOk("/globle/api/unlimited/current?kind=country", jen);
        assertThat(resumed.get("id").asLong()).isEqualTo(id);
        assertThat(resumed.get("guesses").get(0).get("code").asText()).isEqualTo(guess);
        assertThat(resumed.get("guesses").get(0).get("km").asInt()).isEqualTo(catalog.km(guess, answerOf(id)));
        assertThat(resumed.get("answer").isNull()).isTrue();
    }

    /** Only a game in the same slot is abandoned; see UnlimitedSlotsTest for the others. */
    @Test
    void startingAgainAbandonsTheGameInProgressAsAGiveUp() throws Exception {
        var jen = player("jen");
        long first = newGame(jen, "country", "europe").get("id").asLong();
        guessUnlimited(jen, first, farFrom(answerOf(first)));

        long second = newGame(jen, "country", "europe").get("id").asLong();

        GlobleGame abandoned = games.findById(first).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(Status.GAVE_UP);
        assertThat(abandoned.getFinishedAt()).isNotNull();
        assertThat(getOk("/globle/api/unlimited/current?kind=country", jen).get("id").asLong()).isEqualTo(second);
        post("/globle/api/unlimited/" + first + "/guess", Map.of("code", "FRA"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("finished"));
        assertThat(games.findAll().stream().filter(GlobleGame::isPlaying)).hasSize(1);
    }

    /**
     * A game with no guesses has shown nothing about its answer, so dropping it dodges
     * nothing: tapping "New game" twice must not add a loss.
     */
    @Test
    void startingAgainBeforeAnyGuessDropsTheUntouchedGame() throws Exception {
        var jen = player("jen");
        long first = newGame(jen, "country", "world").get("id").asLong();

        long second = newGame(jen, "country", "world").get("id").asLong();

        // Compared by contents rather than id: SQLite may hand the freed id out again.
        assertThat(games.findAll()).extracting(GlobleGame::getId).containsExactly(second);
        assertThat(games.findById(second).orElseThrow().isPlaying()).isTrue();
        if (first != second) {
            assertThat(games.findById(first)).isEmpty();
        }
        assertThat(getOk("/globle/api/unlimited/current?kind=country", jen).get("id").asLong()).isEqualTo(second);

        for (int i = 0; i < 4; i++) {
            newGame(jen, "country", "world");
        }
        assertThat(board(jen).get("unlimited").get("country")).isEmpty();
        assertThat(games.count()).isEqualTo(1);
    }

    /** Small regions made back-to-back repeats common: Oceania dealt one about every 14 games. */
    @Test
    void aNewGameNeverDealsThePreviousAnswerAgain() throws Exception {
        var jen = player("jen");
        for (String kind : new String[]{"country", "capital"}) {
            String previous = null;
            for (int i = 0; i < 40; i++) {
                long id = newGame(jen, kind, "oceania").get("id").asLong();
                String answer = answerOf(id);
                assertThat(answer).as("%s game %d", kind, i).isNotEqualTo(previous);
                postOk("/globle/api/unlimited/" + id + "/give-up", null, jen);
                previous = answer;
            }
        }
    }

    @Test
    void countryAndCapitalGamesAreIndependent() throws Exception {
        var jen = player("jen");
        long country = newGame(jen, "country", "world").get("id").asLong();
        long capital = newGame(jen, "capital", "world").get("id").asLong();

        assertThat(games.findById(country).orElseThrow().isPlaying()).isTrue();
        assertThat(getOk("/globle/api/unlimited/current?kind=country", jen).get("id").asLong()).isEqualTo(country);
        assertThat(getOk("/globle/api/unlimited/current?kind=capital", jen).get("id").asLong()).isEqualTo(capital);
    }

    @Test
    void winningRevealsTheAnswer() throws Exception {
        var jen = player("jen");
        long id = newGame(jen, "country", "world").get("id").asLong();
        String answer = answerOf(id);

        JsonNode won = guessUnlimited(jen, id, answer);

        assertThat(won.get("status").asText()).isEqualTo("WON");
        assertThat(won.get("answer").get("code").asText()).isEqualTo(answer);
        assertThat(won.get("guesses").get(0).get("km").asInt()).isZero();
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current").param("kind", "country").with(jen))
                .andExpect(status().isNoContent());
    }

    @Test
    void givingUpRevealsTheAnswer() throws Exception {
        var jen = player("jen");
        long id = newGame(jen, "country", "world").get("id").asLong();

        JsonNode gaveUp = postOk("/globle/api/unlimited/" + id + "/give-up", null, jen);

        assertThat(gaveUp.get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(gaveUp.get("answer").get("code").asText()).isEqualTo(answerOf(id));
        post("/globle/api/unlimited/" + id + "/give-up", null, jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("finished"));
    }

    @Test
    void theRegionFilterDecidesWhereTheAnswerIs() throws Exception {
        var jen = player("jen");
        for (String region : new String[]{"africa", "americas", "asia", "europe", "oceania"}) {
            for (int i = 0; i < 6; i++) {
                JsonNode started = newGame(jen, "country", region);
                assertThat(started.get("region").asText()).isEqualTo(region);
                long id = started.get("id").asLong();
                JsonNode revealed = postOk("/globle/api/unlimited/" + id + "/give-up", null, jen).get("answer");
                var answer = catalog.require(revealed.get("code").asText());
                assertThat(answer.region()).as("%s game drew %s", region, answer.code()).isEqualTo(region);
                assertThat(answer.answer()).isTrue();
            }
        }
    }

    @Test
    void someoneElsesGameIsNotFound() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        long jens = newGame(jen, "country", "world").get("id").asLong();

        post("/globle/api/unlimited/" + jens + "/guess", Map.of("code", "FRA"), colleen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        post("/globle/api/unlimited/" + jens + "/give-up", null, colleen)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not-found"));
        post("/globle/api/unlimited/999999/guess", Map.of("code", "FRA"), jen)
                .andExpect(status().isNotFound());

        assertThat(games.findById(jens).orElseThrow().getGuessCount()).isZero();
        assertThat(games.findById(jens).orElseThrow().isPlaying()).isTrue();
    }

    /** A daily row has an id too, but it is never an unlimited game. */
    @Test
    void aDailyGameCannotBePlayedThroughTheUnlimitedRoutes() throws Exception {
        var jen = player("jen");
        guessDaily(jen, farFrom(answerToday()));
        long dailyId = games.findAll().stream().filter(g -> g.getMode() == Mode.DAILY)
                .findFirst().orElseThrow().getId();

        post("/globle/api/unlimited/" + dailyId + "/give-up", null, jen)
                .andExpect(status().isNotFound());
        assertThat(getOk("/globle/api/daily", jen).get("status").asText()).isEqualTo("PLAYING");
    }

    @Test
    void unknownKindsAndRegionsAreBadRequests() throws Exception {
        var jen = player("jen");
        post("/globle/api/unlimited/new", Map.of("kind", "planet", "region", "world"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/unlimited/new", Map.of("kind", "country", "region", "antarctica"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/unlimited/new", Map.of("kind", "country", "region", "mars"), jen)
                .andExpect(status().isBadRequest());
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current").param("kind", "planet").with(jen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/unlimited/not-a-number/guess", Map.of("code", "FRA"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();
    }

    @Test
    void anEmptyNewGameRequestMeansAWholeWorldCountryGame() throws Exception {
        var jen = player("jen");
        JsonNode started = postOk("/globle/api/unlimited/new", Map.of(), jen);
        assertThat(started.get("kind").asText()).isEqualTo("country");
        assertThat(started.get("region").asText()).isEqualTo("world");
    }

    @Test
    void unlimitedGamesDoNotTouchTheDaily() throws Exception {
        var jen = player("jen");
        long id = newGame(jen, "country", "world").get("id").asLong();
        guessUnlimited(jen, id, answerOf(id));

        JsonNode daily = getOk("/globle/api/daily", jen);
        assertThat(daily.get("guesses")).isEmpty();
        assertThat(days.count()).isZero();
    }

    @Test
    void theAnswerNeverLeaksWhilePlaying() throws Exception {
        var jen = player("jen");
        for (String kind : new String[]{"country", "capital"}) {
            String started = post("/globle/api/unlimited/new", Map.of("kind", kind, "region", "world"), jen)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            long id = json.readTree(started).get("id").asLong();
            String quoted = "\"" + answerOf(id) + "\"";
            assertThat(started).doesNotContain(quoted).contains("\"answer\":null");

            for (String wrong : wrongGuesses(answerOf(id), 4)) {
                if (!catalog.require(wrong).hasCapital()) {
                    continue;
                }
                String body = post("/globle/api/unlimited/" + id + "/guess", Map.of("code", wrong), jen)
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                assertThat(body).doesNotContain(quoted).contains("\"answer\":null");
            }
            String current = mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current")
                            .param("kind", kind).with(jen))
                    .andReturn().getResponse().getContentAsString();
            assertThat(current).doesNotContain(quoted).contains("\"answer\":null");
        }
    }
}

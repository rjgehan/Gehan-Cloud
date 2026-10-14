package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.MovableClock;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The second daily: the same rules as classic, its own answer and its own row. */
class HardDailyApiTest extends GlobleTestBase {

    @Test
    void lookingAtTheHardDailyStoresNothing() throws Exception {
        var jen = player("jen");
        JsonNode state = daily(jen, Variant.HARD);

        assertThat(state.get("id").isNull()).isTrue();
        assertThat(state.get("mode").asText()).isEqualTo("daily");
        assertThat(state.get("variant").asText()).isEqualTo("hard");
        assertThat(state.get("kind").asText()).isEqualTo("country");
        assertThat(state.get("region").asText()).isEqualTo("world");
        assertThat(state.get("puzzle").asInt()).isEqualTo(1);
        assertThat(state.get("date").asText()).isEqualTo("2026-10-09");
        assertThat(state.get("status").asText()).isEqualTo("PLAYING");
        assertThat(state.get("guesses")).isEmpty();
        assertThat(state.get("answer").isNull()).isTrue();
        assertThat(state.get("nextPuzzleAt").asText()).isEqualTo("2026-10-10T00:00:00-04:00");

        assertThat(games.count()).isZero();
        assertThat(days.count()).isZero();
        assertThat(hardDays.count()).isZero();
    }

    /** A request that names no variant is the classic daily, as it always was. */
    @Test
    void theDailyWithoutAVariantIsClassic() throws Exception {
        var jen = player("jen");
        assertThat(getOk("/globle/api/daily", jen).get("variant").asText()).isEqualTo("classic");
        assertThat(daily(jen, Variant.CLASSIC).get("variant").asText()).isEqualTo("classic");
        JsonNode guessed = guessDaily(jen, farFrom(answerToday()));
        assertThat(guessed.get("variant").asText()).isEqualTo("classic");
        assertThat(games.findAll()).singleElement().extracting(GlobleGame::getVariant).isEqualTo(Variant.CLASSIC);
    }

    @Test
    void everybodyGetsTheSameHardCountryAndItIsNotTheClassicOne() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        guessDaily(jen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        String hard = hardDays.findById(DAY_ONE.toString()).orElseThrow().getCountryCode();

        JsonNode won = guessDaily(colleen, hard, Variant.HARD);

        assertThat(won.get("status").asText()).isEqualTo("WON");
        assertThat(won.get("variant").asText()).isEqualTo("hard");
        assertThat(won.get("answer").get("code").asText()).isEqualTo(hard);
        assertThat(hard).isNotEqualTo(answerToday(Variant.CLASSIC));
        assertThat(catalog.require(hard).difficulty()).isGreaterThan(1);
        assertThat(daily(jen, Variant.HARD).get("status").asText()).isEqualTo("PLAYING");
    }

    @Test
    void classicAndHardAreSeparateGamesOnTheSameDay() throws Exception {
        var jen = player("jen");
        Player me = new Player(idOf("jen"), "jen");
        winDailyIn(jen, 2);

        JsonNode hard = daily(jen, Variant.HARD);
        assertThat(hard.get("status").asText()).isEqualTo("PLAYING");
        assertThat(hard.get("guesses")).isEmpty();

        JsonNode gaveUp = giveUpDaily(jen, Variant.HARD);
        assertThat(gaveUp.get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(gaveUp.get("answer").get("code").asText()).isEqualTo(answerToday(Variant.HARD));
        assertThat(getOk("/globle/api/daily", jen).get("status").asText()).isEqualTo("WON");

        GlobleGame classicRow = games.findDaily(me, DAY_ONE, Variant.CLASSIC).orElseThrow();
        GlobleGame hardRow = games.findDaily(me, DAY_ONE, Variant.HARD).orElseThrow();
        assertThat(games.count()).isEqualTo(2);
        assertThat(classicRow.getGuessCount()).isEqualTo(2);
        assertThat(hardRow.getGuessCount()).isZero();
        // The classic key is exactly the one rows have always had; Hard's is its own.
        assertThat(jdbc.queryForObject("select daily_key from globle_game where id = ?", String.class,
                classicRow.getId())).isEqualTo(me.id() + "/jen/2026-10-09");
        assertThat(jdbc.queryForObject("select daily_key from globle_game where id = ?", String.class,
                hardRow.getId())).isEqualTo(me.id() + "/jen/2026-10-09/HARD");
    }

    @Test
    void theDatabaseAllowsOneDailyRowPerVariantAndNoMore() {
        userService.create("jen", "USER");
        Player jen = new Player(idOf("jen"), "jen");
        games.saveAndFlush(GlobleGame.daily(jen, DAY_ONE, Variant.CLASSIC, clock.instant()));
        games.saveAndFlush(GlobleGame.daily(jen, DAY_ONE, Variant.HARD, clock.instant()));

        assertThatThrownBy(() -> games.saveAndFlush(GlobleGame.daily(jen, DAY_ONE, Variant.HARD, clock.instant())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(games.count()).isEqualTo(2);
    }

    @Test
    void theHardAnswerNeverLeaksWhilePlaying() throws Exception {
        var jen = player("jen");
        String answer = answerToday(Variant.HARD);
        String quoted = "\"" + answer + "\"";

        String looked = mvc.perform(MockMvcRequestBuilders.get("/globle/api/daily").param("variant", "hard")
                        .with(jen))
                .andReturn().getResponse().getContentAsString();
        assertThat(looked).doesNotContain(quoted);

        for (String wrong : wrongGuesses(answer, 5)) {
            String body = post("/globle/api/daily/guess", Map.of("code", wrong, "variant", "hard"), jen)
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(quoted).contains("\"answer\":null");
        }
        for (String url : new String[]{"/globle/api/home", "/globle/api/leaderboard?variant=hard"}) {
            String body = mvc.perform(MockMvcRequestBuilders.get(url).with(jen))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(url).doesNotContain(quoted);
        }
    }

    /** As for classic: a Hard move meant for yesterday's puzzle does not land on today's. */
    @Test
    void aHardMoveMeantForYesterdaysPuzzleIsRefusedAfterMidnight() throws Exception {
        var jen = player("jen");
        Player me = new Player(idOf("jen"), "jen");
        clock.set(DAY_ONE.atTime(23, 59).atZone(MovableClock.PUZZLE_ZONE).toInstant());
        String guess = farFrom(answerToday(Variant.HARD));
        postOk("/globle/api/daily/guess", Map.of("code", guess, "date", "2026-10-09", "variant", "hard"), jen);

        clock.advance(Duration.ofMinutes(2));

        post("/globle/api/daily/give-up", Map.of("date", "2026-10-09", "variant", "hard"), jen)
                .andExpect(status().isConflict())
                .andExpect(content().json("{\"error\":\"new-day\"}", true));
        post("/globle/api/daily/guess", Map.of("code", farFrom(guess), "date", "2026-10-09", "variant", "hard"), jen)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("new-day"));

        // Nothing stored for today in either table; yesterday's Hard game is as it was.
        assertThat(games.findDaily(me, day(2), Variant.HARD)).isEmpty();
        assertThat(hardDays.findById(day(2).toString())).isEmpty();
        assertThat(days.findById(day(2).toString())).isEmpty();
        GlobleGame yesterday = games.findDaily(me, DAY_ONE, Variant.HARD).orElseThrow();
        assertThat(yesterday.getStatus()).isEqualTo(Status.PLAYING);
        assertThat(yesterday.getGuessCount()).isEqualTo(1);

        JsonNode today = postOk("/globle/api/daily/guess",
                Map.of("code", farFrom(answerToday(Variant.HARD)), "date", "2026-10-10", "variant", "hard"), jen);
        assertThat(today.get("date").asText()).isEqualTo("2026-10-10");
        assertThat(today.get("puzzle").asInt()).isEqualTo(2);
    }

    @Test
    void unknownVariantsAreBadRequests() throws Exception {
        var jen = player("jen");
        for (String url : new String[]{"/globle/api/daily?variant=extreme", "/globle/api/leaderboard?variant=extreme",
                "/globle/api/unlimited/current?kind=country&variant=extreme"}) {
            mvc.perform(MockMvcRequestBuilders.get(url).with(jen))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        }
        post("/globle/api/daily/guess", Map.of("code", "FRA", "variant", "extreme"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/daily/give-up", Map.of("variant", "extreme"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/unlimited/new", Map.of("kind", "country", "variant", "extreme"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();
        assertThat(days.count()).isZero();
        assertThat(hardDays.count()).isZero();
    }

    /** Difficulty would be a clue in Hard; the mainland flag says nothing the page needs. */
    @Test
    void theCountryListKeepsDifficultyAndIslandsToTheServer() throws Exception {
        String body = mvc.perform(MockMvcRequestBuilders.get("/globle/api/countries").with(player("jen")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("difficulty").doesNotContain("smallIsland").doesNotContain("\"answer\"");
        JsonNode first = json.readTree(body).get(0);
        List<String> fields = new ArrayList<>();
        first.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly(
                "code", "name", "nameEs", "iso2", "aliases", "aliasesEs", "region", "canBeAnswer", "capital");
    }

    @Test
    void hardDailyRowsAreDailyCountryWorldGames() throws Exception {
        var jen = player("jen");
        guessDaily(jen, farFrom(answerToday(Variant.HARD)), Variant.HARD);
        GlobleGame row = games.findAll().get(0);
        assertThat(row.getMode()).isEqualTo(Mode.DAILY);
        assertThat(row.getVariant()).isEqualTo(Variant.HARD);
        assertThat(row.getPuzzleDate()).isEqualTo(DAY_ONE);
        assertThat(row.getAnswerCode()).isNull();
    }
}

package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Practice games live in slots, (kind, region, variant): every preset on the home
 * screen keeps its own game in progress and resumes on its own.
 */
class UnlimitedSlotsTest extends GlobleTestBase {

    @Test
    void everySlotResumesItsOwnGame() throws Exception {
        var jen = player("jen");
        long world = started(jen, "country", "world", Variant.CLASSIC);
        long europe = started(jen, "country", "europe", Variant.CLASSIC);
        long mainland = started(jen, "country", "mainland", Variant.CLASSIC);
        long capitals = started(jen, "capital", "world", Variant.CLASSIC);
        long hard = started(jen, "country", "world", Variant.HARD);
        for (long id : new long[]{world, europe, mainland, capitals, hard}) {
            guessUnlimited(jen, id, wrongWithCapital(answerOf(id)));
        }

        assertThat(current(jen, "kind=country&region=world&variant=classic").get("id").asLong()).isEqualTo(world);
        assertThat(current(jen, "kind=country&region=europe&variant=classic").get("id").asLong()).isEqualTo(europe);
        assertThat(current(jen, "kind=country&region=mainland").get("id").asLong()).isEqualTo(mainland);
        assertThat(current(jen, "kind=capital&region=world").get("id").asLong()).isEqualTo(capitals);
        JsonNode resumedHard = current(jen, "kind=country&region=world&variant=hard");
        assertThat(resumedHard.get("id").asLong()).isEqualTo(hard);
        assertThat(resumedHard.get("variant").asText()).isEqualTo("hard");
        assertThat(resumedHard.get("guessCount").asInt()).isEqualTo(1);
        assertThat(resumedHard.get("answer").isNull()).isTrue();
        assertThat(games.findAll()).allMatch(GlobleGame::isPlaying);

        // Without a region: the newest game of the kind and variant, wherever it is.
        assertThat(current(jen, "kind=country").get("id").asLong()).isEqualTo(mainland);
        assertThat(current(jen, "kind=country&variant=hard").get("id").asLong()).isEqualTo(hard);
        assertThat(current(jen, "kind=capital").get("id").asLong()).isEqualTo(capitals);
        // An empty slot has nothing to resume.
        noGame(jen, "kind=country&region=asia&variant=classic");
        noGame(jen, "kind=country&region=europe&variant=hard");
    }

    @Test
    void startingAgainOnlyAbandonsTheGameInTheSameSlot() throws Exception {
        var jen = player("jen");
        long europe = started(jen, "country", "europe", Variant.CLASSIC);
        guessUnlimited(jen, europe, farFrom(answerOf(europe)));
        long world = started(jen, "country", "world", Variant.CLASSIC);
        guessUnlimited(jen, world, farFrom(answerOf(world)));
        long hard = started(jen, "country", "world", Variant.HARD);

        long europeAgain = started(jen, "country", "europe", Variant.CLASSIC);

        assertThat(games.findById(europe).orElseThrow().getStatus()).isEqualTo(Status.GAVE_UP);
        assertThat(games.findById(world).orElseThrow().isPlaying()).isTrue();
        assertThat(games.findById(hard).orElseThrow().isPlaying()).isTrue();
        assertThat(current(jen, "kind=country&region=europe").get("id").asLong()).isEqualTo(europeAgain);

        // A Hard game with no guesses is dropped by a new Hard game, and only that.
        long hardAgain = started(jen, "country", "world", Variant.HARD);
        assertThat(games.findAll().stream().filter(g -> g.getVariant() == Variant.HARD))
                .extracting(GlobleGame::getId).containsExactly(hardAgain);
        assertThat(games.findById(world).orElseThrow().isPlaying()).isTrue();
        assertThat(games.findById(europeAgain).orElseThrow().isPlaying()).isTrue();
        assertThat(games.findAll().stream().filter(GlobleGame::isPlaying)).hasSize(3);
        assertThat(games.count()).isEqualTo(4);
    }

    @Test
    void mainlandNeverDealsASmallIslandNation() throws Exception {
        var jen = player("jen");
        for (int i = 0; i < 40; i++) {
            JsonNode started = newGame(jen, "country", "mainland");
            assertThat(started.get("region").asText()).isEqualTo("mainland");
            assertThat(started.get("variant").asText()).isEqualTo("classic");
            long id = started.get("id").asLong();
            Country answer = catalog.require(giveUp(jen, id).get("answer").get("code").asText());
            assertThat(answer.smallIsland()).as("mainland drew %s", answer.code()).isFalse();
            assertThat(answer.answer()).isTrue();
        }
        JsonNode capitals = newGame(jen, "capital", "mainland");
        assertThat(catalog.require(answerOf(capitals.get("id").asLong())).smallIsland()).isFalse();
    }

    @Test
    void hardPracticeDealsFromTheHardPool() throws Exception {
        var jen = player("jen");
        for (int i = 0; i < 40; i++) {
            JsonNode started = newGame(jen, "country", "world", Variant.HARD);
            assertThat(started.get("variant").asText()).isEqualTo("hard");
            assertThat(started.get("kind").asText()).isEqualTo("country");
            long id = started.get("id").asLong();
            Country answer = catalog.require(giveUp(jen, id).get("answer").get("code").asText());
            assertThat(answer.difficulty()).as("hard practice drew %s", answer.code()).isGreaterThan(1);
        }
    }

    /** The UI only offers Hard for the world, but the region filter still applies. */
    @Test
    void hardPracticeCanBeConfinedToARegion() throws Exception {
        var jen = player("jen");
        for (String region : new String[]{"europe", "mainland"}) {
            for (int i = 0; i < 10; i++) {
                JsonNode started = newGame(jen, "country", region, Variant.HARD);
                assertThat(started.get("region").asText()).isEqualTo(region);
                Country answer = catalog.require(answerOf(started.get("id").asLong()));
                assertThat(answer.difficulty()).isGreaterThan(1);
                if (region.equals("europe")) {
                    assertThat(answer.region()).isEqualTo("europe");
                } else {
                    assertThat(answer.smallIsland()).isFalse();
                }
            }
        }
    }

    @Test
    void thereIsNoHardCapitals() throws Exception {
        var jen = player("jen");
        post("/globle/api/unlimited/new", Map.of("kind", "capital", "region", "world", "variant", "hard"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current?kind=capital&variant=hard").with(jen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();
    }

    @Test
    void anUnknownRegionIsABadRequestWhenResumingToo() throws Exception {
        var jen = player("jen");
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current?kind=country&region=mars").with(jen))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
    }

    @Test
    void theAnswerNeverLeaksInHardPractice() throws Exception {
        var jen = player("jen");
        String started = post("/globle/api/unlimited/new", Map.of("kind", "country", "variant", "hard"), jen)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(started).get("id").asLong();
        String quoted = "\"" + answerOf(id) + "\"";
        assertThat(started).doesNotContain(quoted).contains("\"answer\":null");
        for (String wrong : wrongGuesses(answerOf(id), 4)) {
            String body = post("/globle/api/unlimited/" + id + "/guess", Map.of("code", wrong), jen)
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(quoted).contains("\"answer\":null");
        }
        for (String url : new String[]{"/globle/api/unlimited/current?kind=country&variant=hard",
                "/globle/api/home"}) {
            String body = mvc.perform(MockMvcRequestBuilders.get(url).with(jen))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(url).doesNotContain(quoted);
        }
    }

    // ------------------------------------------------------------------ helpers

    private long started(RequestPostProcessor who, String kind, String region, Variant variant) throws Exception {
        return newGame(who, kind, region, variant).get("id").asLong();
    }

    private JsonNode current(RequestPostProcessor who, String query) throws Exception {
        return getOk("/globle/api/unlimited/current?" + query, who);
    }

    private void noGame(RequestPostProcessor who, String query) throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current?" + query).with(who))
                .andExpect(status().isNoContent());
    }

    /** A wrong guess any game accepts, capitals included. */
    private String wrongWithCapital(String answer) {
        return catalog.all().stream()
                .filter(c -> c.hasCapital() && !c.code().equals(answer))
                .map(Country::code)
                .findFirst()
                .orElseThrow();
    }

    private JsonNode giveUp(RequestPostProcessor who, long id) throws Exception {
        return postOk("/globle/api/unlimited/" + id + "/give-up", null, who);
    }
}

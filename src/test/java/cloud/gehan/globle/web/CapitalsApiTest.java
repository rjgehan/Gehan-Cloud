package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CapitalsApiTest extends GlobleTestBase {

    @Test
    void guessesAreMeasuredCapitalToCapitalAndCarryTheCity() throws Exception {
        var jen = player("jen");
        JsonNode started = newGame(jen, "capital", "world");
        assertThat(started.get("kind").asText()).isEqualTo("capital");
        long id = started.get("id").asLong();
        String answer = answerOf(id);
        String guess = answer.equals("FRA") ? "ESP" : "FRA";

        JsonNode state = guessUnlimited(jen, id, guess);

        JsonNode first = state.get("guesses").get(0);
        Country.Capital capital = catalog.require(guess).capital();
        assertThat(first.get("km").asInt()).isEqualTo(catalog.capitalKm(guess, answer));
        assertThat(first.get("capital").get("name").asText()).isEqualTo(capital.name());
        assertThat(first.get("capital").get("lat").asDouble()).isEqualTo(capital.lat());
        assertThat(first.get("capital").get("lng").asDouble()).isEqualTo(capital.lng());
        assertThat(state.get("answer").isNull()).isTrue();
    }

    /**
     * Neighbours are 0 km apart as countries, but their capitals are not. The answer is
     * drawn at random and about one in five has no neighbour with a capital (islands,
     * mostly), so this deals again until it gets one: starting over before guessing
     * costs nothing. Fifty islands in a row is a 1-in-1e35 chance.
     */
    @Test
    void aNeighbourIsNotZeroKilometresAwayInCapitals() throws Exception {
        var jen = player("jen");
        long id = -1;
        String neighbour = null;
        for (int tries = 0; neighbour == null; tries++) {
            assertThat(tries).as("draws without a bordered capital answer").isLessThan(50);
            id = newGame(jen, "capital", "world").get("id").asLong();
            neighbour = borderingCapital(answerOf(id));
        }
        JsonNode state = guessUnlimited(jen, id, neighbour);
        assertThat(state.get("guesses").get(0).get("km").asInt()).isPositive();
        assertThat(state.get("status").asText()).isEqualTo("PLAYING");
    }

    /** A place with a capital that shares a border with {@code answer}, if there is one. */
    private String borderingCapital(String answer) {
        return catalog.all().stream()
                .map(Country::code)
                .filter(code -> !code.equals(answer) && catalog.km(code, answer) == 0
                        && catalog.require(code).hasCapital())
                .findFirst()
                .orElse(null);
    }

    @Test
    void aPlaceWithNoCapitalCannotBeGuessed() throws Exception {
        var jen = player("jen");
        long id = newGame(jen, "capital", "world").get("id").asLong();

        post("/globle/api/unlimited/" + id + "/guess", Map.of("code", "ATA"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("no-capital"));
        assertThat(games.findById(id).orElseThrow().getGuessCount()).isZero();
    }

    @Test
    void winningRevealsTheCapital() throws Exception {
        var jen = player("jen");
        long id = newGame(jen, "capital", "europe").get("id").asLong();
        String answer = answerOf(id);

        JsonNode won = guessUnlimited(jen, id, answer);

        assertThat(won.get("status").asText()).isEqualTo("WON");
        assertThat(won.get("guesses").get(0).get("km").asInt()).isZero();
        assertThat(won.get("answer").get("capital").get("name").asText())
                .isEqualTo(catalog.require(answer).capital().name());
        assertThat(catalog.require(answer).region()).isEqualTo("europe");
    }

    @Test
    void theCountryListCarriesCapitalsAndMarksPlacesThatCanNeverBeTheAnswer() throws Exception {
        var jen = player("jen");
        String body = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/globle/api/countries").with(jen))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode list = json.readTree(body);

        assertThat(list).hasSize(catalog.all().size());
        assertThat(body).doesNotContain("\"answer\"");
        JsonNode france = null;
        for (JsonNode c : list) {
            if (c.get("code").asText().equals("FRA")) {
                france = c;
            }
        }
        assertThat(france).isNotNull();
        assertThat(france.get("name").asText()).isEqualTo("France");
        assertThat(france.get("iso2").asText()).isEqualTo("FR");
        assertThat(france.get("region").asText()).isEqualTo("europe");
        assertThat(france.get("aliases").isArray()).isTrue();
        assertThat(france.get("capital").get("name").asText()).isEqualTo("Paris");
        assertThat(france.get("capital").get("aliases").isArray()).isTrue();
        assertThat(france.get("capital").get("lat").asDouble()).isBetween(48.0, 49.0);
        assertThat(france.get("canBeAnswer").asBoolean()).isTrue();

        // A territory is guessable, and the list says it can never be the mystery.
        int answers = 0;
        boolean greenland = false;
        for (JsonNode c : list) {
            assertThat(c.get("canBeAnswer").isBoolean()).isTrue();
            assertThat(c.get("canBeAnswer").asBoolean()).isEqualTo(catalog.require(c.get("code").asText()).answer());
            if (c.get("canBeAnswer").asBoolean()) {
                answers++;
            }
            if (c.get("code").asText().equals("GRL")) {
                assertThat(c.get("canBeAnswer").asBoolean()).isFalse();
                greenland = true;
            }
        }
        assertThat(greenland).isTrue();
        assertThat(answers).isEqualTo(197);

        JsonNode antarctica = null;
        for (JsonNode c : list) {
            if (c.get("code").asText().equals("ATA")) {
                antarctica = c;
            }
        }
        assertThat(antarctica).isNotNull();
        assertThat(antarctica.get("capital").isNull()).isTrue();
        assertThat(list.get(0).get("name").asText()).isEqualTo("Afghanistan");
    }
}

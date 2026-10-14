package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.GreatCircle;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /globle/api/rollcall/study: "Study what you missed" after a "Name them all" run. */
class RollCallStudyApiTest extends GlobleTestBase {

    private static final List<String> FIELDS = List.of("runId", "region", "finishedAt", "namedCount", "total",
            "missedCount", "missed", "order");
    private static final List<String> MISSED_FIELDS = List.of("code", "region", "lat", "lng", "nearby",
            "nearestNamed", "namedRate", "sampleRuns", "yourRuns", "yourMisses", "familiarity");

    // ------------------------------------------------------------------ which run

    @Test
    void withNoFinishedRunThereIsNothingToStudy() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        noStudy(jen, "world");

        // A run in progress is not studied, ...
        long europe = startRollCall(jen, "europe", "off").get("id").asLong();
        nameIn(jen, europe, "FRA");
        noStudy(jen, "europe");
        // ... a finished one is studied in its own region only, ...
        finishRollCall(jen, europe);
        assertThat(study(jen, "europe").get("runId").asLong()).isEqualTo(europe);
        noStudy(jen, "world");
        noStudy(jen, "mainland");
        // ... and by its own player only.
        noStudy(colleen, "europe");
    }

    @Test
    void theNewestFinishedRunIsStudiedWhetherItCountsOrNot() throws Exception {
        var jen = player("jen");
        long first = play(jen, "oceania", "off", List.of("AUS", "NZL"));
        assertThat(study(jen, "oceania").get("runId").asLong()).isEqualTo(first);

        // A run with nothing typed counts nowhere, but its end card offers the study too.
        long empty = startRollCall(jen, "oceania", "easy").get("id").asLong();
        finishRollCall(jen, empty);
        JsonNode study = study(jen, "oceania");
        assertThat(study.get("runId").asLong()).isEqualTo(empty);
        assertThat(study.get("namedCount").asInt()).isEqualTo(2);
        assertThat(codes(study.get("missed"))).doesNotContain("AUS", "NZL").hasSize(12);

        // A run started since, still in progress, changes nothing.
        nameIn(jen, startRollCall(jen, "oceania", "off").get("id").asLong(), "FJI");
        assertThat(study(jen, "oceania").get("runId").asLong()).isEqualTo(empty);
    }

    // ------------------------------------------------------------------ the shape

    @Test
    void aStudyListsWhatTheRunMissedWithWhereEachOneIs() throws Exception {
        var jen = player("jen");
        long id = startRollCall(jen, "europe", "off").get("id").asLong();
        for (String code : List.of("DEU", "ESP", "ITA")) {
            nameIn(jen, id, code);
        }
        clock.advance(Duration.ofSeconds(95));
        JsonNode finished = finishRollCall(jen, id);

        JsonNode study = study(jen, "europe");
        assertThat(fields(study)).containsExactlyElementsOf(FIELDS);
        assertThat(study.get("runId").asLong()).isEqualTo(id);
        assertThat(study.get("region").asText()).isEqualTo("europe");
        assertThat(study.get("finishedAt").asText()).isEqualTo(finished.get("finishedAt").asText())
                .isEqualTo("2026-10-09T16:01:35Z");
        assertThat(study.get("namedCount").asInt()).isEqualTo(3);
        assertThat(study.get("total").asInt()).isEqualTo(finished.get("total").asInt()).isEqualTo(46);
        assertThat(study.get("missedCount").asInt()).isEqualTo(43).isEqualTo(study.get("missed").size());
        // The same countries as the end card's "missed".
        assertThat(codes(study.get("missed"))).containsExactlyInAnyOrderElementsOf(texts(finished.get("missed")));

        for (JsonNode missed : study.get("missed")) {
            Country country = catalog.find(missed.get("code").asText()).orElseThrow();
            assertThat(fields(missed)).containsExactlyElementsOf(MISSED_FIELDS);
            assertThat(missed.get("region").asText()).isEqualTo(country.region()).isEqualTo("europe");
            assertThat(missed.get("lat").asDouble()).isEqualTo(country.lat());
            assertThat(missed.get("lng").asDouble()).isEqualTo(country.lng());
            assertThat(missed.get("familiarity").asText()).isEqualTo(familiarity(country.difficulty()));
            // Only one run in the region: too few for a rate.
            assertThat(missed.get("namedRate").isNull()).isTrue();
            assertThat(missed.get("sampleRuns").asInt()).isEqualTo(1);
            assertThat(missed.get("yourRuns").asInt()).isEqualTo(1);
            assertThat(missed.get("yourMisses").asInt()).isEqualTo(1);
        }
        assertThat(study.get("order").asText()).isEqualTo("familiarity");
    }

    @Test
    void nearbyIsEveryBorderThenTheNearestAnswerCountriesToFour() throws Exception {
        var jen = player("jen");
        play(jen, "world", "off", List.of("DEU", "ESP", "ITA", "AUS"));
        Map<String, JsonNode> missed = byCode(study(jen, "world").get("missed"));
        List<Country> answers = catalog.answerable(Region.WORLD, Kind.COUNTRY);

        for (JsonNode entry : missed.values()) {
            String code = entry.get("code").asText();
            Country country = catalog.find(code).orElseThrow();
            List<JsonNode> nearby = list(entry.get("nearby"));
            int borders = (int) answers.stream()
                    .filter(other -> !other.code().equals(code) && catalog.km(code, other.code()) == 0)
                    .count();
            assertThat(nearby).as(code).hasSize(Math.max(4, borders));
            List<String> expected = answers.stream()
                    .filter(other -> !other.code().equals(code))
                    .sorted(Comparator.comparingInt((Country other) -> catalog.km(code, other.code()))
                            .thenComparingDouble(other -> labelKm(country, other))
                            .thenComparing(Country::code))
                    .limit(Math.max(4, borders))
                    .map(Country::code)
                    .toList();
            assertThat(nearby).extracting(n -> n.get("code").asText()).as(code).containsExactlyElementsOf(expected);
            for (JsonNode near : nearby) {
                assertThat(fields(near)).containsExactly("code", "km");
                assertThat(near.get("km").asInt()).isEqualTo(catalog.km(code, near.get("code").asText()));
            }
        }

        // France borders eight answer countries: all eight are listed, every one 0 km.
        List<JsonNode> france = list(missed.get("FRA").get("nearby"));
        assertThat(france).extracting(n -> n.get("code").asText())
                .containsExactlyInAnyOrder("AND", "BEL", "DEU", "ITA", "LUX", "MCO", "ESP", "CHE");
        assertThat(france).allSatisfy(near -> assertThat(near.get("km").asInt()).isZero());
        // China borders fourteen: every one of them is there.
        assertThat(list(missed.get("CHN").get("nearby"))).hasSize(14)
                .allSatisfy(near -> assertThat(near.get("km").asInt()).isZero());
        // New Zealand's nearest are answer countries only: Norfolk Island and New Caledonia are territories.
        assertThat(list(missed.get("NZL").get("nearby"))).extracting(n -> n.get("code").asText())
                .startsWith("AUS", "FJI").doesNotContain("NFK", "NCL", "ATA");
        assertThat(list(missed.get("NZL").get("nearby")).get(0).get("km").asInt()).isEqualTo(616);
    }

    @Test
    void nearestNamedIsTheClosestCountryTheRunNamed() throws Exception {
        var jen = player("jen");
        List<String> named = List.of("DEU", "ESP", "ITA", "AUS");
        play(jen, "world", "off", named);
        Map<String, JsonNode> missed = byCode(study(jen, "world").get("missed"));

        for (JsonNode entry : missed.values()) {
            String code = entry.get("code").asText();
            Country country = catalog.find(code).orElseThrow();
            String expected = named.stream()
                    .map(n -> catalog.find(n).orElseThrow())
                    .min(Comparator.comparingInt((Country other) -> catalog.km(code, other.code()))
                            .thenComparingDouble(other -> labelKm(country, other))
                            .thenComparing(Country::code))
                    .orElseThrow().code();
            JsonNode nearest = entry.get("nearestNamed");
            assertThat(fields(nearest)).containsExactly("code", "km");
            assertThat(nearest.get("code").asText()).as(code).isEqualTo(expected);
            assertThat(nearest.get("km").asInt()).isEqualTo(catalog.km(code, expected));
        }
        assertThat(missed.get("NZL").get("nearestNamed").get("code").asText()).isEqualTo("AUS");
        assertThat(missed.get("NZL").get("nearestNamed").get("km").asInt()).isEqualTo(616);
        assertThat(missed.get("FRA").get("nearestNamed").get("km").asInt()).isZero();
    }

    @Test
    void aRunThatNamedNothingHasNoNearestNamed() throws Exception {
        var jen = player("jen");
        long id = startRollCall(jen, "oceania", "off").get("id").asLong();
        finishRollCall(jen, id);
        JsonNode study = study(jen, "oceania");
        assertThat(study.get("namedCount").asInt()).isZero();
        assertThat(study.get("missedCount").asInt()).isEqualTo(14);
        for (JsonNode missed : study.get("missed")) {
            assertThat(missed.get("nearestNamed").isNull()).isTrue();
            // It counts nowhere, so it is in no sample.
            assertThat(missed.get("sampleRuns").asInt()).isZero();
            assertThat(missed.get("yourRuns").asInt()).isZero();
            assertThat(missed.get("yourMisses").asInt()).isZero();
        }
    }

    // ------------------------------------------------------------------ how often it is named

    /**
     * The rate is over every run in the region that counts, by players who still exist:
     * not runs with nothing typed, nor runs in progress, other regions, or a deleted
     * account's. Filled-in countries count as named, as on a run's end card.
     */
    @Test
    void theNamedRateIsEverybodysCountingRunsInTheRegion() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var amy = player("amy");
        var dan = player("dan");

        play(jen, "oceania", "off", List.of("AUS", "NZL", "FJI"));
        play(colleen, "oceania", "off", List.of("AUS", "PNG"));
        play(amy, "oceania", "off", List.of("AUS", "NZL"));
        play(amy, "oceania", "easy", List.of("FJI"));             // AUS and NZL filled in
        // None of these is in the sample:
        long empty = startRollCall(colleen, "oceania", "easy").get("id").asLong();
        finishRollCall(colleen, empty);                             // nothing typed
        nameIn(amy, startRollCall(amy, "oceania", "off").get("id").asLong(), "TUV");   // in progress
        play(jen, "world", "off", List.of("TUV", "PNG"));          // another region
        play(dan, "oceania", "off", List.of("TUV", "PLW", "PNG"));
        users.delete(users.findByUsername("dan").orElseThrow());   // an account since deleted
        long latest = play(jen, "oceania", "off", List.of("AUS"));

        JsonNode study = study(jen, "oceania");
        assertThat(study.get("runId").asLong()).isEqualTo(latest);
        assertThat(study.get("order").asText()).isEqualTo("named-rate");
        Map<String, JsonNode> missed = byCode(study.get("missed"));
        assertThat(missed).hasSize(13).doesNotContainKey("AUS");
        for (JsonNode entry : missed.values()) {
            assertThat(entry.get("sampleRuns").asInt()).isEqualTo(5);
            assertThat(entry.get("yourRuns").asInt()).isEqualTo(2);
        }
        assertRate(missed.get("NZL"), 0.6, 1);
        assertRate(missed.get("FJI"), 0.4, 1);
        assertRate(missed.get("PNG"), 0.2, 2);
        assertRate(missed.get("TUV"), 0.0, 2);
        assertRate(missed.get("PLW"), 0.0, 2);

        // Most named first; then, at the same rate, well-known before familiar before obscure, then by code.
        List<String> order = codes(study.get("missed"));
        assertThat(order.subList(0, 3)).containsExactly("NZL", "FJI", "PNG");
        List<String> rest = order.subList(3, order.size());
        assertThat(rest).isEqualTo(rest.stream()
                .sorted(Comparator.comparingInt((String code) -> familiarityRank(code)).thenComparing(code -> code))
                .toList());
        // All ten are obscure (tiers 4 and 5), so by code.
        assertThat(rest).containsExactly("FSM", "KIR", "MHL", "NRU", "PLW", "SLB", "TON", "TUV", "VUT", "WSM");
    }

    @Test
    void ratesAreRoundedToFourPlacesAndYourRecordIsYourCountingRuns() throws Exception {
        var jen = player("jen");
        for (int i = 0; i < 4; i++) {
            play(jen, "oceania", "off", List.of("AUS", "NZL"));
        }
        play(jen, "oceania", "off", List.of("AUS", "FJI"));
        for (int i = 0; i < 4; i++) {
            play(jen, "oceania", "off", List.of("AUS"));
        }
        Map<String, JsonNode> missed = byCode(study(jen, "oceania").get("missed"));
        // 4 of 9 runs named New Zealand (0.444...), 1 of 9 Fiji (0.111...).
        assertThat(missed.get("NZL").get("sampleRuns").asInt()).isEqualTo(9);
        assertThat(missed.get("NZL").get("namedRate").asDouble()).isEqualTo(0.4444);
        assertThat(missed.get("FJI").get("namedRate").asDouble()).isEqualTo(0.1111);
        assertThat(missed.get("PNG").get("namedRate").asDouble()).isEqualTo(0.0);
        assertThat(missed.get("NZL").get("yourRuns").asInt()).isEqualTo(9);
        assertThat(missed.get("NZL").get("yourMisses").asInt()).isEqualTo(5);
        assertThat(missed.get("FJI").get("yourMisses").asInt()).isEqualTo(8);
        assertThat(missed.get("PNG").get("yourMisses").asInt()).isEqualTo(9);
        assertThat(codes(study(jen, "oceania").get("missed")).subList(0, 2)).containsExactly("NZL", "FJI");

        // Two of three round up: 0.6667.
        var colleen = player("colleen");
        play(colleen, "africa", "off", List.of("EGY", "KEN"));
        play(colleen, "africa", "off", List.of("EGY", "KEN"));
        play(colleen, "africa", "off", List.of("EGY"));
        assertThat(byCode(study(colleen, "africa").get("missed")).get("KEN").get("namedRate").asDouble())
                .isEqualTo(0.6667);
    }

    @Test
    void withFewerThanThreeRunsTheOrderIsByFamiliarity() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        play(colleen, "europe", "off", List.of("FRA"));
        play(jen, "europe", "off", List.of("DEU"));
        JsonNode study = study(jen, "europe");
        assertThat(study.get("order").asText()).isEqualTo("familiarity");
        List<String> order = codes(study.get("missed"));
        assertThat(order).isEqualTo(order.stream()
                .sorted(Comparator.comparingInt((String code) -> familiarityRank(code)).thenComparing(code -> code))
                .toList());
        Map<String, JsonNode> missed = byCode(study.get("missed"));
        assertThat(missed.get("FRA").get("namedRate").isNull()).isTrue();
        assertThat(missed.get("FRA").get("sampleRuns").asInt()).isEqualTo(2);
        assertThat(missed.get("FRA").get("yourRuns").asInt()).isEqualTo(1);
        assertThat(missed.get("FRA").get("familiarity").asText()).isEqualTo("well-known");
        // Tiers 1 and 2 are both well-known, so Austria (2) comes before Spain (1).
        assertThat(order.subList(0, 4)).containsExactly("AUT", "BEL", "CHE", "DNK");
        assertThat(missed.get("ALB").get("familiarity").asText()).isEqualTo("familiar");
        assertThat(missed.get("AND").get("familiarity").asText()).isEqualTo("obscure");
        assertThat(missed.get("EST").get("familiarity").asText()).isEqualTo("obscure");
    }

    // ------------------------------------------------------------------ the request

    @Test
    void theRegionIsTheWorldByDefaultAndMustBeKnown() throws Exception {
        var jen = player("jen");
        long world = play(jen, "world", "off", List.of("FRA"));
        long mainland = play(jen, "mainland", "off", List.of("FRA"));
        assertThat(getOk("/globle/api/rollcall/study", jen).get("runId").asLong()).isEqualTo(world);
        assertThat(study(jen, " World ").get("runId").asLong()).isEqualTo(world);
        JsonNode study = study(jen, "mainland");
        assertThat(study.get("runId").asLong()).isEqualTo(mainland);
        assertThat(study.get("region").asText()).isEqualTo("mainland");
        assertThat(study.get("total").asInt()).isEqualTo(166);
        assertThat(codes(study.get("missed"))).doesNotContain("TON", "MLT");
        mvc.perform(get("/globle/api/rollcall/study?region=atlantis").with(jen))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        mvc.perform(get("/globle/api/rollcall/study?region=antarctica").with(jen))
                .andExpect(status().isBadRequest());
    }

    @Test
    void studyingStoresNothing() throws Exception {
        var jen = player("jen");
        play(jen, "oceania", "off", List.of("AUS", "FJI"));
        String before = jdbc.queryForList("select * from globle_game order by id").toString();
        JsonNode first = study(jen, "oceania");
        clock.advance(Duration.ofHours(3));
        assertThat(study(jen, "oceania")).isEqualTo(first);
        assertThat(jdbc.queryForList("select * from globle_game order by id").toString()).isEqualTo(before);
        assertThat(nameRequests.count()).isZero();
    }

    @Test
    void signedOutGetsA401() throws Exception {
        mvc.perform(get("/globle/api/rollcall/study?region=world"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
    }

    // ------------------------------------------------------------------ helpers

    /** A finished run in a region, started with {@code autofill}, that typed exactly {@code codes}. */
    private long play(RequestPostProcessor who, String region, String autofill, List<String> codes) throws Exception {
        long id = startRollCall(who, region, autofill).get("id").asLong();
        JsonNode state = null;
        for (String code : codes) {
            state = nameIn(who, id, code);
        }
        if (state == null || state.get("status").asText().equals("PLAYING")) {
            finishRollCall(who, id);
        }
        return id;
    }

    private JsonNode study(RequestPostProcessor who, String region) throws Exception {
        return read(mvc.perform(get("/globle/api/rollcall/study").param("region", region).with(who))
                .andExpect(status().isOk()).andReturn());
    }

    private void noStudy(RequestPostProcessor who, String region) throws Exception {
        mvc.perform(get("/globle/api/rollcall/study").param("region", region).with(who))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private static void assertRate(JsonNode missed, double rate, int yourMisses) {
        String code = missed.get("code").asText();
        assertThat(missed.get("namedRate").asDouble()).as(code).isEqualTo(rate);
        assertThat(missed.get("yourMisses").asInt()).as(code).isEqualTo(yourMisses);
    }

    private static String familiarity(int tier) {
        return tier <= 2 ? "well-known" : tier == 3 ? "familiar" : "obscure";
    }

    private int familiarityRank(String code) {
        int tier = catalog.find(code).orElseThrow().difficulty();
        return tier <= 2 ? 0 : tier == 3 ? 1 : 2;
    }

    private static double labelKm(Country a, Country b) {
        return GreatCircle.km(a.lat(), a.lng(), b.lat(), b.lng());
    }

    private static Map<String, JsonNode> byCode(JsonNode missed) {
        Map<String, JsonNode> byCode = new HashMap<>();
        missed.forEach(entry -> byCode.put(entry.get("code").asText(), entry));
        return byCode;
    }

    private static List<JsonNode> list(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).toList();
    }

    private static List<String> codes(JsonNode missed) {
        return StreamSupport.stream(missed.spliterator(), false).map(n -> n.get("code").asText()).toList();
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}

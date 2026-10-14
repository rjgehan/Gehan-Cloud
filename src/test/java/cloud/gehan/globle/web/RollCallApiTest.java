package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Name them all" (roll call) through its API: starting, naming, finishing, every
 * autofill, every refusal, and who may touch which run.
 */
class RollCallApiTest extends GlobleTestBase {

    private static final List<String> CONTINENTS = List.of("africa", "americas", "asia", "europe", "oceania");

    // ------------------------------------------------------------------ options

    @Test
    void theWorldOptionsCountEachAutofillFromTheData() throws Exception {
        var jen = player("jen");
        JsonNode options = rollCallOptions(jen, "world");

        assertThat(fields(options))
                .containsExactly("region", "total", "easy", "known", "mine", "finishedRuns", "studyable", "best");
        assertThat(options.get("region").asText()).isEqualTo("world");
        assertThat(options.get("total").asInt()).isEqualTo(rollCallPool(Region.WORLD).size()).isEqualTo(197);
        assertThat(options.get("easy").asInt()).isEqualTo(rollCallTiers(Region.WORLD, 1).size()).isEqualTo(30);
        assertThat(options.get("known").asInt()).isEqualTo(rollCallTiers(Region.WORLD, 2).size()).isEqualTo(70);
        assertThat(options.get("mine").isNull()).isTrue();
        assertThat(options.get("finishedRuns").asInt()).isZero();
        assertThat(options.get("studyable").asBoolean()).isFalse();
        assertThat(options.get("best").isNull()).isTrue();
        // Without a region it is the world.
        assertThat(getOk("/globle/api/rollcall/options", jen)).isEqualTo(options);
        assertThat(games.count()).isZero();
    }

    @Test
    void eachContinentAndMainlandCountsItsOwnPool() throws Exception {
        var jen = player("jen");
        int sum = 0;
        for (String continent : CONTINENTS) {
            Region region = Region.parse(continent).orElseThrow();
            JsonNode options = rollCallOptions(jen, continent);
            assertThat(options.get("region").asText()).isEqualTo(continent);
            assertThat(options.get("total").asInt()).as(continent).isEqualTo(rollCallPool(region).size()).isPositive();
            assertThat(options.get("easy").asInt()).as(continent).isEqualTo(rollCallTiers(region, 1).size());
            assertThat(options.get("known").asInt()).as(continent).isEqualTo(rollCallTiers(region, 2).size())
                    .as("%s: known leaves something to name, so it is never refused", continent)
                    .isLessThan(options.get("total").asInt());
            sum += options.get("total").asInt();
        }
        assertThat(sum).isEqualTo(197);
        JsonNode mainland = rollCallOptions(jen, "mainland");
        assertThat(mainland.get("total").asInt()).isEqualTo(rollCallPool(Region.MAINLAND).size()).isEqualTo(166);
    }

    // ------------------------------------------------------------------ starting

    @Test
    void aNewRunStartsWithNothingNamedAndResumes() throws Exception {
        var jen = player("jen");
        JsonNode started = startRollCall(jen, "world", "off");

        assertThat(fields(started)).containsExactly("id", "mode", "region", "status", "autofill", "total", "named",
                "namedCount", "autoCount", "byRegion", "elapsedSeconds", "startedAt", "finishedAt", "missed");
        long id = started.get("id").asLong();
        assertThat(id).isPositive();
        assertThat(started.get("mode").asText()).isEqualTo("rollcall");
        assertThat(started.get("region").asText()).isEqualTo("world");
        assertThat(started.get("status").asText()).isEqualTo("PLAYING");
        assertThat(started.get("autofill").asText()).isEqualTo("off");
        assertThat(started.get("total").asInt()).isEqualTo(197);
        assertThat(started.get("named")).isEmpty();
        assertThat(started.get("namedCount").asInt()).isZero();
        assertThat(started.get("autoCount").asInt()).isZero();
        assertThat(started.get("elapsedSeconds").asLong()).isZero();
        assertThat(started.get("startedAt").asText()).isEqualTo(clock.instant().toString())
                .isEqualTo("2026-10-09T16:00:00Z");
        assertThat(started.get("finishedAt").isNull()).isTrue();
        assertThat(started.get("missed").isNull()).isTrue();

        JsonNode byRegion = started.get("byRegion");
        assertThat(texts(byRegion, "region")).containsExactlyElementsOf(CONTINENTS);
        for (JsonNode continent : byRegion) {
            assertThat(fields(continent)).containsExactly("region", "named", "total");
            assertThat(continent.get("named").asInt()).isZero();
            assertThat(continent.get("total").asInt()).isEqualTo(
                    (int) rollCallPool(Region.WORLD).stream()
                            .filter(c -> c.region().equals(continent.get("region").asText())).count());
        }

        GlobleGame row = games.findById(id).orElseThrow();
        assertThat(row.getMode()).isEqualTo(Mode.ROLLCALL);
        assertThat(row.getAutofill()).isEqualTo(Autofill.OFF);
        assertThat(row.getAutofillCount()).isZero();
        assertThat(row.getAnswerCode()).isNull();
        assertThat(row.getPuzzleDate()).isNull();

        assertThat(getOk("/globle/api/rollcall/current?region=world", jen)).isEqualTo(started);
        assertThat(getOk("/globle/api/rollcall/current", jen)).isEqualTo(started);
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current?region=europe").with(jen))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void anEmptyStartIsAWholeWorldRunWithNothingFilledIn() throws Exception {
        var jen = player("jen");
        JsonNode started = postOk("/globle/api/rollcall/new", Map.of(), jen);
        assertThat(started.get("region").asText()).isEqualTo("world");
        assertThat(started.get("autofill").asText()).isEqualTo("off");

        JsonNode noBody = postOk("/globle/api/rollcall/new", null, jen);
        assertThat(noBody.get("region").asText()).isEqualTo("world");
        assertThat(noBody.get("namedCount").asInt()).isZero();
    }

    @Test
    void easyAndKnownFillInTheirTiersFlaggedAsAuto() throws Exception {
        var jen = player("jen");
        for (String autofill : new String[]{"easy", "known"}) {
            int tier = autofill.equals("easy") ? 1 : 2;
            List<String> expected = rollCallTiers(Region.WORLD, tier);
            JsonNode run = startRollCall(jen, "world", autofill);

            assertThat(run.get("autofill").asText()).isEqualTo(autofill);
            assertThat(run.get("status").asText()).isEqualTo("PLAYING");
            assertThat(texts(run.get("named"), "code")).as(autofill).containsExactlyElementsOf(expected);
            assertThat(run.get("named")).allMatch(n -> n.get("auto").asBoolean());
            assertThat(run.get("namedCount").asInt()).isEqualTo(expected.size());
            assertThat(run.get("autoCount").asInt()).isEqualTo(expected.size());
            int perContinent = StreamSupport.stream(run.get("byRegion").spliterator(), false)
                    .mapToInt(r -> r.get("named").asInt()).sum();
            assertThat(perContinent).isEqualTo(expected.size());

            GlobleGame row = games.findById(run.get("id").asLong()).orElseThrow();
            assertThat(row.getAutofill().api()).isEqualTo(autofill);
            assertThat(row.getAutofillCount()).isEqualTo(expected.size());
            assertThat(row.guessCodes()).containsExactlyElementsOf(expected);
        }
        assertThat(rollCallTiers(Region.WORLD, 1)).hasSize(30).contains("USA", "CHN", "FRA");
        assertThat(rollCallTiers(Region.WORLD, 2)).hasSize(70);
    }

    @Test
    void autofillInAContinentFillsOnlyThatContinent() throws Exception {
        var jen = player("jen");
        for (String continent : CONTINENTS) {
            Region region = Region.parse(continent).orElseThrow();
            JsonNode easy = startRollCall(jen, continent, "easy");
            assertThat(easy.get("total").asInt()).isEqualTo(rollCallPool(region).size());
            assertThat(texts(easy.get("named"), "code")).as(continent)
                    .containsExactlyElementsOf(rollCallTiers(region, 1));
            assertThat(texts(easy.get("byRegion"), "region")).containsExactly(continent);
            assertThat(easy.get("byRegion").get(0).get("named").asInt()).isEqualTo(rollCallTiers(region, 1).size());

            JsonNode known = startRollCall(jen, continent, "known");
            assertThat(texts(known.get("named"), "code")).as(continent)
                    .containsExactlyElementsOf(rollCallTiers(region, 2));
            for (JsonNode named : known.get("named")) {
                assertThat(catalog.require(named.get("code").asText()).region()).isEqualTo(continent);
            }
        }
        JsonNode europe = startRollCall(jen, "europe", "easy");
        assertThat(europe.get("autoCount").asInt()).isEqualTo(rollCallTiers(Region.EUROPE, 1).size()).isPositive();
    }

    // ------------------------------------------------------------------ naming

    @Test
    void namingAddsToTheEndAsTypedAndCountsPerContinent() throws Exception {
        var jen = player("jen");
        JsonNode started = startRollCall(jen, "world", "easy");
        long id = started.get("id").asLong();
        Country typed = notEasy(Region.WORLD, 0);

        JsonNode after = postOk("/globle/api/rollcall/" + id + "/name",
                Map.of("code", " " + typed.code().toLowerCase() + " "), jen);

        assertThat(after.get("namedCount").asInt()).isEqualTo(31);
        assertThat(after.get("autoCount").asInt()).isEqualTo(30);
        JsonNode last = after.get("named").get(30);
        assertThat(last.get("code").asText()).isEqualTo(typed.code());
        assertThat(last.get("auto").asBoolean()).isFalse();
        assertThat(after.get("named").get(0).get("auto").asBoolean()).isTrue();
        JsonNode continent = StreamSupport.stream(after.get("byRegion").spliterator(), false)
                .filter(r -> r.get("region").asText().equals(typed.region())).findFirst().orElseThrow();
        JsonNode before = StreamSupport.stream(started.get("byRegion").spliterator(), false)
                .filter(r -> r.get("region").asText().equals(typed.region())).findFirst().orElseThrow();
        assertThat(continent.get("named").asInt()).isEqualTo(before.get("named").asInt() + 1);
        assertThat(after.get("status").asText()).isEqualTo("PLAYING");
        assertThat(after.get("missed").isNull()).isTrue();

        GlobleGame row = games.findById(id).orElseThrow();
        assertThat(row.typedCodes()).containsExactly(typed.code());
        assertThat(row.getGuessCount()).isEqualTo(31);
        assertThat(getOk("/globle/api/rollcall/current?region=world", jen)).isEqualTo(after);
    }

    @Test
    void everyRefusalLeavesTheRunAsItWas() throws Exception {
        var jen = player("jen");
        long id = startRollCall(jen, "europe", "easy").get("id").asLong();
        String autoFilled = rollCallTiers(Region.EUROPE, 1).get(0);
        Country typed = notEasy(Region.EUROPE, 0);
        nameIn(jen, id, typed.code());
        Country territory = catalog.all().stream().filter(c -> !c.answer()).findFirst().orElseThrow();
        Country elsewhere = rollCallPool(Region.ASIA).get(0);

        refused(jen, id, Map.of("code", "XYZ"), 400, "unknown-country");
        refused(jen, id, Map.of("code", ""), 400, "bad-request");
        refused(jen, id, Map.of(), 400, "bad-request");
        refused(jen, id, null, 400, "bad-request");
        refused(jen, id, Map.of("code", typed.code()), 400, "already-guessed");
        refused(jen, id, Map.of("code", autoFilled), 400, "already-guessed");
        refused(jen, id, Map.of("code", territory.code()), 400, "not-an-answer");
        refused(jen, id, Map.of("code", elsewhere.code()), 400, "outside-region");
        postRaw("/globle/api/rollcall/" + id + "/name", "not json", jen)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));

        GlobleGame row = games.findById(id).orElseThrow();
        assertThat(row.getGuessCount()).isEqualTo(rollCallTiers(Region.EUROPE, 1).size() + 1);
        assertThat(row.isPlaying()).isTrue();

        finishRollCall(jen, id);
        refused(jen, id, Map.of("code", notEasy(Region.EUROPE, 1).code()), 400, "finished");
        post("/globle/api/rollcall/" + id + "/finish", null, jen)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"finished\"}", true));
    }

    /** Mainland is accepted by the API: a small island nation is outside it. */
    @Test
    void aSmallIslandNationIsOutsideAMainlandRun() throws Exception {
        var jen = player("jen");
        long id = startRollCall(jen, "mainland", "off").get("id").asLong();
        Country island = catalog.all().stream().filter(c -> c.answer() && c.smallIsland()).findFirst().orElseThrow();
        refused(jen, id, Map.of("code", island.code()), 400, "outside-region");
        assertThat(nameIn(jen, id, rollCallPool(Region.MAINLAND).get(0).code()).get("namedCount").asInt())
                .isEqualTo(1);
    }

    // ------------------------------------------------------------------ ending

    @Test
    void finishingListsTheMissedCountriesSortedByCode() throws Exception {
        var jen = player("jen");
        long id = startRollCall(jen, "world", "easy").get("id").asLong();
        List<String> typed = List.of(notEasy(Region.WORLD, 3).code(), notEasy(Region.WORLD, 1).code());
        for (String code : typed) {
            nameIn(jen, id, code);
        }
        clock.advance(Duration.ofSeconds(90));

        JsonNode finished = finishRollCall(jen, id);

        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("finishedAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(finished.get("elapsedSeconds").asLong()).isEqualTo(90);
        assertThat(finished.get("namedCount").asInt()).isEqualTo(32);
        List<String> expected = rollCallPool(Region.WORLD).stream().map(Country::code)
                .filter(code -> !rollCallTiers(Region.WORLD, 1).contains(code) && !typed.contains(code))
                .sorted()
                .toList();
        List<String> missed = texts(finished.get("missed"));
        assertThat(missed).containsExactlyElementsOf(expected).hasSize(197 - 32).isSorted();
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current?region=world").with(jen))
                .andExpect(status().isNoContent());
        assertThat(games.findById(id).orElseThrow().getStatus()).isEqualTo(Status.FINISHED);
    }

    @Test
    void namingTheLastCountryFinishesTheRun() throws Exception {
        var jen = player("jen");
        List<Country> oceania = rollCallPool(Region.OCEANIA);
        long id = startRollCall(jen, "oceania", "off").get("id").asLong();
        JsonNode state = null;
        for (int i = 0; i < oceania.size(); i++) {
            assertThat(state == null || state.get("status").asText().equals("PLAYING")).isTrue();
            clock.advance(Duration.ofSeconds(5));
            state = nameIn(jen, id, oceania.get(i).code());
        }

        assertThat(state.get("status").asText()).isEqualTo("FINISHED");
        assertThat(state.get("missed")).isEmpty();
        assertThat(state.get("namedCount").asInt()).isEqualTo(oceania.size()).isEqualTo(state.get("total").asInt());
        assertThat(state.get("elapsedSeconds").asLong()).isEqualTo(5L * oceania.size());
        assertThat(state.get("finishedAt").asText()).isEqualTo(clock.instant().toString());
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current?region=oceania").with(jen))
                .andExpect(status().isNoContent());
    }

    /**
     * "I'm done" with nothing typed - on an empty run, or one holding only what autofill
     * gave it - ends the run, which keeps its end card, but it counts nowhere: not as a
     * finished run, not as the best, and not toward "mine". One typed name is enough.
     */
    @Test
    void aRunFinishedWithNothingTypedCountsNowhere() throws Exception {
        var jen = player("jen");
        List<String> known = rollCallTiers(Region.AFRICA, 2);
        int total = rollCallPool(Region.AFRICA).size();
        long filledIn = startRollCall(jen, "africa", "known").get("id").asLong();
        clock.advance(Duration.ofSeconds(5));
        JsonNode onlyFilledIn = finishRollCall(jen, filledIn);
        long empty = startRollCall(jen, "africa", "off").get("id").asLong();
        clock.advance(Duration.ofSeconds(5));
        JsonNode nothing = finishRollCall(jen, empty);

        // Both end as usual and are kept...
        assertThat(onlyFilledIn.get("status").asText()).isEqualTo("FINISHED");
        assertThat(onlyFilledIn.get("namedCount").asInt()).isEqualTo(known.size()).isPositive();
        assertThat(onlyFilledIn.get("autoCount").asInt()).isEqualTo(known.size());
        assertThat(onlyFilledIn.get("missed")).hasSize(total - known.size());
        assertThat(nothing.get("status").asText()).isEqualTo("FINISHED");
        assertThat(nothing.get("namedCount").asInt()).isZero();
        assertThat(nothing.get("missed")).hasSize(total);
        assertThat(games.findById(filledIn).orElseThrow().getStatus()).isEqualTo(Status.FINISHED);
        assertThat(games.findById(empty).orElseThrow().getStatus()).isEqualTo(Status.FINISHED);
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current?region=africa").with(jen))
                .andExpect(status().isNoContent());

        // ...but count nowhere: two finished runs, and "mine" still locked.
        JsonNode options = rollCallOptions(jen, "africa");
        assertThat(options.get("finishedRuns").asInt()).isZero();
        assertThat(options.get("best").isNull()).isTrue();
        assertThat(options.get("mine").isNull()).isTrue();
        mineRefused(jen, "africa");

        long typed = startRollCall(jen, "africa", "known").get("id").asLong();
        nameIn(jen, typed, notTier(Region.AFRICA, 2, 0).code());
        clock.advance(Duration.ofSeconds(7));
        finishRollCall(jen, typed);
        options = rollCallOptions(jen, "africa");
        assertThat(options.get("finishedRuns").asInt()).isEqualTo(1);
        assertThat(options.get("best").get("named").asInt()).isEqualTo(known.size() + 1);
        assertThat(options.get("best").get("autoCount").asInt()).isEqualTo(known.size());
        assertThat(options.get("best").get("seconds").asLong()).isEqualTo(7);
        assertThat(options.get("mine").isNull()).as("one run that counts").isTrue();
    }

    // ------------------------------------------------------------------ starting over

    @Test
    void startingOverFinishesARunWithATypedName() throws Exception {
        var jen = player("jen");
        long first = startRollCall(jen, "europe", "easy").get("id").asLong();
        nameIn(jen, first, notEasy(Region.EUROPE, 0).code());
        long world = startRollCall(jen, "world", "off").get("id").asLong();
        clock.advance(Duration.ofMinutes(3));

        long second = startRollCall(jen, "europe", "off").get("id").asLong();

        GlobleGame abandoned = games.findById(first).orElseThrow();
        assertThat(abandoned.getStatus()).isEqualTo(Status.FINISHED);
        assertThat(abandoned.getFinishedAt()).isEqualTo(clock.instant());
        assertThat(games.findById(world).orElseThrow().isPlaying()).as("another region").isTrue();
        assertThat(getOk("/globle/api/rollcall/current?region=europe", jen).get("id").asLong()).isEqualTo(second);
        JsonNode options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).isEqualTo(1);
        assertThat(options.get("best").get("named").asInt()).isEqualTo(rollCallTiers(Region.EUROPE, 1).size() + 1);
        assertThat(options.get("best").get("seconds").asLong()).isEqualTo(180);
        refused(jen, first, Map.of("code", notEasy(Region.EUROPE, 1).code()), 400, "finished");
    }

    /** Nothing typed means nothing the player did: starting again simply drops it. */
    @Test
    void startingOverDropsARunWithNothingTyped() throws Exception {
        var jen = player("jen");
        long first = startRollCall(jen, "world", "known").get("id").asLong();
        long second = startRollCall(jen, "world", "off").get("id").asLong();
        long third = startRollCall(jen, "world", "easy").get("id").asLong();

        assertThat(games.findAll()).extracting(GlobleGame::getId).containsExactly(third);
        assertThat(rollCallOptions(jen, "world").get("finishedRuns").asInt()).isZero();
        assertThat(games.findAll().stream().filter(GlobleGame::isPlaying)).hasSize(1);
        // A tab still showing a dropped run is told so; it never names into the new one
        // (on SQLite too: SqliteWritesTest).
        assertThat(List.of(first, second, third)).doesNotHaveDuplicates();
        for (long dropped : new long[]{first, second}) {
            post("/globle/api/rollcall/" + dropped + "/name", Map.of("code", notEasy(Region.WORLD, 0).code()), jen)
                    .andExpect(status().isNotFound())
                    .andExpect(content().json("{\"error\":\"not-found\"}", true));
        }
        assertThat(games.findById(third).orElseThrow().typedCodes()).isEmpty();
    }

    // ------------------------------------------------------------------ mine

    @Test
    void mineIsLockedUntilTwoFinishedRunsThenIsWhatEveryRecentRunNamed() throws Exception {
        var jen = player("jen");
        List<String> europe = rollCallPool(Region.EUROPE).stream().map(Country::code).toList();
        String a = europe.get(0);
        String b = europe.get(1);
        String c = europe.get(2);
        String d = europe.get(3);

        assertThat(rollCallOptions(jen, "europe").get("mine").isNull()).isTrue();
        mineRefused(jen, "europe");

        play(jen, "europe", List.of(a, b, c));
        assertThat(rollCallOptions(jen, "europe").get("mine").isNull()).as("one finished run").isTrue();
        long inProgress = startRollCall(jen, "europe", "off").get("id").asLong();
        nameIn(jen, inProgress, d);
        mineRefused(jen, "europe");
        assertThat(games.findById(inProgress).orElseThrow().isPlaying())
                .as("a refused start abandons nothing").isTrue();
        finishRollCall(jen, inProgress);          // second run: d only

        play(jen, "europe", List.of(a, b, c, d)); // third run
        // Last three runs: {a,b,c}, {d}, {a,b,c,d}: nothing in all three.
        JsonNode options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).isEqualTo(3);
        assertThat(options.get("mine").asInt()).isZero();

        play(jen, "europe", List.of(b, c, d));    // fourth: the {a,b,c} run drops out of the last three
        // Last three: {d}, {a,b,c,d}, {b,c,d}.
        assertThat(rollCallOptions(jen, "europe").get("mine").asInt()).isEqualTo(1);
        play(jen, "europe", List.of(a, b, c, d)); // fifth
        // Last three: {a,b,c,d}, {b,c,d}, {a,b,c,d}.
        assertThat(rollCallOptions(jen, "europe").get("mine").asInt()).isEqualTo(3);

        JsonNode mine = startRollCall(jen, "europe", "mine");
        assertThat(mine.get("autofill").asText()).isEqualTo("mine");
        assertThat(texts(mine.get("named"), "code")).containsExactly(b, c, d);
        assertThat(mine.get("named")).allMatch(n -> n.get("auto").asBoolean());
        assertThat(mine.get("autoCount").asInt()).isEqualTo(3);
        assertThat(games.findById(mine.get("id").asLong()).orElseThrow().getAutofill()).isEqualTo(Autofill.MINE);

        // Another region's runs are not this region's: the world is still locked.
        assertThat(rollCallOptions(jen, "world").get("mine").isNull()).isTrue();
        assertThat(rollCallOptions(jen, "world").get("finishedRuns").asInt()).isZero();
    }

    /** Exactly two finished runs are enough, and a filled-in country counts as named. */
    @Test
    void mineWorksFromTwoRunsAndCountsFilledInCountries() throws Exception {
        var jen = player("jen");
        List<String> easy = rollCallTiers(Region.WORLD, 1);
        long first = startRollCall(jen, "world", "easy").get("id").asLong();
        String extra = notEasy(Region.WORLD, 0).code();
        nameIn(jen, first, extra);
        finishRollCall(jen, first);
        long second = startRollCall(jen, "world", "off").get("id").asLong();
        for (String code : easy.subList(0, 10)) {
            nameIn(jen, second, code);
        }
        nameIn(jen, second, extra);
        finishRollCall(jen, second);

        assertThat(rollCallOptions(jen, "world").get("mine").asInt()).isEqualTo(11);
        JsonNode mine = startRollCall(jen, "world", "mine");
        List<String> expected = rollCallPool(Region.WORLD).stream().map(Country::code)
                .filter(code -> easy.subList(0, 10).contains(code) || code.equals(extra))
                .toList();
        assertThat(texts(mine.get("named"), "code")).containsExactlyElementsOf(expected);
    }

    /**
     * "Mine" reads the last three runs that count. Runs with nothing typed - however much
     * was filled in - neither unlock it nor push a real run out of its window.
     */
    @Test
    void mineIgnoresRunsWithNothingTyped() throws Exception {
        var jen = player("jen");
        List<String> europe = rollCallPool(Region.EUROPE).stream().map(Country::code).toList();
        String a = europe.get(0);
        String b = europe.get(1);
        String c = europe.get(2);
        String d = europe.get(3);

        // A run with nothing typed counts for nothing, yet it has finished: the study page has it.
        doneWithNothingTyped(jen, "europe", "known");
        JsonNode options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).isZero();
        assertThat(options.get("studyable").asBoolean()).isTrue();
        assertThat(rollCallOptions(jen, "asia").get("studyable").asBoolean()).isFalse();

        play(jen, "europe", List.of(a, b, c));
        doneWithNothingTyped(jen, "europe", "off");
        options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).as("three finished, one counts").isEqualTo(1);
        assertThat(options.get("mine").isNull()).isTrue();
        mineRefused(jen, "europe");

        play(jen, "europe", List.of(a, b, d));
        assertThat(rollCallOptions(jen, "europe").get("mine").asInt()).isEqualTo(2);

        // The three newest finished runs have nothing typed; the window is still the two that count.
        doneWithNothingTyped(jen, "europe", "off");
        doneWithNothingTyped(jen, "europe", "easy");
        doneWithNothingTyped(jen, "europe", "mine");
        options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).isEqualTo(2);
        assertThat(options.get("mine").asInt()).isEqualTo(2);
        assertThat(games.findAll().stream().filter(g -> g.getStatus() == Status.FINISHED)).hasSize(7);

        JsonNode mine = startRollCall(jen, "europe", "mine");
        assertThat(texts(mine.get("named"), "code")).containsExactly(a, b);
        assertThat(mine.get("status").asText()).isEqualTo("PLAYING");
    }

    /**
     * A player who always names all of Oceania would have "mine" fill in all 14: a run
     * over before it began, with nothing left to name. The setup card sees it coming -
     * the count equals the total - and the start is refused, changing nothing.
     */
    @Test
    void anAutofillThatWouldNameTheWholePoolIsRefused() throws Exception {
        var jen = player("jen");
        List<String> oceania = rollCallPool(Region.OCEANIA).stream().map(Country::code).toList();
        play(jen, "oceania", oceania);
        play(jen, "oceania", oceania);
        JsonNode options = rollCallOptions(jen, "oceania");
        assertThat(options.get("mine").asInt()).isEqualTo(options.get("total").asInt()).isEqualTo(oceania.size());

        long inProgress = startRollCall(jen, "oceania", "off").get("id").asLong();
        for (String code : oceania.subList(0, oceania.size() - 1)) {
            nameIn(jen, inProgress, code);
        }
        long rows = games.count();

        mineRefused(jen, "oceania");

        assertThat(games.count()).isEqualTo(rows);
        GlobleGame untouched = games.findById(inProgress).orElseThrow();
        assertThat(untouched.isPlaying()).as("a refused start abandons nothing").isTrue();
        assertThat(untouched.getGuessCount()).isEqualTo(oceania.size() - 1);
        assertThat(getOk("/globle/api/rollcall/current?region=oceania", jen).get("id").asLong()).isEqualTo(inProgress);
        assertThat(rollCallOptions(jen, "oceania")).isEqualTo(options);

        // Once a recent run missed one, "mine" leaves that one to name, and naming it ends the run.
        finishRollCall(jen, inProgress);
        assertThat(rollCallOptions(jen, "oceania").get("mine").asInt()).isEqualTo(oceania.size() - 1);
        JsonNode run = startRollCall(jen, "oceania", "mine");
        assertThat(run.get("status").asText()).isEqualTo("PLAYING");
        assertThat(run.get("autoCount").asInt()).isEqualTo(oceania.size() - 1);
        clock.advance(Duration.ofSeconds(4));
        JsonNode done = nameIn(jen, run.get("id").asLong(), oceania.get(oceania.size() - 1));
        assertThat(done.get("status").asText()).isEqualTo("FINISHED");
        assertThat(done.get("missed")).isEmpty();
        assertThat(done.get("elapsedSeconds").asLong()).isEqualTo(4);
        assertThat(rollCallOptions(jen, "oceania").get("finishedRuns").asInt()).isEqualTo(4);
    }

    // ------------------------------------------------------------------ time

    @Test
    void elapsedSecondsRunOnTheServerClockAndStopAtTheFinish() throws Exception {
        var jen = player("jen");
        Instant start = clock.instant();
        long id = startRollCall(jen, "asia", "off").get("id").asLong();

        clock.advance(Duration.ofSeconds(754));
        assertThat(getOk("/globle/api/rollcall/current?region=asia", jen).get("elapsedSeconds").asLong())
                .isEqualTo(754);
        clock.advance(Duration.ofMillis(1999));
        JsonNode named = nameIn(jen, id, rollCallPool(Region.ASIA).get(0).code());
        assertThat(named.get("elapsedSeconds").asLong()).as("whole seconds, rounded down").isEqualTo(755);
        assertThat(named.get("startedAt").asText()).isEqualTo(start.toString());

        clock.advance(Duration.ofMillis(1));
        JsonNode finished = finishRollCall(jen, id);
        assertThat(finished.get("elapsedSeconds").asLong()).isEqualTo(756);

        clock.advance(Duration.ofHours(5));
        JsonNode best = rollCallOptions(jen, "asia").get("best");
        assertThat(fields(best)).containsExactly("named", "total", "seconds", "autoCount");
        assertThat(best.get("seconds").asLong()).isEqualTo(756);
        assertThat(best.get("named").asInt()).isEqualTo(1);
        assertThat(best.get("total").asInt()).isEqualTo(rollCallPool(Region.ASIA).size());
        assertThat(best.get("autoCount").asInt()).isZero();
    }

    /**
     * The games table keeps times to the millisecond (epoch millis), and the real clock is
     * finer than that. A run as a POST returns it must be the run a later read returns -
     * the server is the source of truth, and the page redraws from either - and every time
     * stays within the three fraction digits of JavaScript's date-time format.
     */
    @Test
    void aResponseCarriesTheTimesTheRunKeeps() throws Exception {
        var jen = player("jen");
        clock.set(Instant.parse("2026-10-09T16:00:00.123456789Z"));
        JsonNode started = startRollCall(jen, "oceania", "off");
        long id = started.get("id").asLong();
        assertThat(started.get("startedAt").asText()).isEqualTo("2026-10-09T16:00:00.123Z");
        assertThat(getOk("/globle/api/rollcall/current?region=oceania", jen)).isEqualTo(started);

        List<Country> oceania = rollCallPool(Region.OCEANIA);
        clock.advance(Duration.ofMillis(10_500).plusNanos(654_321));
        JsonNode named = nameIn(jen, id, oceania.get(0).code());
        assertThat(getOk("/globle/api/rollcall/current?region=oceania", jen)).isEqualTo(named);

        // Naming the last one finishes the run: the finish is reported as it is stored.
        JsonNode last = null;
        for (Country country : oceania.subList(1, oceania.size())) {
            clock.advance(Duration.ofNanos(333_333_333));
            last = nameIn(jen, id, country.code());
        }
        assertThat(last.get("status").asText()).isEqualTo("FINISHED");
        assertThat(last.get("finishedAt").asText())
                .isEqualTo(games.findById(id).orElseThrow().getFinishedAt().toString())
                .matches("2026-10-09T16:00:\\d\\d\\.\\d{3}Z");

        // So is "I'm done", and the setup card's best says the same time as the end card.
        long europe = startRollCall(jen, "europe", "off").get("id").asLong();
        nameIn(jen, europe, rollCallPool(Region.EUROPE).get(0).code());
        clock.advance(Duration.ofSeconds(42).plusNanos(999_999));
        JsonNode done = finishRollCall(jen, europe);
        assertThat(done.get("finishedAt").asText())
                .isEqualTo(games.findById(europe).orElseThrow().getFinishedAt().toString())
                .matches("2026-10-09T16:\\d\\d:\\d\\d\\.\\d{3}Z");
        assertThat(done.get("startedAt").asText())
                .isEqualTo(games.findById(europe).orElseThrow().getStartedAt().toString());
        assertThat(rollCallOptions(jen, "europe").get("best").get("seconds").asLong())
                .isEqualTo(done.get("elapsedSeconds").asLong()).isEqualTo(42);
    }

    /** Of the runs that count only: a run of nothing but filled-in countries never sets the best. */
    @Test
    void theBestRunIsTheMostNamedThenTheFastest() throws Exception {
        var jen = player("jen");
        List<String> europe = rollCallPool(Region.EUROPE).stream().map(Country::code).toList();
        playTimed(jen, europe.subList(0, 3), 100);
        playTimed(jen, europe.subList(0, 5), 400);
        playTimed(jen, europe.subList(5, 10), 300);
        playTimed(jen, europe.subList(0, 4), 10);
        long filledIn = startRollCall(jen, "europe", "known").get("id").asLong();
        clock.advance(Duration.ofSeconds(1));
        assertThat(finishRollCall(jen, filledIn).get("namedCount").asInt())
                .isEqualTo(rollCallTiers(Region.EUROPE, 2).size()).isGreaterThan(5);

        JsonNode options = rollCallOptions(jen, "europe");
        assertThat(options.get("finishedRuns").asInt()).isEqualTo(4);
        assertThat(options.get("best").get("named").asInt()).isEqualTo(5);
        assertThat(options.get("best").get("seconds").asLong()).isEqualTo(300);
        assertThat(options.get("best").get("autoCount").asInt()).isZero();
    }

    // ------------------------------------------------------------------ who and what

    @Test
    void someoneElsesRunIsNotFound() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        long jens = startRollCall(jen, "world", "off").get("id").asLong();

        post("/globle/api/rollcall/" + jens + "/name", Map.of("code", "FRA"), colleen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        post("/globle/api/rollcall/" + jens + "/finish", null, colleen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        post("/globle/api/rollcall/999999/name", Map.of("code", "FRA"), jen)
                .andExpect(status().isNotFound());
        post("/globle/api/rollcall/999999/finish", null, jen)
                .andExpect(status().isNotFound());
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current").with(colleen))
                .andExpect(status().isNoContent());
        assertThat(rollCallOptions(colleen, "world").get("finishedRuns").asInt()).isZero();

        GlobleGame row = games.findById(jens).orElseThrow();
        assertThat(row.getGuessCount()).isZero();
        assertThat(row.isPlaying()).isTrue();
    }

    /** Ids are shared by every mode; each mode's routes find only their own. */
    @Test
    void rollCallAndGuessingGameIdsDoNotCross() throws Exception {
        var jen = player("jen");
        long run = startRollCall(jen, "world", "off").get("id").asLong();
        long practice = newGame(jen, "country", "world").get("id").asLong();
        guessDaily(jen, farFrom(answerToday()));
        long daily = games.findAll().stream().filter(g -> g.getMode() == Mode.DAILY).findFirst().orElseThrow().getId();

        for (long id : new long[]{practice, daily}) {
            post("/globle/api/rollcall/" + id + "/name", Map.of("code", "FRA"), jen)
                    .andExpect(status().isNotFound())
                    .andExpect(content().json("{\"error\":\"not-found\"}", true));
            post("/globle/api/rollcall/" + id + "/finish", null, jen)
                    .andExpect(status().isNotFound());
        }
        post("/globle/api/unlimited/" + run + "/guess", Map.of("code", "FRA"), jen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        post("/globle/api/unlimited/" + run + "/give-up", null, jen)
                .andExpect(status().isNotFound());

        assertThat(games.findById(run).orElseThrow().getGuessCount()).isZero();
        assertThat(games.findById(practice).orElseThrow().isPlaying()).isTrue();
        assertThat(games.findById(practice).orElseThrow().getGuessCount()).isZero();
        assertThat(getOk("/globle/api/unlimited/current?kind=country", jen).get("id").asLong()).isEqualTo(practice);
    }

    @Test
    void unknownRegionsAndAutofillsAreBadRequests() throws Exception {
        var jen = player("jen");
        for (Map<String, String> body : List.of(
                Map.of("region", "mars"),
                Map.of("region", "antarctica"),
                Map.of("autofill", "all"),
                Map.of("region", "world", "autofill", "everything"))) {
            post("/globle/api/rollcall/new", body, jen)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        }
        for (String url : new String[]{"/globle/api/rollcall/options?region=mars",
                "/globle/api/rollcall/current?region=antarctica"}) {
            mvc.perform(MockMvcRequestBuilders.get(url).with(jen))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("bad-request"));
        }
        post("/globle/api/rollcall/not-a-number/name", Map.of("code", "FRA"), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        postRaw("/globle/api/rollcall/new", "{", jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        assertThat(games.count()).isZero();

        // Case and spaces are forgiven, as for kind and region elsewhere.
        JsonNode started = startRollCall(jen, " Europe ", "EASY");
        assertThat(started.get("region").asText()).isEqualTo("europe");
        assertThat(started.get("autofill").asText()).isEqualTo("easy");
    }

    @Test
    void signedOutRequestsGetA401AndPostsNeedTheCsrfToken() throws Exception {
        for (String url : new String[]{"/globle/api/rollcall/options", "/globle/api/rollcall/current?region=world"}) {
            mvc.perform(MockMvcRequestBuilders.get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"signed-out\"}", true));
        }
        var jen = player("jen");
        long id = startRollCall(jen, "world", "off").get("id").asLong();
        String[] posts = {"/globle/api/rollcall/new", "/globle/api/rollcall/" + id + "/name",
                "/globle/api/rollcall/" + id + "/finish"};
        for (String url : posts) {
            mvc.perform(MockMvcRequestBuilders.post(url)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"signed-out\"}", true));
            mvc.perform(MockMvcRequestBuilders.post(url).with(jen)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(content().json("{\"error\":\"forbidden\"}", true));
        }
        GlobleGame run = games.findById(id).orElseThrow();
        assertThat(run.getGuessCount()).isZero();
        assertThat(run.isPlaying()).isTrue();
        assertThat(games.count()).isEqualTo(1);
    }

    /** No difficulty tier, and nothing about the countries still to name, while playing. */
    @Test
    void aRunInProgressSaysNothingAboutWhatIsLeft() throws Exception {
        var jen = player("jen");
        String body = post("/globle/api/rollcall/new", Map.of("region", "europe", "autofill", "known"), jen)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("difficulty").contains("\"missed\":null");
        List<String> filled = rollCallTiers(Region.EUROPE, 2);
        for (Country country : rollCallPool(Region.EUROPE)) {
            if (!filled.contains(country.code())) {
                assertThat(body).doesNotContain("\"" + country.code() + "\"");
            }
        }
        String options = mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/options?region=europe")
                .with(jen)).andReturn().getResponse().getContentAsString();
        assertThat(options).doesNotContain("\"FRA\"").doesNotContain("difficulty");
    }

    // ------------------------------------------------------------------ helpers

    /** A POST whose body is sent exactly as given, for malformed JSON. */
    private ResultActions postRaw(String url, String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void refused(RequestPostProcessor who, long id, Object body, int status, String code) throws Exception {
        post("/globle/api/rollcall/" + id + "/name", body, who)
                .andExpect(status().is(status))
                .andExpect(content().json("{\"error\":\"" + code + "\"}", true));
    }

    private void mineRefused(RequestPostProcessor who, String region) throws Exception {
        post("/globle/api/rollcall/new", Map.of("region", region, "autofill", "mine"), who)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
    }

    /** A finished run in a region that named exactly {@code codes}, typed. */
    private void play(RequestPostProcessor who, String region, List<String> codes) throws Exception {
        long id = startRollCall(who, region, "off").get("id").asLong();
        JsonNode state = null;
        for (String code : codes) {
            state = nameIn(who, id, code);
        }
        if (state == null || state.get("status").asText().equals("PLAYING")) {
            finishRollCall(who, id);
        }
    }

    /** A run started with {@code autofill} and ended with "I'm done" before anything was typed. */
    private void doneWithNothingTyped(RequestPostProcessor who, String region, String autofill) throws Exception {
        JsonNode started = startRollCall(who, region, autofill);
        JsonNode finished = finishRollCall(who, started.get("id").asLong());
        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("namedCount").asInt()).isEqualTo(finished.get("autoCount").asInt());
    }

    /** A finished Europe run naming {@code codes} that took {@code seconds}. */
    private void playTimed(RequestPostProcessor who, List<String> codes, int seconds) throws Exception {
        long id = startRollCall(who, "europe", "off").get("id").asLong();
        for (String code : codes) {
            nameIn(who, id, code);
        }
        clock.advance(Duration.ofSeconds(seconds));
        finishRollCall(who, id);
    }

    /** The {@code n}th country of the region's pool that "easy" does not fill in. */
    private Country notEasy(Region region, int n) {
        return notTier(region, 1, n);
    }

    /** The {@code n}th country of the region's pool above difficulty {@code tier}. */
    private Country notTier(Region region, int tier, int n) {
        return rollCallPool(region).stream().filter(c -> c.difficulty() > tier).skip(n).findFirst().orElseThrow();
    }

    private static List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static List<String> texts(JsonNode array, String field) {
        return StreamSupport.stream(array.spliterator(), false).map(n -> n.get(field).asText()).toList();
    }
}

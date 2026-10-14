package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Name them all" on the leaderboard and the home screen, and nowhere else: its rows
 * share the games table with the dailies and practice, and must never leak into their
 * boards, streaks, home summary or slots.
 */
class RollCallBoardsTest extends GlobleTestBase {

    @Test
    void theBoardRanksFinishedWorldRunsByMostNamedThenFastest() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var ryan = player("ryan");
        var sam = player("sam");
        var alex = player("alex");
        var bob = player("bob");

        worldRun(jen, "off", 5, 100);
        worldRun(jen, "easy", 2, 300);           // 32 named, 30 of them filled in
        worldRun(colleen, "off", 32, 200);       // 32 too, faster
        worldRun(ryan, "off", 32, 200);          // same count and time: by username
        worldRun(ryan, "off", 10, 50);
        long samsEurope = startRollCall(sam, "europe", "off").get("id").asLong();
        nameIn(sam, samsEurope, rollCallPool(Region.EUROPE).get(0).code());
        finishRollCall(sam, samsEurope);         // not a world run: not on the board
        long alexs = startRollCall(alex, "world", "known").get("id").asLong();
        nameIn(alex, alexs, notTier(Region.WORLD, 2, 0));   // in progress: not on the board
        worldRun(bob, "off", 100, 10);
        users.delete(users.findByUsername("bob").orElseThrow());

        JsonNode rollcall = board(jen).get("rollcall");

        assertThat(names(rollcall)).containsExactly("colleen", "ryan", "jen");
        JsonNode jens = rollcall.get(2);
        List<String> fields = new ArrayList<>();
        jens.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("username", "played", "best", "total", "bestSeconds", "bestAutoCount",
                "you");
        assertThat(jens.get("played").asInt()).isEqualTo(2);
        assertThat(jens.get("best").asInt()).isEqualTo(32);
        assertThat(jens.get("total").asInt()).isEqualTo(rollCallPool(Region.WORLD).size()).isEqualTo(197);
        assertThat(jens.get("bestSeconds").asLong()).isEqualTo(300);
        assertThat(jens.get("bestAutoCount").asInt()).isEqualTo(30);
        assertThat(jens.get("you").asBoolean()).isTrue();

        JsonNode ryans = rollcall.get(1);
        assertThat(ryans.get("played").asInt()).isEqualTo(2);
        assertThat(ryans.get("best").asInt()).isEqualTo(32);
        assertThat(ryans.get("bestSeconds").asLong()).isEqualTo(200);
        assertThat(ryans.get("bestAutoCount").asInt()).isZero();
        assertThat(ryans.get("you").asBoolean()).isFalse();
        assertThat(rollcall.get(0).get("bestSeconds").asLong()).isEqualTo(200);

        // The same board whichever daily the request names.
        assertThat(board(jen, Variant.HARD).get("rollcall")).isEqualTo(rollcall);
        assertThat(board(colleen).get("rollcall").get(0).get("you").asBoolean()).isTrue();
        // Bob's runs are still stored, just not shown.
        assertThat(games.findAll().stream().filter(g -> g.getUsername().equals("bob"))).hasSize(1);
    }

    /** Among a player's runs the best is the most named, and of those the fastest. */
    @Test
    void aPlayersRowShowsTheirBestRun() throws Exception {
        var jen = player("jen");
        worldRun(jen, "off", 12, 400);
        worldRun(jen, "off", 12, 250);
        worldRun(jen, "off", 11, 5);
        worldRun(jen, "off", 3, 1);

        JsonNode row = only(board(jen).get("rollcall"));
        assertThat(row.get("played").asInt()).isEqualTo(4);
        assertThat(row.get("best").asInt()).isEqualTo(12);
        assertThat(row.get("bestSeconds").asLong()).isEqualTo(250);
    }

    /**
     * A whole-world run with nothing typed - ended empty, or only filled in - is on the
     * board nowhere: not in played, and not as the best, even when it names more than
     * any run that counts. A player with only such runs has no row.
     */
    @Test
    void runsWithNothingTypedAreNotOnTheBoard() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        worldRun(jen, "off", 0, 10);         // ended empty
        worldRun(jen, "known", 0, 20);       // 70 filled in, nothing typed
        worldRun(jen, "off", 3, 50);
        worldRun(colleen, "easy", 0, 5);
        worldRun(colleen, "off", 0, 1);

        JsonNode row = only(board(jen).get("rollcall"));
        assertThat(row.get("username").asText()).isEqualTo("jen");
        assertThat(row.get("played").asInt()).isEqualTo(1);
        assertThat(row.get("best").asInt()).isEqualTo(3);
        assertThat(row.get("bestSeconds").asLong()).isEqualTo(50);
        assertThat(row.get("bestAutoCount").asInt()).isZero();
        // Every run is still kept, finished.
        assertThat(games.findAll()).hasSize(5).allMatch(g -> g.getStatus() == Status.FINISHED);

        // One typed name puts a filled-in run on the board, filled-in countries and all.
        worldRun(colleen, "known", 1, 30);
        JsonNode rollcall = board(jen).get("rollcall");
        assertThat(names(rollcall)).containsExactly("colleen", "jen");
        assertThat(rollcall.get(0).get("played").asInt()).isEqualTo(1);
        assertThat(rollcall.get(0).get("best").asInt()).isEqualTo(71);
        assertThat(rollcall.get(0).get("bestAutoCount").asInt()).isEqualTo(70);
        assertThat(rollcall.get(1).get("played").asInt()).isEqualTo(1);
    }

    /**
     * An account that reuses a deleted one's id does not inherit its runs (SQLite's
     * users.id has no AUTOINCREMENT, so the next account can get the deleted one's id).
     * Every per-player lookup matches the username as well as the id: the finished
     * runs (board, options), and the run in progress (current, home, name, finish, and
     * the one a start abandons).
     */
    @Test
    void aReusedIdDoesNotInheritRuns() throws Exception {
        var jen = player("jen");
        // A run that counts (a typed name), so only the owner check can keep it off.
        GlobleGame ghost = GlobleGame.rollCall(new Player(idOf("jen"), "ghost"),
                Region.WORLD, Autofill.EASY, rollCallTiers(Region.WORLD, 1), clock.instant());
        ghost.addGuess(notTier(Region.WORLD, 1, 0));
        ghost.finish(Status.FINISHED, clock.instant());
        assertThat(games.save(ghost).counts()).isTrue();

        assertThat(board(jen).get("rollcall")).isEmpty();
        assertThat(rollCallOptions(jen, "world").get("finishedRuns").asInt()).isZero();

        // A run the ghost left in progress: only the username check keeps it from Jen.
        long ghostRun = games.save(GlobleGame.rollCall(new Player(idOf("jen"), "ghost"),
                Region.EUROPE, Autofill.OFF, List.of(), clock.instant())).getId();

        mvc.perform(MockMvcRequestBuilders.get("/globle/api/rollcall/current?region=europe").with(jen))
                .andExpect(status().isNoContent());
        assertThat(home(jen).get("rollcall")).isEmpty();
        post("/globle/api/rollcall/" + ghostRun + "/name", Map.of("code", "FRA"), jen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        post("/globle/api/rollcall/" + ghostRun + "/finish", null, jen)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        // Starting her own Europe run must not abandon (delete or finish) the ghost's.
        long mine = startRollCall(jen, "europe", "off").get("id").asLong();
        assertThat(mine).isNotEqualTo(ghostRun);

        GlobleGame row = games.findById(ghostRun).orElseThrow();
        assertThat(row.getGuessCount()).isZero();
        assertThat(row.isPlaying()).isTrue();
        assertThat(row.getUsername()).isEqualTo("ghost");
    }

    @Test
    void homeListsRunsInProgressNewestFirst() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        long world = startRollCall(jen, "world", "off").get("id").asLong();
        nameIn(jen, world, "FRA");
        nameIn(jen, world, "BRA");
        long africa = startRollCall(jen, "africa", "off").get("id").asLong();
        finishRollCall(jen, africa);
        long europe = startRollCall(jen, "europe", "easy").get("id").asLong();
        long practice = newGame(jen, "country", "world").get("id").asLong();
        startRollCall(colleen, "asia", "off");

        JsonNode home = home(jen);

        JsonNode rollcall = home.get("rollcall");
        assertThat(rollcall).extracting(r -> r.get("id").asLong()).containsExactly(europe, world);
        List<String> fields = new ArrayList<>();
        rollcall.get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("id", "region", "namedCount", "total");
        assertThat(rollcall.get(0).get("region").asText()).isEqualTo("europe");
        assertThat(rollcall.get(0).get("namedCount").asInt()).isEqualTo(rollCallTiers(Region.EUROPE, 1).size());
        assertThat(rollcall.get(0).get("total").asInt()).isEqualTo(rollCallPool(Region.EUROPE).size());
        assertThat(rollcall.get(1).get("region").asText()).isEqualTo("world");
        assertThat(rollcall.get(1).get("namedCount").asInt()).isEqualTo(2);
        assertThat(rollcall.get(1).get("total").asInt()).isEqualTo(197);

        // Practice in progress is listed as before, and only practice.
        assertThat(home.get("inProgress")).extracting(g -> g.get("id").asLong()).containsExactly(practice);
    }

    /**
     * Roll call rows sit in the same table as everything else. Every daily and practice
     * board, the stats, the streaks, the home summary and the practice slots must read
     * exactly as if they were not there.
     */
    @Test
    void rollCallRunsTouchNoOtherBoardStreakOrSlot() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        for (int puzzle = 1; puzzle <= 3; puzzle++) {
            clock.setDay(day(puzzle));
            JsonNode before = withoutRollCall(board(jen));
            JsonNode hardBefore = withoutRollCall(board(jen, Variant.HARD));
            JsonNode homeBefore = withoutRollCall(home(jen));

            worldRun(jen, "easy", 3, 60);
            long europe = startRollCall(jen, "europe", "off").get("id").asLong();
            nameIn(jen, europe, rollCallPool(Region.EUROPE).get(puzzle).code());
            worldRun(colleen, "off", 2, 30);

            assertThat(withoutRollCall(board(jen))).as("puzzle %d board", puzzle).isEqualTo(before);
            assertThat(withoutRollCall(board(jen, Variant.HARD))).as("puzzle %d Hard board", puzzle)
                    .isEqualTo(hardBefore);
            assertThat(withoutRollCall(home(jen))).as("puzzle %d home", puzzle).isEqualTo(homeBefore);

            winDailyIn(jen, 2);
        }
        JsonNode board = board(jen);
        assertThat(board.get("me").get("played").asInt()).isEqualTo(3);
        assertThat(board.get("me").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(board.get("today").get("entries")).hasSize(1);
        assertThat(board.get("unlimited").get("country")).isEmpty();
        assertThat(board.get("unlimited").get("capital")).isEmpty();
        assertThat(board.get("unlimited").get("hard")).isEmpty();
        JsonNode home = home(jen);
        assertThat(home.get("inProgress")).isEmpty();
        assertThat(home.get("classic").get("currentStreak").asInt()).isEqualTo(3);
        assertThat(home.get("hard").get("status").asText()).isEqualTo("NEW");
        for (String query : new String[]{"kind=country", "kind=country&region=world", "kind=country&region=europe",
                "kind=capital", "kind=country&variant=hard"}) {
            mvc.perform(MockMvcRequestBuilders.get("/globle/api/unlimited/current?" + query).with(jen))
                    .andExpect(status().isNoContent());
        }
        assertThat(board.get("rollcall")).extracting(r -> r.get("username").asText())
                .containsExactly("jen", "colleen");
    }

    /** Starting either kind of game never abandons the other, even in the same region. */
    @Test
    void practiceSlotsAndRollCallRunsDoNotAbandonEachOther() throws Exception {
        var jen = player("jen");
        long practice = newGame(jen, "country", "europe").get("id").asLong();
        guessUnlimited(jen, practice, farFrom(answerOf(practice)));
        long run = startRollCall(jen, "europe", "off").get("id").asLong();
        nameIn(jen, run, rollCallPool(Region.EUROPE).get(0).code());

        long secondRun = startRollCall(jen, "europe", "off").get("id").asLong();
        assertThat(games.findById(practice).orElseThrow().isPlaying()).isTrue();
        assertThat(games.findById(run).orElseThrow().getStatus()).isEqualTo(Status.FINISHED);

        long secondPractice = newGame(jen, "country", "europe").get("id").asLong();
        assertThat(games.findById(practice).orElseThrow().getStatus()).isEqualTo(Status.GAVE_UP);
        assertThat(games.findById(secondRun).orElseThrow().isPlaying()).isTrue();
        assertThat(getOk("/globle/api/unlimited/current?kind=country&region=europe", jen).get("id").asLong())
                .isEqualTo(secondPractice);
        assertThat(getOk("/globle/api/rollcall/current?region=europe", jen).get("id").asLong()).isEqualTo(secondRun);

        // The practice board counts the given-up game, and nothing from roll call.
        JsonNode country = only(board(jen).get("unlimited").get("country"));
        assertThat(country.get("played").asInt()).isEqualTo(1);
        assertThat(country.get("wins").asInt()).isZero();
    }

    @Test
    void aNewPlayersBoardHasAnEmptyRollCallBoard() throws Exception {
        var jen = player("jen");
        JsonNode board = board(jen);
        List<String> fields = new ArrayList<>();
        board.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("variant", "today", "week", "allTime", "unlimited", "me", "rollcall");
        assertThat(board.get("rollcall")).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    /** A finished whole-world run: the autofill, then {@code typed} more names, taking {@code seconds}. */
    private void worldRun(RequestPostProcessor who, String autofill, int typed, int seconds) throws Exception {
        JsonNode started = startRollCall(who, "world", autofill);
        long id = started.get("id").asLong();
        List<String> already = StreamSupport.stream(started.get("named").spliterator(), false)
                .map(n -> n.get("code").asText()).toList();
        List<String> toName = rollCallPool(Region.WORLD).stream().map(Country::code)
                .filter(code -> !already.contains(code))
                .limit(typed)
                .toList();
        for (String code : toName) {
            nameIn(who, id, code);
        }
        clock.advance(Duration.ofSeconds(seconds));
        finishRollCall(who, id);
    }

    /** The {@code n}th country of the pool above difficulty {@code tier}. */
    private String notTier(Region region, int tier, int n) {
        return rollCallPool(region).stream().filter(c -> c.difficulty() > tier).skip(n).findFirst().orElseThrow()
                .code();
    }

    /** A board or home summary with its roll call section taken out, to compare the rest. */
    private static JsonNode withoutRollCall(JsonNode body) {
        JsonNode copy = body.deepCopy();
        ((ObjectNode) copy).remove("rollcall");
        return copy;
    }

    private static List<String> names(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false).map(r -> r.get("username").asText()).toList();
    }

    private static JsonNode only(JsonNode rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}

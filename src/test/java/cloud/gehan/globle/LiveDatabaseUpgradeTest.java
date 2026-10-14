package cloud.gehan.globle;

import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.service.DailyPuzzles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production's database may predate Hard. This boots the app, as production does
 * ({@code ddl-auto=update} on SQLite), on a file holding exactly what the first
 * release of Globle (59ea8d8) created: its schema, copied from a database that release
 * built, and the kinds of rows it wrote. The upgrade may only add - the variant column,
 * the Hard table, and roll call's two nullable columns - and every old game must carry
 * on as the classic game it was. {@link HardReleaseUpgradeTest} does the same from the
 * Hard release's schema.
 */
@SpringBootTest(properties = {
        "globle.enabled=true",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
@Import(LiveDatabaseUpgradeTest.TestClock.class)
@DirtiesContext
class LiveDatabaseUpgradeTest {

    /** `sqlite3 users.db .schema` on a database created by 59ea8d8. */
    private static final List<String> RELEASED_SCHEMA = List.of(
            "CREATE TABLE globle_day (puzzle_date varchar(10) not null, country_code varchar(8) not null,"
                    + " created_at bigint not null, puzzle_number integer not null, primary key (puzzle_date))",
            "CREATE TABLE globle_game (id integer, answer_code varchar(8), daily_key varchar(128) unique,"
                    + " finished_at bigint, guess_count integer not null, guesses varchar(4000) not null,"
                    + " kind varchar(16) not null, mode varchar(16) not null, puzzle_date varchar(255),"
                    + " region varchar(16) not null, started_at bigint not null, status varchar(16) not null,"
                    + " user_id bigint not null, username varchar(64) not null, primary key (id))",
            "CREATE TABLE users (id integer, password varchar(255), role varchar(255),"
                    + " username varchar(255) unique, primary key (id))",
            "CREATE INDEX ix_globle_game_player on globle_game (user_id, mode, kind, status)",
            "CREATE INDEX ix_globle_game_date on globle_game (puzzle_date)");

    /** What that release wrote: two players' dailies over two days, and practice. */
    private static final List<String> RELEASED_ROWS = List.of(
            "insert into users (id, password, role, username) values (1, null, 'ADMIN', 'admin'),"
                    + " (2, null, 'USER', 'jen'), (3, null, 'USER', 'colleen')",
            "insert into globle_day values ('2026-10-08', 'PER', 1791500000000, 0),"
                    + " ('2026-10-09', 'FJI', 1791589930924, 1)",
            "insert into globle_game (id, user_id, username, mode, kind, region, puzzle_date, daily_key,"
                    + " answer_code, status, guesses, guess_count, started_at, finished_at) values"
                    + " (1, 2, 'jen', 'DAILY', 'COUNTRY', 'WORLD', '2026-10-09', '2/jen/2026-10-09', null,"
                    + "  'WON', 'MNG,FJI', 2, 1791589930900, 1791589931010),"
                    + " (2, 3, 'colleen', 'DAILY', 'COUNTRY', 'WORLD', '2026-10-09', '3/colleen/2026-10-09', null,"
                    + "  'GAVE_UP', 'BRA', 1, 1791589931000, 1791589931060),"
                    + " (3, 2, 'jen', 'UNLIMITED', 'COUNTRY', 'WORLD', null, null, 'CUB',"
                    + "  'WON', 'CUB', 1, 1791589932000, 1791589932100),"
                    + " (4, 2, 'jen', 'UNLIMITED', 'COUNTRY', 'EUROPE', null, null, 'DEU',"
                    + "  'PLAYING', 'JPN,BRA,AUS', 3, 1791589933000, null),"
                    + " (5, 3, 'colleen', 'UNLIMITED', 'CAPITAL', 'WORLD', null, null, 'UZB',"
                    + "  'WON', 'FRA,UZB', 2, 1791589934000, 1791589934100),"
                    + " (6, 2, 'jen', 'DAILY', 'COUNTRY', 'WORLD', '2026-10-08', '2/jen/2026-10-08', null,"
                    + "  'WON', 'BRA,PER', 2, 1791500000000, 1791500100000)");

    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);

    private static final Path DATA = releasedDatabase();

    @TestConfiguration
    static class TestClock {
        @Bean
        MovableClock globleTimeSource() {
            return new MovableClock();
        }
    }

    @DynamicPropertySource
    static void sqlite(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DATA.resolve("users.db"));
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MovableClock clock;

    @Autowired
    private DailyPuzzles puzzles;

    @Autowired
    private GlobleGameRepository games;

    private final RequestPostProcessor jen = user("jen").roles("USER");
    private final RequestPostProcessor colleen = user("colleen").roles("USER");

    @BeforeEach
    void today() {
        clock.setDay(DAY);
    }

    @Test
    void theUpgradeOnlyAddsNullableColumnsAndTables() {
        List<Map<String, Object>> columns = jdbc.queryForList("pragma table_info(globle_game)");
        // The three missing columns are added at the end, in Hibernate's (alphabetical) order.
        assertThat(columns).extracting(c -> c.get("name")).containsExactly(
                "id", "answer_code", "daily_key", "finished_at", "guess_count", "guesses", "kind", "mode",
                "puzzle_date", "region", "started_at", "status", "user_id", "username",
                "autofill", "autofill_count", "variant");
        for (Map<String, Object> added : columns.subList(columns.size() - 3, columns.size())) {
            assertThat(((Number) added.get("notnull")).intValue()).as("%s", added.get("name")).isZero();
            assertThat(added.get("dflt_value")).as("%s", added.get("name")).isNull();
        }
        assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'globle_game'", String.class))
                .contains("daily_key varchar(128) unique").contains("primary key (id)");

        assertThat(jdbc.queryForList("pragma table_info(globle_day)")).extracting(c -> c.get("name"))
                .containsExactly("puzzle_date", "country_code", "created_at", "puzzle_number");
        assertThat(jdbc.queryForList("pragma table_info(globle_hard_day)")).extracting(c -> c.get("name"))
                .containsExactlyInAnyOrder("puzzle_date", "country_code", "created_at", "puzzle_number");
        assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'globle_hard_day'", String.class))
                .contains("primary key (puzzle_date)");
        assertThat(jdbc.queryForList("select name from sqlite_master where type = 'table' order by name"))
                .extracting(r -> r.get("name"))
                .containsExactly("globle_day", "globle_game", "globle_hard_day", "globle_name_request", "users");

        // The name requests' table is new: created whole, its two empty-while-open columns nullable.
        List<Map<String, Object>> requestColumns = jdbc.queryForList("pragma table_info(globle_name_request)");
        assertThat(requestColumns).extracting(c -> c.get("name")).containsExactlyInAnyOrder(
                "id", "user_id", "username", "code", "suggestion", "lang", "source", "created_at", "status",
                "resolved_at", "resolved_by");
        assertThat(requestColumns).filteredOn(c -> ((Number) c.get("notnull")).intValue() == 0)
                .extracting(c -> c.get("name")).containsExactlyInAnyOrder("id", "resolved_at", "resolved_by");
        // Plain columns only: no CHECK constraint (enums go through converters) and no index or foreign key.
        assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'globle_name_request'", String.class))
                .doesNotContainIgnoringCase("check").doesNotContainIgnoringCase("references")
                .contains("primary key (id)");
        assertThat(jdbc.queryForObject("select count(*) from sqlite_master where tbl_name = 'globle_name_request'"
                + " and type = 'index' and sql is not null", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from globle_name_request", Integer.class)).isZero();

        // Nothing rewrote the old rows.
        assertThat(jdbc.queryForObject("select count(*) from globle_game where variant is null and id <= 6",
                Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from globle_game"
                + " where autofill is null and autofill_count is null and id <= 6", Integer.class)).isEqualTo(6);
        GlobleGame old = games.findById(4L).orElseThrow();
        assertThat(old.getAutofill()).isEqualTo(Autofill.OFF);
        assertThat(old.getAutofillCount()).isNull();
        assertThat(old.autoCount()).isZero();
        assertThat(old.guessCodes()).containsExactly("JPN", "BRA", "AUS");
    }

    /**
     * "Name them all" works on the upgraded file, in the new columns, and touches none
     * of the old games. Only Jen plays it, and no other test here looks at roll call,
     * so the order the tests run in does not matter.
     */
    @Test
    void nameThemAllWorksBesideTheOldGames() throws Exception {
        JsonNode started = postOk("/globle/api/rollcall/new", "{\"region\":\"world\",\"autofill\":\"easy\"}", jen);
        long id = started.get("id").asLong();
        assertThat(id).isGreaterThan(6);
        assertThat(started.get("autoCount").asInt()).isEqualTo(30);
        assertThat(jdbc.queryForMap("select mode, kind, region, variant, status, autofill, autofill_count,"
                + " answer_code, daily_key from globle_game where id = ?", id))
                .containsEntry("mode", "ROLLCALL").containsEntry("kind", "COUNTRY").containsEntry("region", "WORLD")
                .containsEntry("variant", "CLASSIC").containsEntry("status", "PLAYING")
                .containsEntry("autofill", "EASY").containsEntry("autofill_count", 30)
                .containsEntry("answer_code", null).containsEntry("daily_key", null);

        JsonNode named = postOk("/globle/api/rollcall/" + id + "/name", "{\"code\":\"MNG\"}", jen);
        assertThat(named.get("namedCount").asInt()).isEqualTo(31);
        assertThat(getOk("/globle/api/home", jen).get("rollcall").get(0).get("id").asLong()).isEqualTo(id);
        assertThat(getOk("/globle/api/home", jen).get("inProgress")).extracting(g -> g.get("id").asLong())
                .containsExactly(4L);
        // The old practice game's id is not a roll call run, nor the run a practice game.
        mvc.perform(post("/globle/api/rollcall/4/finish").with(jen).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/globle/api/unlimited/" + id + "/give-up").with(jen).with(csrf()))
                .andExpect(status().isNotFound());

        JsonNode finished = postOk("/globle/api/rollcall/" + id + "/finish", null, jen);
        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        JsonNode board = getOk("/globle/api/leaderboard", jen);
        assertThat(board.get("rollcall")).hasSize(1);
        assertThat(board.get("rollcall").get(0).get("username").asText()).isEqualTo("jen");
        assertThat(board.get("rollcall").get(0).get("best").asInt()).isEqualTo(31);
        assertThat(board.get("me").get("played").asInt()).isEqualTo(2);
        assertThat(board.get("unlimited").get("country")).hasSize(1);
        assertThat(board.get("unlimited").get("country").get(0).get("played").asInt()).isEqualTo(1);
        assertThat(getOk("/globle/api/rollcall/options?region=world", jen).get("finishedRuns").asInt()).isEqualTo(1);
    }

    @Test
    void oldDailiesStreaksAndBoardsAreIntactAndClassic() throws Exception {
        JsonNode daily = getOk("/globle/api/daily", jen);
        assertThat(daily.get("status").asText()).isEqualTo("WON");
        assertThat(daily.get("variant").asText()).isEqualTo("classic");
        assertThat(daily.get("answer").get("code").asText()).isEqualTo("FJI");
        assertThat(getOk("/globle/api/daily", colleen).get("status").asText()).isEqualTo("GAVE_UP");

        JsonNode board = getOk("/globle/api/leaderboard", jen);
        assertThat(board.get("variant").asText()).isEqualTo("classic");
        assertThat(board.get("me").get("currentStreak").asInt()).isEqualTo(2);
        assertThat(board.get("me").get("played").asInt()).isEqualTo(2);
        assertThat(board.get("today").get("entries")).hasSize(2);
        assertThat(board.get("unlimited").get("country").get(0).get("username").asText()).isEqualTo("jen");
        assertThat(board.get("unlimited").get("capital").get(0).get("username").asText()).isEqualTo("colleen");
        assertThat(board.get("unlimited").get("hard")).isEmpty();

        // The old row's key still holds the day: no second classic game for Jen.
        mvc.perform(post("/globle/api/daily/guess").with(jen).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"BRA\",\"date\":\"2026-10-09\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("finished"));
    }

    @Test
    void theOldPracticeGameResumesAndHardWorksBesideIt() throws Exception {
        JsonNode legacy = getOk("/globle/api/unlimited/current?kind=country", jen);
        assertThat(legacy.get("id").asLong()).isEqualTo(4);
        assertThat(legacy.get("region").asText()).isEqualTo("europe");
        assertThat(legacy.get("guessCount").asInt()).isEqualTo(3);
        assertThat(legacy.get("answer").isNull()).isTrue();
        assertThat(getOk("/globle/api/unlimited/current?kind=country&region=europe&variant=classic", jen))
                .isEqualTo(legacy);

        JsonNode home = getOk("/globle/api/home", jen);
        assertThat(home.get("classic").get("status").asText()).isEqualTo("WON");
        assertThat(home.get("classic").get("currentStreak").asInt()).isEqualTo(2);
        assertThat(home.get("hard").get("status").asText()).isEqualTo("NEW");
        assertThat(home.get("inProgress")).hasSize(1);
        assertThat(home.get("inProgress").get(0).get("id").asLong()).isEqualTo(4);

        mvc.perform(post("/globle/api/daily/give-up").with(colleen).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"hard\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variant").value("hard"))
                .andExpect(jsonPath("$.status").value("GAVE_UP"));
        String hard = puzzles.answerFor(Variant.HARD, DAY);
        assertThat(hard).isNotEqualTo("FJI");
        assertThat(jdbc.queryForObject("select country_code from globle_hard_day", String.class)).isEqualTo(hard);
        assertThat(jdbc.queryForObject("select daily_key from globle_game where variant = 'HARD'", String.class))
                .isEqualTo("3/colleen/2026-10-09/HARD");
    }

    /**
     * "Request a name change" and "Study what you missed" on the upgraded file. Only the
     * name requests' new table is written, and the roll call run is in Oceania by the
     * admin, which no other test here looks at.
     */
    @Test
    void nameRequestsAndTheStudyWorkOnTheUpgradedFile() throws Exception {
        RequestPostProcessor admin = user("admin").roles("ADMIN");
        JsonNode sent = json.readTree(mvc.perform(post("/globle/api/name-requests").with(jen).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"GBR\",\"suggestion\":\"Great Britain and NI\",\"lang\":\"en\","
                                + "\"source\":\"missed\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/globle/api/name-requests").with(jen).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEU\",\"suggestion\":\"Deutschland\",\"lang\":\"es\","
                                + "\"source\":\"study\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/globle/api/name-requests").with(jen)).andExpect(status().isNotFound());

        JsonNode open = getOk("/globle/api/name-requests", admin);
        assertThat(open).hasSize(1);
        assertThat(open.get(0)).isEqualTo(sent);
        assertThat(getOk("/globle/api/home", admin).get("nameRequests").asInt()).isEqualTo(1);
        assertThat(getOk("/globle/api/home", jen).get("nameRequests").isNull()).isTrue();
        assertThat(postOk("/globle/api/name-requests/" + sent.get("id").asLong() + "/resolve",
                "{\"status\":\"done\"}", admin).get("resolvedBy").asText()).isEqualTo("admin");
        assertThat(jdbc.queryForList("select code, status, resolved_by from globle_name_request order by id"))
                .extracting(r -> r.get("code") + "/" + r.get("status") + "/" + r.get("resolved_by"))
                .containsExactly("GBR/DONE/admin", "DEU/DONE/data");

        long run = postOk("/globle/api/rollcall/new", "{\"region\":\"oceania\"}", admin).get("id").asLong();
        postOk("/globle/api/rollcall/" + run + "/name", "{\"code\":\"AUS\"}", admin);
        postOk("/globle/api/rollcall/" + run + "/finish", null, admin);
        JsonNode study = getOk("/globle/api/rollcall/study?region=oceania", admin);
        assertThat(study.get("runId").asLong()).isEqualTo(run);
        assertThat(study.get("missedCount").asInt()).isEqualTo(13);
        assertThat(study.get("missed").get(0).get("sampleRuns").asInt()).isEqualTo(1);
    }

    private JsonNode getOk(String url, RequestPostProcessor who) throws Exception {
        return json.readTree(mvc.perform(get(url).with(who)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode postOk(String url, String body, RequestPostProcessor who) throws Exception {
        var request = post(url).with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(body);
        }
        return json.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static Path releasedDatabase() {
        try {
            Path dir = Files.createTempDirectory("globle-upgrade");
            Path db = dir.resolve("users.db");
            dir.toFile().deleteOnExit();
            db.toFile().deleteOnExit();
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
                 Statement sql = connection.createStatement()) {
                for (String statement : RELEASED_SCHEMA) {
                    sql.execute(statement);
                }
                for (String statement : RELEASED_ROWS) {
                    sql.execute(statement);
                }
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}

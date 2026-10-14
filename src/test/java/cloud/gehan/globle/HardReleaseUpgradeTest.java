package cloud.gehan.globle;

import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.repository.GlobleGameRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The upgrade from the Hard release (13a0310) to "Name them all", the way production
 * takes it: {@code ddl-auto=update} on a SQLite file that release created, holding the
 * kinds of rows it wrote (both dailies, practice in every slot). The schema below is
 * exactly what that release creates on an empty file. The upgrade may only add the two
 * nullable roll call columns, nothing may rewrite an old row, and every old game must
 * read and count as it did. {@link LiveDatabaseUpgradeTest} does the same from the
 * first release.
 */
@SpringBootTest(properties = {
        "globle.enabled=true",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect",
        "spring.jpa.hibernate.ddl-auto=update"})
@AutoConfigureMockMvc
@Import(HardReleaseUpgradeTest.TestClock.class)
@DirtiesContext
class HardReleaseUpgradeTest {

    /** `select sql from sqlite_master` on an empty file after 13a0310 started on it. */
    private static final List<String> HARD_SCHEMA = List.of(
            "CREATE TABLE globle_day (puzzle_date varchar(10) not null, country_code varchar(8) not null,"
                    + " created_at bigint not null, puzzle_number integer not null, primary key (puzzle_date))",
            "CREATE TABLE globle_game (id integer, answer_code varchar(8), daily_key varchar(128) unique,"
                    + " finished_at bigint, guess_count integer not null, guesses varchar(4000) not null,"
                    + " kind varchar(16) not null, mode varchar(16) not null, puzzle_date varchar(255),"
                    + " region varchar(16) not null, started_at bigint not null, status varchar(16) not null,"
                    + " user_id bigint not null, username varchar(64) not null, variant varchar(16),"
                    + " primary key (id))",
            "CREATE TABLE globle_hard_day (puzzle_date varchar(10) not null, country_code varchar(8) not null,"
                    + " created_at bigint not null, puzzle_number integer not null, primary key (puzzle_date))",
            "CREATE INDEX ix_globle_game_date on globle_game (puzzle_date)",
            "CREATE INDEX ix_globle_game_player on globle_game (user_id, mode, kind, status)",
            "CREATE TABLE users (id integer, password varchar(255), role varchar(255),"
                    + " username varchar(255) unique, primary key (id))");

    /** What that release wrote: both dailies over two days, and practice in several slots. */
    private static final List<String> HARD_ROWS = List.of(
            "insert into users (id, password, role, username) values (1, null, 'ADMIN', 'admin'),"
                    + " (2, null, 'USER', 'jen'), (3, null, 'USER', 'colleen')",
            "insert into globle_day values ('2026-10-08', 'PER', 1791500000000, 0),"
                    + " ('2026-10-09', 'FJI', 1791589930924, 1)",
            "insert into globle_hard_day values ('2026-10-09', 'BTN', 1791589930950, 1)",
            "insert into globle_game (id, user_id, username, mode, kind, region, variant, puzzle_date, daily_key,"
                    + " answer_code, status, guesses, guess_count, started_at, finished_at) values"
                    + " (1, 2, 'jen', 'DAILY', 'COUNTRY', 'WORLD', 'CLASSIC', '2026-10-09', '2/jen/2026-10-09', null,"
                    + "  'WON', 'MNG,FJI', 2, 1791589930900, 1791589931010),"
                    + " (2, 2, 'jen', 'DAILY', 'COUNTRY', 'WORLD', 'HARD', '2026-10-09', '2/jen/2026-10-09/HARD',"
                    + "  null, 'GAVE_UP', 'BRA', 1, 1791589931000, 1791589931060),"
                    + " (3, 3, 'colleen', 'DAILY', 'COUNTRY', 'WORLD', 'HARD', '2026-10-09',"
                    + "  '3/colleen/2026-10-09/HARD', null, 'WON', 'NPL,BTN', 2, 1791589931100, 1791589931200),"
                    + " (4, 2, 'jen', 'UNLIMITED', 'COUNTRY', 'EUROPE', 'CLASSIC', null, null, 'DEU',"
                    + "  'PLAYING', 'JPN,BRA,AUS', 3, 1791589933000, null),"
                    + " (5, 2, 'jen', 'UNLIMITED', 'COUNTRY', 'MAINLAND', 'CLASSIC', null, null, 'CHN',"
                    + "  'WON', 'CHN', 1, 1791589934000, 1791589934100),"
                    + " (6, 3, 'colleen', 'UNLIMITED', 'COUNTRY', 'WORLD', 'HARD', null, null, 'TON',"
                    + "  'GAVE_UP', 'FJI', 1, 1791589935000, 1791589935100),"
                    + " (7, 3, 'colleen', 'UNLIMITED', 'CAPITAL', 'WORLD', 'CLASSIC', null, null, 'UZB',"
                    + "  'PLAYING', 'FRA', 1, 1791589936000, null),"
                    + " (8, 2, 'jen', 'DAILY', 'COUNTRY', 'WORLD', 'CLASSIC', '2026-10-08', '2/jen/2026-10-08', null,"
                    + "  'WON', 'BRA,PER', 2, 1791500000000, 1791500100000)");

    private static final LocalDate DAY = LocalDate.of(2026, 10, 9);

    private static final Path DATA = hardReleaseDatabase();

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
    private GlobleGameRepository games;

    private final RequestPostProcessor jen = user("jen").roles("USER");
    private final RequestPostProcessor colleen = user("colleen").roles("USER");

    @BeforeEach
    void today() {
        clock.setDay(DAY);
    }

    @Test
    void theUpgradeOnlyAddsTwoNullableColumnsAndATable() {
        List<Map<String, Object>> columns = jdbc.queryForList("pragma table_info(globle_game)");
        assertThat(columns).extracting(c -> c.get("name")).containsExactly(
                "id", "answer_code", "daily_key", "finished_at", "guess_count", "guesses", "kind", "mode",
                "puzzle_date", "region", "started_at", "status", "user_id", "username", "variant",
                "autofill", "autofill_count");
        for (Map<String, Object> added : columns.subList(columns.size() - 2, columns.size())) {
            assertThat(((Number) added.get("notnull")).intValue()).as("%s", added.get("name")).isZero();
            assertThat(added.get("dflt_value")).as("%s", added.get("name")).isNull();
        }
        assertThat(jdbc.queryForObject("select sql from sqlite_master where name = 'globle_game'", String.class))
                .startsWith(HARD_SCHEMA.get(1).substring(0, HARD_SCHEMA.get(1).length() - ", primary key (id))".length()))
                .contains("daily_key varchar(128) unique").contains("primary key (id)");
        for (String table : new String[]{"globle_day", "globle_hard_day", "users"}) {
            String created = HARD_SCHEMA.stream().filter(s -> s.startsWith("CREATE TABLE " + table + " "))
                    .findFirst().orElseThrow();
            assertThat(jdbc.queryForObject("select sql from sqlite_master where name = ?", String.class, table))
                    .as(table).isEqualTo(created);
        }
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
        assertThat(jdbc.queryForList("select name from sqlite_master where type = 'index' and name like 'ix_%'"
                + " order by name")).extracting(r -> r.get("name"))
                .containsExactly("ix_globle_game_date", "ix_globle_game_player");

        // Nothing rewrote the old rows.
        assertThat(jdbc.queryForObject("select count(*) from globle_game"
                + " where autofill is null and autofill_count is null and id <= 8", Integer.class)).isEqualTo(8);
        assertThat(jdbc.queryForObject("select count(*) from globle_game where variant = 'HARD' and id <= 8",
                Integer.class)).isEqualTo(3);
        for (GlobleGame old : games.findAllById(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L))) {
            assertThat(old.getAutofill()).isEqualTo(Autofill.OFF);
            assertThat(old.getAutofillCount()).isNull();
            assertThat(old.autoCount()).isZero();
        }
    }

    @Test
    void oldGamesReadAndCountAsTheyDid() throws Exception {
        JsonNode classic = getOk("/globle/api/daily", jen);
        assertThat(classic.get("status").asText()).isEqualTo("WON");
        assertThat(classic.get("answer").get("code").asText()).isEqualTo("FJI");
        JsonNode hard = getOk("/globle/api/daily?variant=hard", jen);
        assertThat(hard.get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(hard.get("answer").get("code").asText()).isEqualTo("BTN");
        assertThat(getOk("/globle/api/daily?variant=hard", colleen).get("status").asText()).isEqualTo("WON");

        JsonNode board = getOk("/globle/api/leaderboard", jen);
        assertThat(board.get("me").get("currentStreak").asInt()).isEqualTo(2);
        assertThat(board.get("me").get("played").asInt()).isEqualTo(2);
        assertThat(board.get("unlimited").get("country")).hasSize(1);
        assertThat(board.get("unlimited").get("country").get(0).get("username").asText()).isEqualTo("jen");
        assertThat(board.get("unlimited").get("country").get(0).get("wins").asInt()).isEqualTo(1);
        assertThat(board.get("unlimited").get("hard").get(0).get("username").asText()).isEqualTo("colleen");
        assertThat(board.get("unlimited").get("capital")).isEmpty();
        assertThat(board.get("rollcall")).noneMatch(row -> row.get("username").asText().equals("colleen"));
        JsonNode hardBoard = getOk("/globle/api/leaderboard?variant=hard", jen);
        assertThat(hardBoard.get("today").get("entries")).extracting(e -> e.get("username").asText())
                .containsExactly("colleen", "jen");

        JsonNode home = getOk("/globle/api/home", jen);
        assertThat(home.get("classic").get("status").asText()).isEqualTo("WON");
        assertThat(home.get("classic").get("currentStreak").asInt()).isEqualTo(2);
        assertThat(home.get("hard").get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(home.get("inProgress")).extracting(g -> g.get("id").asLong()).containsExactly(4L);
        JsonNode colleensHome = getOk("/globle/api/home", colleen);
        assertThat(colleensHome.get("inProgress")).extracting(g -> g.get("id").asLong()).containsExactly(7L);
        assertThat(colleensHome.get("rollcall")).isEmpty();

        JsonNode europe = getOk("/globle/api/unlimited/current?kind=country&region=europe", jen);
        assertThat(europe.get("id").asLong()).isEqualTo(4);
        assertThat(europe.get("answer").isNull()).isTrue();
        assertThat(getOk("/globle/api/unlimited/current?kind=capital", colleen).get("id").asLong()).isEqualTo(7);
        assertThat(getOk("/globle/api/rollcall/options?region=world", colleen).get("finishedRuns").asInt()).isZero();
    }

    /** Only Jen plays roll call here, and the test above never asks about hers: order does not matter. */
    @Test
    void nameThemAllWorksBesideTheOldGames() throws Exception {
        JsonNode started = postOk("/globle/api/rollcall/new", "{\"region\":\"europe\",\"autofill\":\"known\"}", jen);
        long id = started.get("id").asLong();
        assertThat(id).isGreaterThan(8);
        int known = started.get("autoCount").asInt();
        assertThat(known).isPositive();
        assertThat(jdbc.queryForMap("select autofill, autofill_count, mode, variant from globle_game where id = ?",
                id)).containsEntry("autofill", "KNOWN").containsEntry("autofill_count", known)
                .containsEntry("mode", "ROLLCALL").containsEntry("variant", "CLASSIC");

        mvc.perform(post("/globle/api/rollcall/" + id + "/name").with(jen).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FJI\"}"))
                .andExpect(status().isBadRequest());
        // One typed name (Moldova is no "known" country), so the run counts once finished.
        assertThat(postOk("/globle/api/rollcall/" + id + "/name", "{\"code\":\"MDA\"}", jen)
                .get("namedCount").asInt()).isEqualTo(known + 1);
        JsonNode finished = postOk("/globle/api/rollcall/" + id + "/finish", null, jen);
        assertThat(finished.get("status").asText()).isEqualTo("FINISHED");
        assertThat(finished.get("missed")).hasSize(finished.get("total").asInt() - known - 1);

        assertThat(getOk("/globle/api/rollcall/options?region=europe", jen).get("finishedRuns").asInt()).isEqualTo(1);
        // A Europe run is not on the whole-world board, and touches no other board.
        JsonNode board = getOk("/globle/api/leaderboard", jen);
        assertThat(board.get("rollcall")).noneMatch(row -> row.get("username").asText().equals("jen"));
        assertThat(board.get("unlimited").get("country").get(0).get("played").asInt()).isEqualTo(1);
        assertThat(getOk("/globle/api/home", jen).get("inProgress")).extracting(g -> g.get("id").asLong())
                .containsExactly(4L);
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

    private static Path hardReleaseDatabase() {
        try {
            Path dir = Files.createTempDirectory("globle-hard-upgrade");
            Path db = dir.resolve("users.db");
            dir.toFile().deleteOnExit();
            db.toFile().deleteOnExit();
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db);
                 Statement sql = connection.createStatement()) {
                for (String statement : HARD_SCHEMA) {
                    sql.execute(statement);
                }
                for (String statement : HARD_ROWS) {
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

package cloud.gehan.globle.service;

import cloud.gehan.globle.MovableClock;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The family playing at the same moment, on the database the app really runs on.
 *
 * <p>The rest of the suite uses H2, whose row-level locking lets two players' writes
 * proceed side by side. SQLite has one writer at a time, and a transaction that has
 * already read fails at once with SQLITE_BUSY - no busy wait - if it tries to write
 * while another connection is writing. Only a real SQLite file shows whether Globle's
 * writes are kept out of each other's way.
 */
@SpringBootTest(properties = {
        "globle.enabled=true",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect"})
@Import(SqliteWritesTest.TestClock.class)
@DirtiesContext
class SqliteWritesTest {

    private static final int PLAYERS = 8;
    private static final Path DATA = temporaryDirectory();

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
    private GameService games;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private CountryCatalog catalog;

    @Autowired
    private MovableClock clock;

    /** Each round is a new day: everyone's first guess, and the day's answer drawn, at once. */
    @Test
    void everyoneStartingTheDailyAtOnceAllLand() throws Exception {
        List<Player> players = players("daily");
        for (int round = 0; round < 5; round++) {
            clock.setDay(LocalDate.of(2026, 10, 9).plusDays(round));
            List<Throwable> failures = atOnce(players, player -> games.guessDaily(player, "FRA", null));
            assertThat(failures).as("round %d", round).isEmpty();
        }
    }

    @Test
    void everyoneGuessingInPracticeAtOnceAllLand() throws Exception {
        List<Player> players = players("practice");
        List<String> codes = catalog.all().stream().map(c -> c.code()).toList();
        for (int round = 0; round < 10; round++) {
            List<Throwable> started = atOnce(players, player -> games.newUnlimited(player, Kind.COUNTRY, Region.WORLD));
            assertThat(started).as("starts in round %d", round).isEmpty();
            String code = codes.get(round);
            List<Throwable> guessed = atOnce(players, player -> {
                long id = games.currentUnlimited(player, Kind.COUNTRY).orElseThrow().id();
                return games.guessUnlimited(player, id, code);
            });
            assertThat(guessed).as("guesses in round %d", round).isEmpty();
        }
    }

    private List<Player> players(String prefix) {
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < PLAYERS; i++) {
            String name = prefix + i;
            userService.create(name, UserService.ROLE_USER);
            players.add(new Player(users.findByUsername(name).orElseThrow().getId(), name));
        }
        return players;
    }

    private interface Move {
        Object by(Player player) throws Exception;
    }

    /** Runs one move per player, all released together; returns what went wrong. */
    private static List<Throwable> atOnce(List<Player> players, Move move) throws InterruptedException {
        CyclicBarrier start = new CyclicBarrier(players.size());
        ExecutorService pool = Executors.newFixedThreadPool(players.size());
        try {
            List<Callable<Object>> calls = new ArrayList<>();
            for (Player player : players) {
                calls.add(() -> {
                    start.await();
                    return move.by(player);
                });
            }
            List<Throwable> failures = new ArrayList<>();
            for (Future<Object> result : pool.invokeAll(calls)) {
                try {
                    result.get();
                } catch (Exception e) {
                    failures.add(e.getCause() == null ? e : e.getCause());
                }
            }
            return failures;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Path temporaryDirectory() {
        try {
            Path dir = Files.createTempDirectory("globle-sqlite");
            File db = dir.resolve("users.db").toFile();
            dir.toFile().deleteOnExit();
            db.deleteOnExit();
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

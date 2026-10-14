package cloud.gehan.globle.service;

import cloud.gehan.globle.MovableClock;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.repository.GlobleNameRequestRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private RollCallService rollCall;

    @Autowired
    private GlobleGameRepository rows;

    @Autowired
    private NameRequestService nameRequests;

    @Autowired
    private GlobleNameRequestRepository requestRows;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private CountryCatalog catalog;

    @Autowired
    private MovableClock clock;

    @Autowired
    private DailyPuzzles puzzles;

    /** Each round is a new day: everyone's first guess, and the day's answer drawn, at once. */
    @Test
    void everyoneStartingTheDailyAtOnceAllLand() throws Exception {
        List<Player> players = players("daily");
        for (int round = 0; round < 5; round++) {
            clock.setDay(LocalDate.of(2026, 10, 9).plusDays(round));
            List<Throwable> failures = atOnce(players, player -> games.guessDaily(player, Variant.CLASSIC, "FRA", null));
            assertThat(failures).as("round %d", round).isEmpty();
        }
    }

    /**
     * Half the family opens classic and half opens Hard at the first moment of a day:
     * both answers are drawn at once, Hard waiting on classic's, and every move lands.
     */
    @Test
    void everyoneStartingBothDailiesAtOnceAllLand() throws Exception {
        List<Player> players = players("both");
        for (int round = 0; round < 5; round++) {
            LocalDate date = LocalDate.of(2027, 1, 1).plusDays(round);
            clock.setDay(date);
            List<Throwable> failures = atOnce(players, player -> {
                Variant variant = player.username().hashCode() % 2 == 0 ? Variant.HARD : Variant.CLASSIC;
                return games.guessDaily(player, variant, "FRA", null);
            });
            assertThat(failures).as("round %d", round).isEmpty();
            assertThat(puzzles.answerFor(Variant.HARD, date)).isNotEqualTo(puzzles.answerFor(date));
        }
    }

    @Test
    void everyoneGuessingInPracticeAtOnceAllLand() throws Exception {
        List<Player> players = players("practice");
        List<String> codes = catalog.all().stream().map(c -> c.code()).toList();
        for (int round = 0; round < 10; round++) {
            List<Throwable> started = atOnce(players, player -> games.newUnlimited(player, Kind.COUNTRY, Region.WORLD, Variant.CLASSIC));
            assertThat(started).as("starts in round %d", round).isEmpty();
            String code = codes.get(round);
            List<Throwable> guessed = atOnce(players, player -> {
                long id = games.currentUnlimited(player, Kind.COUNTRY, null, Variant.CLASSIC).orElseThrow().id();
                return games.guessUnlimited(player, id, code);
            });
            assertThat(guessed).as("guesses in round %d", round).isEmpty();
        }
    }

    /** The whole family starting, naming in and finishing "Name them all" at the same moments. */
    @Test
    void everyonePlayingNameThemAllAtOnceAllLand() throws Exception {
        List<Player> players = players("rollcall");
        List<String> codes = catalog.all().stream().filter(c -> c.answer() && c.difficulty() > 1)
                .map(c -> c.code()).limit(5).toList();
        List<Throwable> started = atOnce(players, player -> rollCall.start(player, Region.WORLD, Autofill.EASY));
        assertThat(started).as("starts").isEmpty();
        for (String code : codes) {
            List<Throwable> named = atOnce(players, player -> {
                long id = rollCall.current(player, Region.WORLD).orElseThrow().id();
                return rollCall.name(player, id, code);
            });
            assertThat(named).as("naming %s", code).isEmpty();
        }
        List<Throwable> finished = atOnce(players, player ->
                rollCall.finish(player, rollCall.current(player, Region.WORLD).orElseThrow().id()));
        assertThat(finished).as("finishes").isEmpty();
        for (Player player : players) {
            List<GlobleGame> runs = rows.findRollCallsFinished(player, Region.WORLD);
            assertThat(runs).hasSize(1);
            assertThat(runs.get(0).getGuessCount()).isEqualTo(30 + codes.size());
        }
    }

    /**
     * A start tapped on several devices at once leaves exactly one run in progress: each
     * start drops the one before it (nothing typed), one at a time under the write lock.
     */
    @Test
    void manyStartsAtOnceLeaveOneRunInProgress() throws Exception {
        Player player = players("tapper").get(0);
        List<Throwable> failures = atOnce(Collections.nCopies(PLAYERS, player),
                p -> rollCall.start(p, Region.EUROPE, Autofill.KNOWN));
        assertThat(failures).isEmpty();
        assertThat(rows.findRollCallsPlaying(player, Region.EUROPE)).hasSize(1);
        assertThat(rows.findRollCallsFinished(player, Region.EUROPE)).isEmpty();

        long id = rollCall.current(player, Region.EUROPE).orElseThrow().id();
        String code = catalog.all().stream().filter(c -> c.answer() && c.region().equals("europe")
                && c.difficulty() > 2).findFirst().orElseThrow().code();
        List<Throwable> sameName = atOnce(Collections.nCopies(PLAYERS, player), p -> rollCall.name(p, id, code));
        assertThat(sameName).hasSize(PLAYERS - 1)
                .allMatch(e -> e instanceof GlobleException g && g.code().equals("already-guessed"));
        assertThat(rows.findById(id).orElseThrow().typedCodes()).containsExactly(code);
    }

    /**
     * SQLite gives a new row one more than the largest id in the table (globle_game has no
     * AUTOINCREMENT), so if the run a start drops were deleted before the new run went in,
     * and it was the newest row, the new run would take over its id. A tab still showing
     * the dropped run would then name, or say "I'm done", into the new one. A start puts
     * the new run in first, so the dropped id is never handed out again and the stale tab
     * is told "not found".
     */
    @Test
    void aRunDroppedByStartingOverNeverLendsItsIdToTheNextRun() throws Exception {
        Player player = players("stale").get(0);
        long dropped = rollCall.start(player, Region.OCEANIA, Autofill.EASY).id();
        assertThat(rows.findAll()).extracting(GlobleGame::getId).as("the newest row").allMatch(id -> id <= dropped);

        long next = rollCall.start(player, Region.OCEANIA, Autofill.OFF).id();

        assertThat(next).isGreaterThan(dropped);
        assertThat(rows.findById(dropped)).as("nothing typed: dropped").isEmpty();
        String code = catalog.all().stream().filter(c -> c.answer() && c.region().equals("oceania")
                && c.difficulty() > 1).findFirst().orElseThrow().code();
        assertThatThrownBy(() -> rollCall.name(player, dropped, code))
                .isInstanceOfSatisfying(GlobleException.class, e -> assertThat(e.code()).isEqualTo("not-found"));
        assertThatThrownBy(() -> rollCall.finish(player, dropped))
                .isInstanceOfSatisfying(GlobleException.class, e -> assertThat(e.code()).isEqualTo("not-found"));
        GlobleGame run = rows.findById(next).orElseThrow();
        assertThat(run.isPlaying()).isTrue();
        assertThat(run.getGuessCount()).isZero();

        // Ids go on rising after it, too: the gap it left is never filled.
        long after = rollCall.start(player, Region.OCEANIA, Autofill.OFF).id();
        assertThat(after).isGreaterThan(next);
        assertThatThrownBy(() -> rollCall.name(player, next, code))
                .isInstanceOfSatisfying(GlobleException.class, e -> assertThat(e.code()).isEqualTo("not-found"));
    }

    /**
     * The family sending name requests at once, one of them double-tapping the same one
     * on every device, and the admin reading the list (which closes what the data already
     * names) while the rest send: every write lands, the double tap once.
     */
    @Test
    void everyoneSendingNameRequestsAtOnceAllLandAndADoubleTapLandsOnce() throws Exception {
        List<Player> players = players("names");
        List<Throwable> sent = atOnce(players,
                player -> nameRequests.send(player, "GBR", "Blighty " + player.username(), "en", "missed"));
        assertThat(sent).isEmpty();

        Player tapper = players.get(0);
        List<Throwable> taps = atOnce(Collections.nCopies(PLAYERS, tapper),
                player -> nameRequests.send(player, "NRU", "Naoero", "en", "study"));
        assertThat(taps).isEmpty();
        assertThat(requestRows.findOpen(tapper)).filteredOn(request -> request.getCode().equals("NRU")).hasSize(1);

        List<Throwable> mixed = atOnce(players, player -> player == tapper
                ? nameRequests.list(true)
                : nameRequests.send(player, "DEU", "Deutschland", "es", "other"));
        assertThat(mixed).isEmpty();
        // Eight Blighty requests and one Naoero stay open; the seven for "Deutschland" close by the data.
        assertThat(nameRequests.openCount()).isEqualTo(PLAYERS + 1);
        assertThat(requestRows.count()).isEqualTo(PLAYERS + 1 + PLAYERS - 1);
    }

    /**
     * One player two short of the limit sending eight different requests from eight
     * devices at once: exactly two land and the other six are refused with too-many, so
     * the limit holds however the sends interleave. Its rows are removed afterwards, so
     * the counts other tests here make are unaffected.
     */
    @Test
    void theOpenLimitHoldsWhenOnePlayerSendsManyAtOnce() throws Exception {
        Player player = players("limit").get(0);
        try {
            for (int i = 0; i < NameRequestService.MAX_OPEN_PER_PLAYER - 2; i++) {
                nameRequests.send(player, "NRU", "Earlier " + i, "en", "other");
            }
            AtomicInteger device = new AtomicInteger();
            List<Throwable> refused = atOnce(Collections.nCopies(PLAYERS, player), same -> nameRequests.send(same,
                    "FRA", "At once " + device.getAndIncrement(), "en", "named"));

            assertThat(refused).hasSize(PLAYERS - 2).allSatisfy(failure -> assertThat(failure)
                    .isInstanceOfSatisfying(GlobleException.class, e -> assertThat(e.code()).isEqualTo("too-many")));
            assertThat(requestRows.findOpen(player)).hasSize(NameRequestService.MAX_OPEN_PER_PLAYER)
                    .filteredOn(request -> request.getCode().equals("FRA")).hasSize(2);
        } finally {
            requestRows.deleteAll(requestRows.findOpen(player));
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

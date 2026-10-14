package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.repository.RollCallSummary;
import cloud.gehan.globle.view.RollCallOptions;
import cloud.gehan.globle.view.RollCallState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The rules of "Name them all" (roll call): name as many countries of a region as you
 * can. There is no mystery and no distance; a run is the list of countries named, a
 * stopwatch, and the region's pool (see {@link RollCallPools}).
 *
 * <p><b>Which runs count.</b> A run counts - toward the player's best and finished-run
 * count on the setup card, the runs "mine" reads, and the board - once it is FINISHED
 * with at least one name typed ({@link GlobleGame#counts()}). A run with nothing typed,
 * ended empty with "I'm done" or holding only what autofill gave it, is kept as
 * FINISHED, so it still has its end card, but counts nowhere: none of it was the
 * player's doing. Counting it would let a run of nothing but filled-in countries set a
 * record, and let an empty one wipe out "mine".
 *
 * <p><b>Autofill.</b> A run may start with some countries already named for the
 * player, chosen when it starts and flagged as filled in: none, the region's tier-1
 * countries ("easy"), tiers 1 and 2 ("known"), or the player's own sure things
 * ("mine"): every country they named, filled in or typed, in each of their last three
 * runs that count in the region (in both, when they have only two). "Mine" needs two
 * runs that count there and is refused before that. It is worked out from the runs
 * finished before the start, which is what the setup card showed; a run abandoned by
 * this start counts from the next start on. Whatever is filled in stays within the
 * run's pool: tiers and sure things outside the region are never added. An autofill
 * that would fill in the whole pool is refused too: the run would be over before it
 * began, with nothing left to name. The setup card sees that coming (an option's count
 * equals the total) and offers it disabled.
 *
 * <p><b>One run in progress per region.</b> Starting another in the same region
 * abandons the one in progress there, and only that one. With at least one typed name
 * it is finished and counts, so starting over cannot quietly discard a run that was
 * going badly. With none (only what autofill gave it, or nothing) it is deleted:
 * nothing about it was the player's doing.
 *
 * <p><b>Ending.</b> A run ends FINISHED: when the player says they are done, when it is
 * abandoned with a typed name, or by itself the moment the last country in the pool is
 * typed. It never starts finished, since an autofill cannot fill in the whole pool.
 *
 * <p><b>Writes</b> run like {@link GameService}'s: one transaction under the app-wide
 * write lock ({@link GlobleWrites}), so a double-tapped name lands once and two quick
 * starts leave one run in progress. Elapsed time is the server's ({@link GlobleClock}).
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RollCallService {

    /** "Mine" reads at most this many of the player's latest runs that count in the region. */
    static final int MINE_LOOKBACK = 3;
    /** ...and needs at least this many before it is offered. */
    static final int MINE_MIN_RUNS = 2;

    private final GlobleGameRepository games;
    private final CountryCatalog catalog;
    private final RollCallPools pools;
    private final GlobleClock clock;
    private final TransactionTemplate transaction;

    public RollCallService(GlobleGameRepository games, CountryCatalog catalog, RollCallPools pools,
                           GlobleClock clock, PlatformTransactionManager transactions) {
        this.games = games;
        this.catalog = catalog;
        this.pools = pools;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------------ reading

    /**
     * What each autofill would fill in for this player in this region, and their record
     * there, from the runs that count; and whether any run there has finished, counting
     * or not (the study page has something to show). A count equal to the total is an
     * autofill that {@link #start} refuses.
     */
    public RollCallOptions options(Player player, Region region) {
        RollCallPools.Pool pool = pools.pool(region);
        List<GlobleGame> finished = games.findRollCallsFinished(player, region);
        List<GlobleGame> counting = finished.stream().filter(GlobleGame::counts).toList();
        RollCallOptions.Best best = counting.stream()
                .max(Comparator.comparingInt(GlobleGame::getGuessCount)
                        .thenComparing(Comparator.comparingLong(RollCallService::seconds).reversed())
                        .thenComparing(Comparator.comparing(GlobleGame::getId).reversed()))
                .map(run -> new RollCallOptions.Best(run.getGuessCount(), pool.size(), seconds(run), run.autoCount()))
                .orElse(null);
        return new RollCallOptions(
                region.api(),
                pool.size(),
                pool.upToTier(RollCallPools.EASY_TIER).size(),
                pool.upToTier(RollCallPools.KNOWN_TIER).size(),
                mine(pool, counting).map(List::size).orElse(null),
                counting.size(),
                !finished.isEmpty(),
                best);
    }

    /** The run in progress in a region, if any; it picks up on any device. */
    public Optional<RollCallState> current(Player player, Region region) {
        return games.findRollCallsPlaying(player, region).stream()
                .findFirst()
                .map(this::state);
    }

    // ------------------------------------------------------------------ writing

    /**
     * Starts a run in a region, filling in what {@code autofill} asks for. The run in
     * progress there, if any, is abandoned (see the class comment). A refused autofill
     * ("mine" too soon, or one that would fill in the whole pool) changes nothing: the
     * checks come before anything is written, and it is all one transaction anyway.
     *
     * <p>The new run goes in before the abandoned one is dropped. SQLite gives a new row
     * one more than the largest id in the table (globle_game has no AUTOINCREMENT), so
     * were the dropped run deleted first while it was the newest row, the new run would
     * take over its id, and a tab still showing the dropped run would name into the new
     * one. In this order the dropped id is never handed out again, and that tab is told
     * "not found" (SqliteWritesTest).
     */
    public RollCallState start(Player player, Region region, Autofill autofill) {
        RollCallPools.Pool pool = pools.pool(region);
        return write(() -> {
            Instant now = now();
            List<String> filled = autofill(player, region, pool, autofill);
            if (!filled.isEmpty() && pool.coveredBy(filled)) {
                throw GlobleException.badRequest();
            }
            List<GlobleGame> inProgress = games.findRollCallsPlaying(player, region);
            GlobleGame run = games.save(GlobleGame.rollCall(player, region, autofill, filled, now));
            for (GlobleGame abandoned : inProgress) {
                if (abandoned.hasTyped()) {
                    abandoned.finish(Status.FINISHED, now);
                    games.save(abandoned);
                } else {
                    games.delete(abandoned);
                }
            }
            return state(run);
        });
    }

    /**
     * Names one country in the player's run. Refused, with nothing stored, if the run
     * is over, the country is already named, it is a territory, or it is outside the
     * run's region. Naming the last one finishes the run.
     */
    public RollCallState name(Player player, long id, String rawCode) {
        Country country = parse(rawCode);
        return write(() -> {
            GlobleGame run = games.findRollCall(player, id).orElseThrow(GlobleException::notFound);
            if (!run.isPlaying()) {
                throw GlobleException.finished();
            }
            if (run.hasGuessed(country.code())) {
                throw GlobleException.alreadyGuessed();
            }
            if (!country.answer()) {
                throw GlobleException.notAnAnswer();
            }
            RollCallPools.Pool pool = pools.pool(run.getRegion());
            if (!pool.contains(country.code())) {
                throw GlobleException.outsideRegion();
            }
            run.addGuess(country.code());
            finishIfComplete(run, pool, now());
            return state(games.save(run));
        });
    }

    /**
     * "I'm done": ends the run. It counts however much was named, provided the player
     * typed at least one name; one with nothing typed is kept, finished, but counts
     * nowhere (see the class comment).
     */
    public RollCallState finish(Player player, long id) {
        return write(() -> {
            GlobleGame run = games.findRollCall(player, id).orElseThrow(GlobleException::notFound);
            if (!run.isPlaying()) {
                throw GlobleException.finished();
            }
            run.finish(Status.FINISHED, now());
            return state(games.save(run));
        });
    }

    // ------------------------------------------------------------------ rules

    /** The codes a new run starts with, in catalogue order. "Mine" too soon is a bad request. */
    private List<String> autofill(Player player, Region region, RollCallPools.Pool pool, Autofill autofill) {
        return switch (autofill) {
            case OFF -> List.of();
            case EASY -> pool.upToTier(RollCallPools.EASY_TIER);
            case KNOWN -> pool.upToTier(RollCallPools.KNOWN_TIER);
            case MINE -> mine(pool, games.findRollCallsCounting(player, region))
                    .orElseThrow(GlobleException::badRequest);
        };
    }

    /**
     * The player's sure things in this pool: what every one of their latest runs that
     * count named (filled in or typed), in catalogue order. Empty, meaning "not yet",
     * with fewer than {@link #MINE_MIN_RUNS} of them. A run with nothing typed is not
     * among them, so it neither unlocks "mine" nor pushes a real run out of the window.
     *
     * @param counting the player's runs that count in the pool's region, newest first
     */
    private static Optional<List<String>> mine(RollCallPools.Pool pool, List<GlobleGame> counting) {
        if (counting.size() < MINE_MIN_RUNS) {
            return Optional.empty();
        }
        Set<String> always = null;
        for (GlobleGame run : counting.subList(0, Math.min(MINE_LOOKBACK, counting.size()))) {
            Set<String> named = new HashSet<>(run.guessCodes());
            if (always == null) {
                always = named;
            } else {
                always.retainAll(named);
            }
        }
        Set<String> sure = always;
        return Optional.of(pool.codes().stream().filter(sure::contains).toList());
    }

    /** Ends the run the moment nothing in its pool is left to name. */
    private static void finishIfComplete(GlobleGame run, RollCallPools.Pool pool, Instant now) {
        if (run.isPlaying() && pool.coveredBy(run.guessCodes())) {
            run.finish(Status.FINISHED, now);
        }
    }

    /**
     * Codes arrive as the client sent them; case and surrounding space are forgiven,
     * anything else that is not in the catalogue is refused. (The same rule as a guess.)
     */
    private Country parse(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw GlobleException.badRequest();
        }
        return catalog.find(rawCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(GlobleException::unknownCountry);
    }

    // ------------------------------------------------------------------ views

    /**
     * The run as the client sees it. A code dropped from the data since it was named is
     * left out of the list, as a guess is in {@link GameViews}; it still counts in
     * {@code namedCount}.
     */
    RollCallState state(GlobleGame run) {
        RollCallPools.Pool pool = pools.pool(run.getRegion());
        List<String> codes = run.guessCodes();
        int auto = run.autoCount();
        List<RollCallState.Named> named = new ArrayList<>();
        for (int i = 0; i < codes.size(); i++) {
            if (catalog.find(codes.get(i)).isPresent()) {
                named.add(new RollCallState.Named(codes.get(i), i < auto));
            }
        }
        Set<String> namedSet = new HashSet<>(codes);

        List<RollCallState.RegionProgress> byRegion = new ArrayList<>();
        for (String continent : RollCallPools.CONTINENTS) {
            List<Country> there = pool.countries().stream().filter(c -> c.region().equals(continent)).toList();
            if (!there.isEmpty()) {
                int done = (int) there.stream().filter(c -> namedSet.contains(c.code())).count();
                byRegion.add(new RollCallState.RegionProgress(continent, done, there.size()));
            }
        }

        boolean playing = run.isPlaying();
        Instant finishedAt = run.getFinishedAt();
        return new RollCallState(
                run.getId(),
                Mode.ROLLCALL.api(),
                run.getRegion().api(),
                run.getStatus().name(),
                run.getAutofill().api(),
                pool.size(),
                List.copyOf(named),
                run.getGuessCount(),
                auto,
                List.copyOf(byRegion),
                RollCallSummary.secondsBetween(run.getStartedAt(), playing ? clock.now() : finishedAt),
                run.getStartedAt().toString(),
                finishedAt == null ? null : finishedAt.toString(),
                playing ? null : pool.codes().stream().filter(code -> !namedSet.contains(code)).sorted().toList());
    }

    /** A finished run's time; the board and the setup card's "best" use the same rule. */
    private static long seconds(GlobleGame run) {
        return RollCallSummary.secondsBetween(run.getStartedAt(), run.getFinishedAt());
    }

    /**
     * The instant a write stamps on a run, to the millisecond: the precision the games
     * table keeps (GlobleColumns.InstantMillis). The real clock is finer, and unlike a
     * guessing game's, a run's times are sent to the client. Stamped at full precision,
     * the run a start, name or finish returns would differ from the same run read back a
     * moment later ("...:26.123456Z", then "...:26.123Z"), and the page redraws from
     * either. Three fraction digits are also all that JavaScript's date-time format
     * defines.
     */
    private Instant now() {
        return clock.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /** One transaction, under the app-wide write lock; see GameService. */
    private RollCallState write(Supplier<RollCallState> work) {
        return GlobleWrites.run(() -> transaction.execute(status -> work.get()));
    }
}

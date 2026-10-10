package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.GlobleDay;
import cloud.gehan.globle.model.GlobleHardDay;
import cloud.gehan.globle.model.PuzzleDay;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleDayRepository;
import cloud.gehan.globle.repository.GlobleHardDayRepository;
import cloud.gehan.globle.repository.PuzzleDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides each day's mystery countries, one per daily variant, and remembers them.
 *
 * <p>An answer is drawn the first time a date is needed - in practice the day's first
 * guess or give-up - with SecureRandom, and stored: the classic one in globle_day, the
 * Hard one in globle_hard_day. It is never derived from the date, because the source
 * is public and anything derivable could be computed in advance by a player who reads
 * it.
 *
 * <p><b>No recent repeats, and a large pool.</b> The draw skips countries used within
 * {@code globle.answer-repeat-window-days} on either side of the date, but never more
 * than a third of the variant's pool (65 of the 197 classic answers, 55 of the 167
 * Hard ones), whatever is configured. Every finished game reveals its answer, so a
 * long window would be a clue: with 197 answers and a 365-day one, the pool would
 * shrink to the few countries not yet used, down to a single candidate on day 197,
 * and a player keeping a list could narrow every later day to fifteen. Capped at a
 * third, the day's pool stays at about two thirds. Either side, not just before, so
 * that a date filled in out of order still respects its neighbours; even then the two
 * windows leave a third of the answers. If the pool were ever empty the window halves
 * until something is left, and only a window of zero means truly anything. Each
 * variant keeps its own window: a country can be the classic answer one day and the
 * Hard answer the next.
 *
 * <p><b>Hard is never the day's classic answer.</b> The Hard draw first settles the
 * classic answer for its date (drawing it if nobody has played classic yet) and then
 * leaves that country out. Settling it first rather than checking whichever happens
 * to exist means the two can never be drawn side by side and collide.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DailyPuzzles {

    private static final Logger log = LoggerFactory.getLogger(DailyPuzzles.class);

    private final GlobleDayRepository days;
    private final GlobleHardDayRepository hardDays;
    private final AnswerPools pools;
    private final GlobleProperties properties;
    private final GlobleClock clock;
    private final Random random;

    @Autowired
    public DailyPuzzles(GlobleDayRepository days, GlobleHardDayRepository hardDays, AnswerPools pools,
                        GlobleProperties properties, GlobleClock clock) {
        this(days, hardDays, pools, properties, clock, new SecureRandom());
    }

    /** For tests that need to control the draw. */
    DailyPuzzles(GlobleDayRepository days, GlobleHardDayRepository hardDays, AnswerPools pools,
                 GlobleProperties properties, GlobleClock clock, Random random) {
        this.days = days;
        this.hardDays = hardDays;
        this.pools = pools;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    /** Puzzle #1 is the epoch date; each day after adds one. Classic and Hard share it. */
    public int puzzleNumber(LocalDate date) {
        return Math.toIntExact(ChronoUnit.DAYS.between(properties.getEpoch(), date) + 1);
    }

    /** The classic answer for {@code date}; see {@link #answerFor(Variant, LocalDate)}. */
    public String answerFor(LocalDate date) {
        return answerFor(Variant.CLASSIC, date);
    }

    /**
     * The answer code for {@code date} in this variant, choosing and storing one if this
     * is the first time the date has been asked about.
     *
     * <p>Runs in its own short transactions and must not be called inside another one:
     * on SQLite a write from a second connection would wait on the outer transaction's
     * read lock, which is waiting on it.
     */
    public String answerFor(Variant variant, LocalDate date) {
        return variant == Variant.HARD
                ? answerFor(hardDays, variant, date, GlobleHardDay::new)
                : answerFor(days, variant, date, GlobleDay::new);
    }

    private interface DayFactory<T extends PuzzleDay> {
        T create(LocalDate date, String code, int puzzleNumber, Instant createdAt);
    }

    private <T extends PuzzleDay> String answerFor(PuzzleDayRepository<T> table, Variant variant, LocalDate date,
                                                   DayFactory<T> factory) {
        Optional<T> stored = table.findById(date.toString());
        if (stored.isPresent()) {
            return stored.get().getCountryCode();
        }
        // Before taking the lock, and in its own transactions: see answerFor's comment.
        Set<String> sameDay = variant == Variant.HARD ? Set.of(answerFor(Variant.CLASSIC, date)) : Set.of();
        return create(table, variant, date, sameDay, factory);
    }

    /**
     * Under Globle's write lock ({@link GlobleWrites}) and re-read, so two players
     * guessing at the first moment of a day cannot each draw a different answer, and the
     * insert never collides with another player's guess on SQLite. The insert can still
     * lose to another process on the same database; the primary key refuses the second
     * row, and the stored answer is the one everybody uses.
     */
    private <T extends PuzzleDay> String create(PuzzleDayRepository<T> table, Variant variant, LocalDate date,
                                                Set<String> sameDay, DayFactory<T> factory) {
        return GlobleWrites.run(() -> {
            Optional<T> stored = table.findById(date.toString());
            if (stored.isPresent()) {
                return stored.get().getCountryCode();
            }
            String code = draw(table, variant, date, sameDay);
            try {
                table.saveAndFlush(factory.create(date, code, puzzleNumber(date), clock.now()));
                log.debug("Chose the Globle {} answer for {}", variant.api(), date);
                return code;
            } catch (DataIntegrityViolationException raced) {
                return table.findById(date.toString())
                        .map(PuzzleDay::getCountryCode)
                        .orElseThrow(() -> raced);
            }
        });
    }

    /** A fresh classic draw for {@code date}, ignoring anything stored for it. */
    String draw(LocalDate date) {
        return draw(days, Variant.CLASSIC, date, Set.of());
    }

    /** A fresh Hard draw for {@code date}, never {@code classicAnswer}. */
    String drawHard(LocalDate date, String classicAnswer) {
        return draw(hardDays, Variant.HARD, date, Set.of(classicAnswer));
    }

    /**
     * @param sameDay codes this date may not have whatever the window: the classic
     *                answer, when drawing Hard
     */
    private String draw(PuzzleDayRepository<? extends PuzzleDay> table, Variant variant, LocalDate date,
                        Set<String> sameDay) {
        AnswerPools.Pool pool = pools.daily(variant);
        int window = Math.min(properties.getAnswerRepeatWindowDays(), pool.size() / 3);
        for (; window > 0; window /= 2) {
            Set<String> excluded = table.findByPuzzleDateBetween(
                            date.minusDays(window).toString(), date.plusDays(window).toString())
                    .stream()
                    .filter(day -> !day.getDate().equals(date))
                    .map(PuzzleDay::getCountryCode)
                    .collect(Collectors.toCollection(HashSet::new));
            excluded.addAll(sameDay);
            Optional<Country> rested = pool.draw(random, c -> !excluded.contains(c.code()));
            if (rested.isPresent()) {
                return rested.get().code();
            }
        }
        return pool.draw(random, c -> !sameDay.contains(c.code()))
                .or(() -> pool.draw(random, c -> true))
                .orElseThrow()
                .code();
    }
}

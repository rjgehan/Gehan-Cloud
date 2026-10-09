package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.GlobleDay;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.repository.GlobleDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides each day's mystery country and remembers it.
 *
 * <p>An answer is drawn the first time a date is needed - in practice the day's first
 * guess or give-up - with SecureRandom, and stored in globle_day. It is never derived
 * from the date, because the source is public and anything derivable could be
 * computed in advance by a player who reads it.
 *
 * <p><b>No recent repeats, and a large pool.</b> The draw skips countries used within
 * {@code globle.answer-repeat-window-days} on either side of the date, but never more
 * than a third of the possible answers (65 of 197), whatever is configured. Every
 * finished game reveals its answer, so a long window would be a clue: with 197 answers
 * and a 365-day one, the pool would shrink to the few countries not yet used, down to
 * a single candidate on day 197, and a player keeping a list could narrow every later
 * day to fifteen. Capped at a third, the day's pool stays at about two thirds of the
 * world. Either side, not just before, so that a date filled in out of order still
 * respects its neighbours; even then the two windows leave a third of the answers. If
 * the pool were ever empty the window halves until something is left, and only a
 * window of zero means truly anything.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DailyPuzzles {

    private static final Logger log = LoggerFactory.getLogger(DailyPuzzles.class);

    private final GlobleDayRepository days;
    private final CountryCatalog catalog;
    private final GlobleProperties properties;
    private final GlobleClock clock;
    private final Random random;

    @Autowired
    public DailyPuzzles(GlobleDayRepository days, CountryCatalog catalog, GlobleProperties properties,
                        GlobleClock clock) {
        this(days, catalog, properties, clock, new SecureRandom());
    }

    /** For tests that need to control the draw. */
    DailyPuzzles(GlobleDayRepository days, CountryCatalog catalog, GlobleProperties properties,
                 GlobleClock clock, Random random) {
        this.days = days;
        this.catalog = catalog;
        this.properties = properties;
        this.clock = clock;
        this.random = random;
    }

    /** Puzzle #1 is the epoch date; each day after adds one. */
    public int puzzleNumber(LocalDate date) {
        return Math.toIntExact(ChronoUnit.DAYS.between(properties.getEpoch(), date) + 1);
    }

    /**
     * The answer code for {@code date}, choosing and storing one if this is the first
     * time the date has been asked about.
     *
     * <p>Runs in its own short transactions and must not be called inside another one:
     * on SQLite a write from a second connection would wait on the outer transaction's
     * read lock, which is waiting on it.
     */
    public String answerFor(LocalDate date) {
        Optional<GlobleDay> stored = days.findById(date.toString());
        return stored.isPresent() ? stored.get().getCountryCode() : create(date);
    }

    /**
     * Under Globle's write lock ({@link GlobleWrites}) and re-read, so two players
     * guessing at the first moment of a day cannot each draw a different answer, and the
     * insert never collides with another player's guess on SQLite. The insert can still
     * lose to another process on the same database; the primary key refuses the second
     * row, and the stored answer is the one everybody uses.
     */
    private String create(LocalDate date) {
        return GlobleWrites.run(() -> {
            Optional<GlobleDay> stored = days.findById(date.toString());
            if (stored.isPresent()) {
                return stored.get().getCountryCode();
            }
            String code = draw(date);
            try {
                days.saveAndFlush(new GlobleDay(date, code, puzzleNumber(date), clock.now()));
                log.debug("Chose the Globle answer for {}", date);
                return code;
            } catch (DataIntegrityViolationException raced) {
                return days.findById(date.toString())
                        .map(GlobleDay::getCountryCode)
                        .orElseThrow(() -> raced);
            }
        });
    }

    String draw(LocalDate date) {
        List<Country> pool = catalog.answerable(Region.WORLD, Kind.COUNTRY);
        int window = Math.min(properties.getAnswerRepeatWindowDays(), pool.size() / 3);
        for (; window > 0; window /= 2) {
            Set<String> recent = days.findByPuzzleDateBetween(
                            date.minusDays(window).toString(), date.plusDays(window).toString())
                    .stream()
                    .filter(day -> !day.getDate().equals(date))
                    .map(GlobleDay::getCountryCode)
                    .collect(Collectors.toSet());
            List<Country> rested = pool.stream().filter(c -> !recent.contains(c.code())).toList();
            if (!rested.isEmpty()) {
                return rested.get(random.nextInt(rested.size())).code();
            }
        }
        return pool.get(random.nextInt(pool.size())).code();
    }
}

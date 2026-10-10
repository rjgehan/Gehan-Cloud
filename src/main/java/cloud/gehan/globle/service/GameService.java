package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.view.GameState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The rules of the game: guessing, giving up, and starting practice rounds.
 *
 * <p><b>One change at a time.</b> Everything that writes games runs inside one
 * transaction under a single app-wide lock ({@link GlobleWrites}), held until the
 * transaction commits. That is what makes a double-tapped guess land once, keeps a
 * daily to a single row, and keeps "at most one unlimited game in progress per slot"
 * true without a constraint SQLite cannot express. The lock is app-wide rather than
 * per player because SQLite has one writer at a time: two players' transactions open
 * at once would deadlock, and SQLite answers that with an immediate SQLITE_BUSY
 * rather than a wait. At family scale a guess waits a few milliseconds at most. (One
 * instance of the app is assumed; the daily's unique key is the backstop if that ever
 * stops being true.)
 *
 * <p>The day's answer is fetched before the transaction is opened, never inside:
 * choosing it may write, in a transaction of its own that must not wait behind an open
 * one of ours.
 *
 * <p><b>Variants.</b> There are two dailies a day, classic and Hard, each with its own
 * row per player. A practice game lives in a <i>slot</i>, (kind, region, variant):
 * Europe, Mainland, Capitals and Hard practice each keep their own game in progress
 * and resume independently. Hard is for countries only.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GameService {

    private final GlobleGameRepository games;
    private final DailyPuzzles puzzles;
    private final CountryCatalog catalog;
    private final AnswerPools pools;
    private final GameViews views;
    private final GlobleClock clock;
    private final GlobleProperties properties;
    private final TransactionTemplate transaction;
    private final Random random = new SecureRandom();

    public GameService(GlobleGameRepository games, DailyPuzzles puzzles, CountryCatalog catalog, AnswerPools pools,
                       GameViews views, GlobleClock clock, GlobleProperties properties,
                       PlatformTransactionManager transactions) {
        this.games = games;
        this.puzzles = puzzles;
        this.catalog = catalog;
        this.pools = pools;
        this.views = views;
        this.clock = clock;
        this.properties = properties;
        this.transaction = new TransactionTemplate(transactions);
    }

    // ------------------------------------------------------------------ daily

    /** Today's puzzle in this variant. Looking stores nothing: a row appears with the first guess. */
    public GameState daily(Player player, Variant variant) {
        LocalDate today = clock.today();
        Optional<GlobleGame> game = games.findDaily(player, today, variant);
        if (game.isEmpty()) {
            return views.untouchedDaily(today, variant);
        }
        return views.of(game.get(), puzzles.answerFor(variant, today));
    }

    /**
     * A guess at today's puzzle in this variant. {@code expectedDate} is the date of the
     * puzzle the page is showing, if it says; see {@link #today(String)}.
     */
    public GameState guessDaily(Player player, Variant variant, String rawCode, String expectedDate) {
        LocalDate today = today(expectedDate);
        Country guess = parse(rawCode);
        String answer = puzzles.answerFor(variant, today);
        return write(() -> {
            GlobleGame game = games.findDaily(player, today, variant)
                    .orElseGet(() -> GlobleGame.daily(player, today, variant, clock.now()));
            applyGuess(game, guess, answer);
            return views.of(games.save(game), answer);
        });
    }

    /**
     * Giving up before guessing still counts: it creates the row, as a loss.
     * {@code expectedDate} is as for {@link #guessDaily}.
     */
    public GameState giveUpDaily(Player player, Variant variant, String expectedDate) {
        LocalDate today = today(expectedDate);
        String answer = puzzles.answerFor(variant, today);
        return write(() -> {
            GlobleGame game = games.findDaily(player, today, variant)
                    .orElseGet(() -> GlobleGame.daily(player, today, variant, clock.now()));
            giveUp(game);
            return views.of(games.save(game), answer);
        });
    }

    // ------------------------------------------------------------------ unlimited

    /**
     * The game in progress in one slot, if any; it picks up on any device.
     *
     * @param region null for any region: the newest game of this kind and variant in
     *               progress wherever it was started. That is how the API answered
     *               before slots, when a player had at most one game per kind, so a
     *               game started then is still found by a request that names no region.
     */
    public Optional<GameState> currentUnlimited(Player player, Kind kind, Region region, Variant variant) {
        requirePlayable(kind, variant);
        List<GlobleGame> playing = region == null
                ? games.findPlaying(player, kind, variant)
                : games.findPlaying(player, kind, region, variant);
        return playing.stream()
                .findFirst()
                .map(game -> views.of(game, game.getAnswerCode()));
    }

    /**
     * Starts a fresh game in a slot, drawing the answer from the slot's pool (weighted
     * for Hard; see {@link AnswerPools}), never the answer of the player's previous game
     * in that slot.
     *
     * <p>A game in the same slot still in progress is abandoned; games in other slots
     * are left alone. With at least one guess it is recorded as given up, so starting
     * over cannot be used to dodge a loss. With none it is simply dropped: nothing about
     * its answer was shown, so there is nothing to dodge, and tapping "New game" twice
     * costs nothing. A dropped game is not a previous game either, so the one to avoid
     * repeating is the last one actually played.
     *
     * <p>The draw happens inside the write, so two quick starts see the same history.
     */
    public GameState newUnlimited(Player player, Kind kind, Region region, Variant variant) {
        requirePlayable(kind, variant);
        AnswerPools.Pool pool = pools.pool(kind, region, variant);
        return write(() -> {
            Instant now = clock.now();
            for (GlobleGame abandoned : games.findPlaying(player, kind, region, variant)) {
                if (abandoned.getGuessCount() == 0) {
                    games.delete(abandoned);
                } else {
                    abandoned.finish(Status.GAVE_UP, now);
                    games.save(abandoned);
                }
            }
            String last = games.findLastUnlimited(player, kind, region, variant)
                    .map(GlobleGame::getAnswerCode).orElse(null);
            String answer = pool.draw(random, country -> !country.code().equals(last))
                    .or(() -> pool.draw(random, country -> true))
                    .orElseThrow()
                    .code();
            GlobleGame game = games.save(GlobleGame.unlimited(player, kind, region, variant, answer, now));
            return views.of(game, answer);
        });
    }

    public GameState guessUnlimited(Player player, long id, String rawCode) {
        Country guess = parse(rawCode);
        return write(() -> {
            GlobleGame game = games.findUnlimited(player, id).orElseThrow(GlobleException::notFound);
            applyGuess(game, guess, game.getAnswerCode());
            return views.of(games.save(game), game.getAnswerCode());
        });
    }

    public GameState giveUpUnlimited(Player player, long id) {
        return write(() -> {
            GlobleGame game = games.findUnlimited(player, id).orElseThrow(GlobleException::notFound);
            giveUp(game);
            return views.of(games.save(game), game.getAnswerCode());
        });
    }

    // ------------------------------------------------------------------ rules

    /** Hard is a country game: there is no Hard capitals. */
    private static void requirePlayable(Kind kind, Variant variant) {
        if (variant == Variant.HARD && kind != Kind.COUNTRY) {
            throw GlobleException.badRequest();
        }
    }

    /**
     * Today's puzzle date, unless the page says it is showing another one.
     *
     * <p>Daily moves carry no game id, so without this a tab left open across midnight
     * would have its next guess or give-up applied to the new day's puzzle, one the
     * player has never seen: a give-up would reveal today's answer and end their streak.
     * The page sends the date it is showing, and a stale one is refused with
     * {@code new-day} so the page can load the new puzzle instead. No date at all means
     * today, as before.
     */
    private LocalDate today(String expectedDate) {
        LocalDate today = clock.today();
        if (expectedDate == null) {
            return today;
        }
        LocalDate expected;
        try {
            expected = LocalDate.parse(expectedDate);
        } catch (DateTimeParseException e) {
            throw GlobleException.badRequest();
        }
        if (!expected.equals(today)) {
            throw GlobleException.newDay();
        }
        return today;
    }

    /**
     * Codes arrive as the client sent them; case and surrounding space are forgiven,
     * anything else that is not in the catalogue is refused.
     */
    private Country parse(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw GlobleException.badRequest();
        }
        return catalog.find(rawCode.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(GlobleException::unknownCountry);
    }

    private void applyGuess(GlobleGame game, Country guess, String answer) {
        if (!game.isPlaying()) {
            throw GlobleException.finished();
        }
        if (game.getKind() == Kind.CAPITAL && !guess.hasCapital()) {
            throw GlobleException.noCapital();
        }
        if (game.hasGuessed(guess.code())) {
            throw GlobleException.alreadyGuessed();
        }
        if (game.getGuessCount() >= properties.getMaxGuesses()) {
            throw GlobleException.badRequest();
        }
        game.addGuess(guess.code());
        if (guess.code().equals(answer)) {
            game.finish(Status.WON, clock.now());
        }
    }

    private void giveUp(GlobleGame game) {
        if (!game.isPlaying()) {
            throw GlobleException.finished();
        }
        game.finish(Status.GAVE_UP, clock.now());
    }

    /** One transaction, under the app-wide write lock; see the class comment. */
    private GameState write(Supplier<GameState> work) {
        return GlobleWrites.run(() -> transaction.execute(status -> work.get()));
    }
}

package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.view.GameState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the {@link GameState} the client sees from a stored game.
 *
 * <p>This is where the rule that matters most is enforced: the answer is attached only
 * once the game is over. Distances are worked out here too, from the catalogue, rather
 * than stored with each guess.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GameViews {

    /** Seconds always present, so the client's Date.parse never sees "T00:00-04:00". */
    private static final DateTimeFormatter OFFSET_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final CountryCatalog catalog;
    private final DailyPuzzles puzzles;
    private final GlobleClock clock;

    public GameViews(CountryCatalog catalog, DailyPuzzles puzzles, GlobleClock clock) {
        this.catalog = catalog;
        this.puzzles = puzzles;
        this.clock = clock;
    }

    /** Today's daily before the player has done anything: nothing stored, nothing to show. */
    public GameState untouchedDaily(LocalDate date, Variant variant) {
        return new GameState(null, Mode.DAILY.api(), Kind.COUNTRY.api(), Region.WORLD.api(), variant.api(),
                puzzles.puzzleNumber(date), date.toString(), Status.PLAYING.name(),
                List.of(), 0, null, null, nextPuzzleAt(date));
    }

    /**
     * @param answerCode the mystery: the day's for a daily, the game's own otherwise.
     *                   Used for distances always, shown only when the game is over.
     */
    public GameState of(GlobleGame game, String answerCode) {
        Country answer = catalog.require(answerCode);
        Kind kind = game.getKind();

        List<GameState.Guess> guesses = new ArrayList<>();
        Integer closest = null;
        int order = 0;
        for (String code : game.guessCodes()) {
            order++;
            // A code dropped from the data since the guess was made is skipped rather
            // than failing the whole game; its turn still counts in guessCount.
            Country guess = catalog.find(code).orElse(null);
            if (guess == null) {
                continue;
            }
            int km = distance(kind, guess, answer);
            closest = closest == null ? km : Math.min(closest, km);
            guesses.add(new GameState.Guess(guess.code(), guess.name(), guess.iso2(), km, order,
                    kind == Kind.CAPITAL ? place(guess) : null));
        }

        boolean daily = game.getMode() == Mode.DAILY;
        LocalDate date = game.getPuzzleDate();
        return new GameState(
                daily ? null : game.getId(),
                game.getMode().api(),
                kind.api(),
                game.getRegion().api(),
                game.getVariant().api(),
                daily ? puzzles.puzzleNumber(date) : null,
                daily ? date.toString() : null,
                game.getStatus().name(),
                List.copyOf(guesses),
                game.getGuessCount(),
                closest,
                game.isPlaying() ? null : reveal(answer),
                daily ? nextPuzzleAt(date) : null);
    }

    private int distance(Kind kind, Country guess, Country answer) {
        return kind == Kind.CAPITAL
                ? catalog.capitalKm(guess.code(), answer.code())
                : catalog.km(guess.code(), answer.code());
    }

    private static GameState.Answer reveal(Country answer) {
        return new GameState.Answer(answer.code(), answer.name(), answer.iso2(), answer.lat(), answer.lng(),
                place(answer));
    }

    private static GameState.Place place(Country country) {
        Country.Capital capital = country.capital();
        return capital == null ? null : new GameState.Place(capital.name(), capital.lat(), capital.lng());
    }

    /** The midnight that ends {@code date}'s puzzles, as the client shows it. */
    public String nextPuzzleAt(LocalDate date) {
        return clock.startOf(date.plusDays(1)).format(OFFSET_TIME);
    }
}

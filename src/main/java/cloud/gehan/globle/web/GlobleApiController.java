package cloud.gehan.globle.web;

import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.service.GameService;
import cloud.gehan.globle.service.GlobleException;
import cloud.gehan.globle.service.HomeService;
import cloud.gehan.globle.service.LeaderboardService;
import cloud.gehan.globle.service.Players;
import cloud.gehan.globle.view.CountryView;
import cloud.gehan.globle.view.GameState;
import cloud.gehan.globle.view.Home;
import cloud.gehan.globle.view.Leaderboard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Globle's JSON API. Thin by design: it resolves who is asking, validates the shape
 * of the request, and hands over to the services. Errors become
 * {@code {"error": "<code>"}} in {@link GlobleApiErrors}.
 *
 * <p>Sign-in and CSRF are enforced in front of this by GlobleSecurityConfig; a request
 * that reaches a method here is from a signed-in session.
 *
 * <p>{@code variant} ("classic" or "hard") is optional everywhere and means classic
 * when left out, so every request shape from before Hard keeps its meaning.
 */
@RestController
@RequestMapping("/globle/api")
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleApiController {

    private final GameService games;
    private final LeaderboardService leaderboard;
    private final HomeService home;
    private final Players players;
    private final List<CountryView> countries;

    public GlobleApiController(CountryCatalog catalog, GameService games, LeaderboardService leaderboard,
                               HomeService home, Players players) {
        this.games = games;
        this.leaderboard = leaderboard;
        this.home = home;
        this.players = players;
        this.countries = catalog.all().stream().map(CountryView::of).toList();
    }

    /** Every guessable place for the autocomplete. The same for everybody, so built once. */
    @GetMapping("/countries")
    public List<CountryView> countries() {
        return countries;
    }

    /**
     * The home screen: today's two dailies, streaks, practice games and "Name them all"
     * runs in progress, and for the admin how many name requests are open.
     */
    @GetMapping("/home")
    public Home home(Authentication authentication) {
        return home.home(players.require(authentication), players.isAdmin(authentication));
    }

    @GetMapping("/daily")
    public GameState daily(Authentication authentication,
                           @RequestParam(name = "variant", required = false) String variant) {
        return games.daily(players.require(authentication), variant(variant));
    }

    /** {@code date} is optional; when sent, a stale one is refused with 409 new-day. */
    @PostMapping("/daily/guess")
    public GameState guessDaily(Authentication authentication, @RequestBody GuessRequest body) {
        return games.guessDaily(players.require(authentication), variant(body.variant()), body.code(), body.date());
    }

    /** The body is optional; see {@link DailyRequest}. */
    @PostMapping("/daily/give-up")
    public GameState giveUpDaily(Authentication authentication,
                                 @RequestBody(required = false) DailyRequest body) {
        DailyRequest request = body == null ? new DailyRequest(null, null) : body;
        return games.giveUpDaily(players.require(authentication), variant(request.variant()), request.date());
    }

    /**
     * The game in progress in one slot, (kind, region, variant), or 204 when there is
     * none, so the client offers to start one. Without a region, the newest game of
     * that kind and variant in any region: how this answered before slots existed.
     */
    @GetMapping("/unlimited/current")
    public ResponseEntity<GameState> current(Authentication authentication,
                                             @RequestParam(name = "kind", required = false) String kind,
                                             @RequestParam(name = "region", required = false) String region,
                                             @RequestParam(name = "variant", required = false) String variant) {
        Region slotRegion = region == null || region.isBlank() ? null : region(region);
        return games.currentUnlimited(players.require(authentication), kind(kind), slotRegion, variant(variant))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/unlimited/new")
    public GameState newGame(Authentication authentication, @RequestBody(required = false) NewGameRequest body) {
        NewGameRequest request = body == null ? new NewGameRequest(null, null, null) : body;
        return games.newUnlimited(players.require(authentication), kind(request.kind()), region(request.region()),
                variant(request.variant()));
    }

    @PostMapping("/unlimited/{id}/guess")
    public GameState guessUnlimited(Authentication authentication, @PathVariable("id") long id,
                                    @RequestBody GuessRequest body) {
        return games.guessUnlimited(players.require(authentication), id, body.code());
    }

    @PostMapping("/unlimited/{id}/give-up")
    public GameState giveUpUnlimited(Authentication authentication, @PathVariable("id") long id) {
        return games.giveUpUnlimited(players.require(authentication), id);
    }

    /** {@code variant} picks which daily the Today, Week, All time and Me sections describe. */
    @GetMapping("/leaderboard")
    public Leaderboard leaderboard(Authentication authentication,
                                   @RequestParam(name = "variant", required = false) String variant) {
        return leaderboard.leaderboard(players.require(authentication), variant(variant));
    }

    private static Kind kind(String value) {
        return Kind.parse(value).orElseThrow(GlobleException::badRequest);
    }

    private static Region region(String value) {
        return Region.parse(value).orElseThrow(GlobleException::badRequest);
    }

    private static Variant variant(String value) {
        return Variant.parse(value).orElseThrow(GlobleException::badRequest);
    }

    /**
     * {@code {"code": "FRA"}}; for a capitals game the code is the capital's country. A
     * daily guess may add {@code "date": "yyyy-MM-dd"}, the puzzle the page is showing,
     * and {@code "variant"}; unlimited games ignore both.
     */
    public record GuessRequest(String code, String date, String variant) {
    }

    /**
     * {@code {"date": "yyyy-MM-dd", "variant": "classic"|"hard"}}. The date is the daily
     * puzzle the page is showing, so a move made after midnight is not applied to a
     * puzzle the player has not seen. Both optional.
     */
    public record DailyRequest(String date, String variant) {
    }

    /**
     * {@code {"kind": "country"|"capital", "region": "world"|"africa"|...|"mainland",
     * "variant": "classic"|"hard"}}; all optional. Hard is countries only.
     */
    public record NewGameRequest(String kind, String region, String variant) {
    }
}

package cloud.gehan.globle.web;

import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.service.GameService;
import cloud.gehan.globle.service.GlobleException;
import cloud.gehan.globle.service.LeaderboardService;
import cloud.gehan.globle.service.Players;
import cloud.gehan.globle.view.CountryView;
import cloud.gehan.globle.view.GameState;
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
 */
@RestController
@RequestMapping("/globle/api")
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleApiController {

    private final GameService games;
    private final LeaderboardService leaderboard;
    private final Players players;
    private final List<CountryView> countries;

    public GlobleApiController(CountryCatalog catalog, GameService games, LeaderboardService leaderboard,
                               Players players) {
        this.games = games;
        this.leaderboard = leaderboard;
        this.players = players;
        this.countries = catalog.all().stream().map(CountryView::of).toList();
    }

    /** Every guessable place for the autocomplete. The same for everybody, so built once. */
    @GetMapping("/countries")
    public List<CountryView> countries() {
        return countries;
    }

    @GetMapping("/daily")
    public GameState daily(Authentication authentication) {
        return games.daily(players.require(authentication));
    }

    /** {@code date} is optional; when sent, a stale one is refused with 409 new-day. */
    @PostMapping("/daily/guess")
    public GameState guessDaily(Authentication authentication, @RequestBody GuessRequest body) {
        return games.guessDaily(players.require(authentication), body.code(), body.date());
    }

    /** The body is optional; see {@link DailyRequest}. */
    @PostMapping("/daily/give-up")
    public GameState giveUpDaily(Authentication authentication,
                                 @RequestBody(required = false) DailyRequest body) {
        return games.giveUpDaily(players.require(authentication), body == null ? null : body.date());
    }

    /** 204 when there is no game of this kind in progress, so the client offers to start one. */
    @GetMapping("/unlimited/current")
    public ResponseEntity<GameState> current(Authentication authentication,
                                             @RequestParam(name = "kind", required = false) String kind) {
        return games.currentUnlimited(players.require(authentication), kind(kind))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/unlimited/new")
    public GameState newGame(Authentication authentication, @RequestBody(required = false) NewGameRequest body) {
        NewGameRequest request = body == null ? new NewGameRequest(null, null) : body;
        return games.newUnlimited(players.require(authentication), kind(request.kind()), region(request.region()));
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

    @GetMapping("/leaderboard")
    public Leaderboard leaderboard(Authentication authentication) {
        return leaderboard.leaderboard(players.require(authentication));
    }

    private static Kind kind(String value) {
        return Kind.parse(value).orElseThrow(GlobleException::badRequest);
    }

    private static Region region(String value) {
        return Region.parse(value).orElseThrow(GlobleException::badRequest);
    }

    /**
     * {@code {"code": "FRA"}}; for a capitals game the code is the capital's country. A
     * daily guess may add {@code "date": "yyyy-MM-dd"}, the puzzle the page is showing;
     * unlimited games ignore it.
     */
    public record GuessRequest(String code, String date) {
    }

    /**
     * {@code {"date": "yyyy-MM-dd"}}, the daily puzzle the page is showing, so a move made
     * after midnight is not applied to a puzzle the player has not seen. Optional.
     */
    public record DailyRequest(String date) {
    }

    /** {@code {"kind": "country"|"capital", "region": "world"|"africa"|...}}; both optional. */
    public record NewGameRequest(String kind, String region) {
    }
}

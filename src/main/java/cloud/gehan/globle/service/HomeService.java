package cloud.gehan.globle.service;

import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.view.Home;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

/**
 * What the home screen shows: today's classic and Hard dailies as the player left
 * them, their streaks, and the practice games and "Name them all" runs they can
 * resume. The two lists are separate: a roll call run is never a practice game.
 *
 * <p>Read-only for a player. It never draws a day's answer, so opening the home screen
 * before anyone has played leaves nothing stored, as looking at a daily always has.
 * The admin's also counts the open "request a name change"s, and counting them closes
 * the ones the data already names (NameRequestService), so the count is what is really
 * left to do.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HomeService {

    /** A daily the player has not touched today. */
    static final String NEW = "NEW";

    private final GlobleGameRepository games;
    private final LeaderboardService leaderboard;
    private final DailyPuzzles puzzles;
    private final GameViews views;
    private final GlobleClock clock;
    private final RollCallPools rollCallPools;
    private final NameRequestService nameRequests;

    public HomeService(GlobleGameRepository games, LeaderboardService leaderboard, DailyPuzzles puzzles,
                       GameViews views, GlobleClock clock, RollCallPools rollCallPools,
                       NameRequestService nameRequests) {
        this.games = games;
        this.leaderboard = leaderboard;
        this.puzzles = puzzles;
        this.views = views;
        this.clock = clock;
        this.rollCallPools = rollCallPools;
        this.nameRequests = nameRequests;
    }

    /** @param admin whether the session has the admin role; only then is {@code nameRequests} counted */
    public Home home(Player player, boolean admin) {
        LocalDate today = clock.today();
        return new Home(
                puzzles.puzzleNumber(today),
                today.toString(),
                views.nextPuzzleAt(today),
                daily(player, today, Variant.CLASSIC),
                daily(player, today, Variant.HARD),
                games.findPlaying(player).stream()
                        .map(game -> new Home.InProgress(game.getId(), game.getKind().api(), game.getRegion().api(),
                                game.getVariant().api(), game.getGuessCount()))
                        .toList(),
                games.findRollCallsPlaying(player).stream()
                        .map(run -> new Home.RollCall(run.getId(), run.getRegion().api(), run.getGuessCount(),
                                rollCallPools.pool(run.getRegion()).size()))
                        .toList(),
                admin,
                admin ? nameRequests.openCount() : null);
    }

    private Home.Daily daily(Player player, LocalDate today, Variant variant) {
        Optional<GlobleGame> game = games.findDaily(player, today, variant);
        return new Home.Daily(
                game.map(g -> g.getStatus().name()).orElse(NEW),
                game.map(GlobleGame::getGuessCount).orElse(0),
                leaderboard.currentStreak(player, variant));
    }
}

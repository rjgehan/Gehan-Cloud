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
 * them, their streaks, and the practice games they can resume.
 *
 * <p>Read-only. It never draws a day's answer, so opening the home screen before
 * anyone has played leaves nothing stored, as looking at a daily always has.
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

    public HomeService(GlobleGameRepository games, LeaderboardService leaderboard, DailyPuzzles puzzles,
                       GameViews views, GlobleClock clock) {
        this.games = games;
        this.leaderboard = leaderboard;
        this.puzzles = puzzles;
        this.views = views;
        this.clock = clock;
    }

    public Home home(Player player) {
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
                        .toList());
    }

    private Home.Daily daily(Player player, LocalDate today, Variant variant) {
        Optional<GlobleGame> game = games.findDaily(player, today, variant);
        return new Home.Daily(
                game.map(g -> g.getStatus().name()).orElse(NEW),
                game.map(GlobleGame::getGuessCount).orElse(0),
                leaderboard.currentStreak(player, variant));
    }
}

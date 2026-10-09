package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Every lookup that belongs to a player takes the whole {@link Player}, id and
 * username together, so there is no query that could hand one person another's game.
 */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleGameRepository extends JpaRepository<GlobleGame, Long> {

    Optional<GlobleGame> findByUserIdAndUsernameAndModeAndPuzzleDate(
            long userId, String username, Mode mode, LocalDate puzzleDate);

    List<GlobleGame> findByUserIdAndUsernameAndModeAndKindAndStatusOrderByIdDesc(
            long userId, String username, Mode mode, Kind kind, Status status);

    Optional<GlobleGame> findByIdAndUserIdAndUsernameAndMode(
            long id, long userId, String username, Mode mode);

    Optional<GlobleGame> findFirstByUserIdAndUsernameAndModeAndKindOrderByIdDesc(
            long userId, String username, Mode mode, Kind kind);

    @Query("select new cloud.gehan.globle.repository.GameSummary("
            + "g.userId, g.username, g.kind, g.status, g.puzzleDate, g.guessCount, g.finishedAt) "
            + "from GlobleGame g where g.mode = :mode")
    List<GameSummary> summaries(@Param("mode") Mode mode);

    default Optional<GlobleGame> findDaily(Player player, LocalDate date) {
        return findByUserIdAndUsernameAndModeAndPuzzleDate(player.id(), player.username(), Mode.DAILY, date);
    }

    /** In-progress unlimited games of a kind, newest first. Normally zero or one. */
    default List<GlobleGame> findPlaying(Player player, Kind kind) {
        return findByUserIdAndUsernameAndModeAndKindAndStatusOrderByIdDesc(
                player.id(), player.username(), Mode.UNLIMITED, kind, Status.PLAYING);
    }

    /** The player's most recent unlimited game of this kind, in any status. */
    default Optional<GlobleGame> findLastUnlimited(Player player, Kind kind) {
        return findFirstByUserIdAndUsernameAndModeAndKindOrderByIdDesc(
                player.id(), player.username(), Mode.UNLIMITED, kind);
    }

    /** An unlimited game, but only if it is this player's. */
    default Optional<GlobleGame> findUnlimited(Player player, long id) {
        return findByIdAndUserIdAndUsernameAndMode(id, player.id(), player.username(), Mode.UNLIMITED);
    }
}

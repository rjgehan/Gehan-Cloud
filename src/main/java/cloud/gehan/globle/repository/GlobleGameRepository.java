package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Mode;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Every lookup that belongs to a player takes the whole {@link Player}, id and
 * username together, so there is no query that could hand one person another's game.
 *
 * <p><b>Variants.</b> Rows from before Hard have no variant and are classic (see
 * GlobleGame). Lookups by variant therefore either filter in Java on
 * {@link GlobleGame#getVariant()}, which reads null as classic, or, for classic, ask
 * for the stored value and for null and take the newer. None of them compares the
 * column with CLASSIC alone, which would lose every game played before the upgrade.
 *
 * <p><b>Modes.</b> Every lookup names its mode, so a roll call run is never found as a
 * daily or a practice game, nor the other way round: an id from one is "not found" on
 * the other's routes, and no board or home summary counts another mode's rows.
 */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleGameRepository extends JpaRepository<GlobleGame, Long> {

    /** Normally one row per variant: the classic and the Hard daily share a date. */
    List<GlobleGame> findAllByUserIdAndUsernameAndModeAndPuzzleDate(
            long userId, String username, Mode mode, LocalDate puzzleDate);

    List<GlobleGame> findByUserIdAndUsernameAndModeAndStatusOrderByIdDesc(
            long userId, String username, Mode mode, Status status);

    Optional<GlobleGame> findByIdAndUserIdAndUsernameAndMode(
            long id, long userId, String username, Mode mode);

    Optional<GlobleGame> findFirstByUserIdAndUsernameAndModeAndKindAndRegionAndVariantOrderByIdDesc(
            long userId, String username, Mode mode, Kind kind, Region region, Variant variant);

    Optional<GlobleGame> findFirstByUserIdAndUsernameAndModeAndKindAndRegionAndVariantIsNullOrderByIdDesc(
            long userId, String username, Mode mode, Kind kind, Region region);

    List<GlobleGame> findByUserIdAndUsernameAndModeAndRegionAndStatusOrderByIdDesc(
            long userId, String username, Mode mode, Region region, Status status);

    /** Whole rows of one mode, region and status, by everybody: for "Study what you missed". */
    List<GlobleGame> findByModeAndRegionAndStatus(Mode mode, Region region, Status status);

    /** Rows of one mode, region and status, by everybody: for the "Name them all" board. */
    @Query("select new cloud.gehan.globle.repository.RollCallSummary("
            + "g.userId, g.username, g.region, g.guessCount, g.autofillCount, g.startedAt, g.finishedAt) "
            + "from GlobleGame g where g.mode = :mode and g.region = :region and g.status = :status")
    List<RollCallSummary> rollCallSummaries(@Param("mode") Mode mode, @Param("region") Region region,
                                            @Param("status") Status status);

    @Query("select new cloud.gehan.globle.repository.GameSummary("
            + "g.userId, g.username, g.kind, g.variant, g.status, g.puzzleDate, g.guessCount, g.finishedAt) "
            + "from GlobleGame g where g.mode = :mode")
    List<GameSummary> summaries(@Param("mode") Mode mode);

    @Query("select new cloud.gehan.globle.repository.GameSummary("
            + "g.userId, g.username, g.kind, g.variant, g.status, g.puzzleDate, g.guessCount, g.finishedAt) "
            + "from GlobleGame g where g.mode = :mode and g.userId = :userId and g.username = :username")
    List<GameSummary> summaries(@Param("mode") Mode mode, @Param("userId") long userId,
                                @Param("username") String username);

    /** The player's classic daily for a date. */
    default Optional<GlobleGame> findDaily(Player player, LocalDate date) {
        return findDaily(player, date, Variant.CLASSIC);
    }

    default Optional<GlobleGame> findDaily(Player player, LocalDate date, Variant variant) {
        return findAllByUserIdAndUsernameAndModeAndPuzzleDate(player.id(), player.username(), Mode.DAILY, date)
                .stream()
                .filter(game -> game.getVariant() == variant)
                .findFirst();
    }

    /** All the player's unlimited games in progress, in every slot, newest first. */
    default List<GlobleGame> findPlaying(Player player) {
        return findByUserIdAndUsernameAndModeAndStatusOrderByIdDesc(
                player.id(), player.username(), Mode.UNLIMITED, Status.PLAYING);
    }

    /**
     * In-progress unlimited games of a kind and variant, in any region, newest first.
     * Normally at most one per region.
     */
    default List<GlobleGame> findPlaying(Player player, Kind kind, Variant variant) {
        return findPlaying(player).stream()
                .filter(game -> game.getKind() == kind && game.getVariant() == variant)
                .toList();
    }

    /** In-progress games in one slot, newest first. Normally zero or one. */
    default List<GlobleGame> findPlaying(Player player, Kind kind, Region region, Variant variant) {
        return findPlaying(player).stream()
                .filter(game -> game.inSlot(kind, region, variant))
                .toList();
    }

    /** The player's most recent unlimited game in this slot, in any status. */
    default Optional<GlobleGame> findLastUnlimited(Player player, Kind kind, Region region, Variant variant) {
        Optional<GlobleGame> tagged = findFirstByUserIdAndUsernameAndModeAndKindAndRegionAndVariantOrderByIdDesc(
                player.id(), player.username(), Mode.UNLIMITED, kind, region, variant);
        if (variant != Variant.CLASSIC) {
            return tagged;
        }
        Optional<GlobleGame> untagged = findFirstByUserIdAndUsernameAndModeAndKindAndRegionAndVariantIsNullOrderByIdDesc(
                player.id(), player.username(), Mode.UNLIMITED, kind, region);
        return Stream.concat(tagged.stream(), untagged.stream()).max(Comparator.comparing(GlobleGame::getId));
    }

    /** An unlimited game, but only if it is this player's. */
    default Optional<GlobleGame> findUnlimited(Player player, long id) {
        return findByIdAndUserIdAndUsernameAndMode(id, player.id(), player.username(), Mode.UNLIMITED);
    }

    // ------------------------------------------------------------------ roll call

    /** A roll call run, but only if it is this player's. */
    default Optional<GlobleGame> findRollCall(Player player, long id) {
        return findByIdAndUserIdAndUsernameAndMode(id, player.id(), player.username(), Mode.ROLLCALL);
    }

    /** The player's roll call runs in progress, in every region, newest first. */
    default List<GlobleGame> findRollCallsPlaying(Player player) {
        return findByUserIdAndUsernameAndModeAndStatusOrderByIdDesc(
                player.id(), player.username(), Mode.ROLLCALL, Status.PLAYING);
    }

    /** The player's roll call runs in progress in one region, newest first. Normally zero or one. */
    default List<GlobleGame> findRollCallsPlaying(Player player, Region region) {
        return findByUserIdAndUsernameAndModeAndRegionAndStatusOrderByIdDesc(
                player.id(), player.username(), Mode.ROLLCALL, region, Status.PLAYING);
    }

    /**
     * The player's finished roll call runs in one region, newest first, whether they
     * count or not. Newest by id is newest by finish too: a region has at most one run in
     * progress, so its runs finish in the order they started.
     */
    default List<GlobleGame> findRollCallsFinished(Player player, Region region) {
        return findByUserIdAndUsernameAndModeAndRegionAndStatusOrderByIdDesc(
                player.id(), player.username(), Mode.ROLLCALL, region, Status.FINISHED);
    }

    /**
     * The player's roll call runs in one region that count, newest first: finished, with
     * at least one name typed ({@link GlobleGame#counts()}). Their best, how many they
     * have finished and what "mine" fills in are worked out from these alone.
     */
    default List<GlobleGame> findRollCallsCounting(Player player, Region region) {
        return findRollCallsFinished(player, region).stream().filter(GlobleGame::counts).toList();
    }

    /**
     * Everybody's roll call runs in one region that count, in no particular order,
     * including any of accounts that no longer exist: the caller decides who is listed.
     */
    default List<GlobleGame> findRollCallsCounting(Region region) {
        return findByModeAndRegionAndStatus(Mode.ROLLCALL, region, Status.FINISHED).stream()
                .filter(GlobleGame::counts)
                .toList();
    }
}

package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Status;
import cloud.gehan.globle.model.Variant;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A game without its guess list: everything the leaderboard counts. Selecting just
 * these columns keeps the leaderboard query small however many games pile up.
 *
 * @param variant never null: a row from before Hard, whose column is empty, is classic
 */
public record GameSummary(Long userId, String username, Kind kind, Variant variant, Status status,
                          LocalDate puzzleDate, Integer guessCount, Instant finishedAt) {

    public GameSummary {
        variant = Variant.orClassic(variant);
    }

    public boolean won() {
        return status == Status.WON;
    }
}

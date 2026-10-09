package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Status;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A game without its guess list: everything the leaderboard counts. Selecting just
 * these columns keeps the leaderboard query small however many games pile up.
 */
public record GameSummary(Long userId, String username, Kind kind, Status status,
                          LocalDate puzzleDate, Integer guessCount, Instant finishedAt) {

    public boolean won() {
        return status == Status.WON;
    }
}

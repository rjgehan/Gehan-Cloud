package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.PuzzleDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * What the daily draw needs from a table of daily answers, whichever variant it holds.
 * Keyed by the puzzle date as ISO text, which sorts in date order.
 */
@NoRepositoryBean
public interface PuzzleDayRepository<T extends PuzzleDay> extends JpaRepository<T, String> {

    /** Days whose ISO date falls in {@code [from, to]}, both ends included. */
    List<T> findByPuzzleDateBetween(String from, String to);
}

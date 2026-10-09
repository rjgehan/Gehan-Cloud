package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleDay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Keyed by the puzzle date as ISO text, which sorts in date order. */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleDayRepository extends JpaRepository<GlobleDay, String> {

    /** Days whose ISO date falls in {@code [from, to]}, both ends included. */
    List<GlobleDay> findByPuzzleDateBetween(String from, String to);
}

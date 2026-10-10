package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleHardDay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** The Hard daily's answers. */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleHardDayRepository extends PuzzleDayRepository<GlobleHardDay> {
}

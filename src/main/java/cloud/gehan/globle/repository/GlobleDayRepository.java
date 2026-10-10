package cloud.gehan.globle.repository;

import cloud.gehan.globle.model.GlobleDay;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** The classic daily's answers. */
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public interface GlobleDayRepository extends PuzzleDayRepository<GlobleDay> {
}

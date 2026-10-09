package cloud.gehan.globle.service;

import cloud.gehan.globle.config.GlobleProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Globle's own sense of time: what instant it is, and which puzzle day that falls on.
 *
 * <p>Time comes from a Clock bean qualified "globleTimeSource" if one exists, otherwise the
 * system clock. Nothing in main code defines that bean; it is the seam tests use to
 * pin and move time across days. Asking only for that qualifier, rather than for any
 * Clock, means a Clock some other part of the app might one day define can never
 * change which puzzle is live.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleClock {

    private final Clock clock;
    private final ZoneId zone;

    public GlobleClock(GlobleProperties properties, @Qualifier("globleTimeSource") ObjectProvider<Clock> override) {
        this.zone = properties.getZone();
        this.clock = override.getIfAvailable(Clock::systemUTC);
    }

    public Instant now() {
        return clock.instant();
    }

    /** Today's puzzle date: the calendar date in the configured zone. */
    public LocalDate today() {
        return LocalDate.ofInstant(now(), zone);
    }

    /**
     * Midnight at the start of {@code date} in the puzzle zone. On a day whose midnight
     * a DST change skips, that is the first instant that exists.
     */
    public OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(zone).toOffsetDateTime();
    }
}

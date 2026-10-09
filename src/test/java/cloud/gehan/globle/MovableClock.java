package cloud.gehan.globle;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A clock tests can set and move, registered as the "globleTimeSource" bean that
 * GlobleClock looks for. Only Globle reads it: nothing else in the app asks for that
 * qualifier.
 */
public class MovableClock extends Clock {

    public static final ZoneId PUZZLE_ZONE = ZoneId.of("America/New_York");

    private volatile Instant now = Instant.parse("2026-10-09T16:00:00Z");

    /** Noon on {@code day} in the puzzle zone, well clear of either midnight. */
    public void setDay(LocalDate day) {
        now = day.atTime(12, 0).atZone(PUZZLE_ZONE).toInstant();
    }

    public void set(Instant instant) {
        now = instant;
    }

    public void advance(Duration duration) {
        now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return PUZZLE_ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }
}

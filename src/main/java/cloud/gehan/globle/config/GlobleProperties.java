package cloud.gehan.globle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Every setting Globle has, each with a working default, so the game needs no line in
 * application.properties to run - and removing it leaves nothing behind there.
 *
 * <p>Registered by {@link GlobleConfiguration}, which is itself gated on
 * {@code globle.enabled}; with the game switched off this class is never bound.
 */
@ConfigurationProperties(prefix = "globle")
public class GlobleProperties {

    /**
     * False removes the game entirely: no routes, no filter chain, no beans. The
     * condition is read by each bean's {@code @ConditionalOnProperty}; this field is
     * here so the knob is documented next to the others.
     */
    private boolean enabled = true;

    /**
     * Whose midnight starts the next daily puzzle. One zone for everybody, because the
     * point of the daily is that the family is all guessing the same country.
     */
    private ZoneId zone = ZoneId.of("America/New_York");

    /** The date of puzzle #1. Puzzle numbers count days from here. */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate epoch = LocalDate.of(2026, 10, 9);

    /**
     * A daily answer is not reused within this many days on either side. Values above a
     * third of the possible answers (65) are treated as 65, so that the day's pool stays
     * large enough not to give the answer away; see DailyPuzzles.
     */
    private int answerRepeatWindowDays = 60;

    /**
     * A ceiling on guesses per game. Duplicates are refused, so the country list caps a
     * game well below this already; it is a guard against a row growing without bound
     * if that ever stops being true, not a rule of the game.
     */
    private int maxGuesses = 400;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ZoneId getZone() {
        return zone;
    }

    public void setZone(ZoneId zone) {
        if (zone == null) {
            throw new IllegalArgumentException("globle.zone must name a time zone, e.g. America/New_York");
        }
        this.zone = zone;
    }

    public LocalDate getEpoch() {
        return epoch;
    }

    public void setEpoch(LocalDate epoch) {
        if (epoch == null) {
            throw new IllegalArgumentException("globle.epoch must be a date, e.g. 2026-10-09");
        }
        this.epoch = epoch;
    }

    public int getAnswerRepeatWindowDays() {
        return answerRepeatWindowDays;
    }

    public void setAnswerRepeatWindowDays(int answerRepeatWindowDays) {
        if (answerRepeatWindowDays < 0) {
            throw new IllegalArgumentException("globle.answer-repeat-window-days cannot be negative");
        }
        this.answerRepeatWindowDays = answerRepeatWindowDays;
    }

    public int getMaxGuesses() {
        return maxGuesses;
    }

    public void setMaxGuesses(int maxGuesses) {
        if (maxGuesses < 1) {
            throw new IllegalArgumentException("globle.max-guesses must be at least 1");
        }
        this.maxGuesses = maxGuesses;
    }
}

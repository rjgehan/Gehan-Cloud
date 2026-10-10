package cloud.gehan.globle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

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
     * A daily answer is not reused within this many days on either side, per daily. Each
     * daily caps it at a third of its own pool (65 classic, 55 Hard with the default
     * weights), so that the day's pool stays large enough not to give the answer away;
     * see DailyPuzzles.
     */
    private int answerRepeatWindowDays = 60;

    /**
     * A ceiling on guesses per game. Duplicates are refused, so the country list caps a
     * game well below this already; it is a guard against a row growing without bound
     * if that ever stops being true, not a rule of the game.
     */
    private int maxGuesses = 400;

    /**
     * How strongly Hard's draw favours each difficulty tier (1 = everyone could find it,
     * 5 = obscure): a tier-5 country at 8 is eight times as likely as a tier-2 one at 1,
     * and a tier at 0 is never drawn. By default tier 1 is left out entirely and about
     * three in four of a single draw are tier 4 or 5 (about seven in ten Hard dailies,
     * after the no-repeat window). Set one tier with e.g.
     * {@code globle.hard-weights.1=1}; the others keep their defaults. Read by
     * AnswerPools.
     */
    private Map<Integer, Integer> hardWeights = defaultHardWeights();

    private static Map<Integer, Integer> defaultHardWeights() {
        Map<Integer, Integer> weights = new TreeMap<>();
        weights.put(1, 0);
        weights.put(2, 1);
        weights.put(3, 3);
        weights.put(4, 6);
        weights.put(5, 8);
        return weights;
    }

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

    /** Tier to weight, tiers 1 to 5. Read-only; a tier missing from it weighs 0. */
    public Map<Integer, Integer> getHardWeights() {
        return Collections.unmodifiableMap(hardWeights);
    }

    /** Weight of one difficulty tier in Hard's draw; 0 means never drawn. */
    public int hardWeight(int difficulty) {
        return hardWeights.getOrDefault(difficulty, 0);
    }

    /**
     * Spring merges what is configured into the defaults before calling this, so a
     * property for one tier leaves the rest as they were.
     */
    public void setHardWeights(Map<Integer, Integer> hardWeights) {
        if (hardWeights == null) {
            throw new IllegalArgumentException("globle.hard-weights must map tiers 1-5 to weights");
        }
        Map<Integer, Integer> checked = new TreeMap<>();
        hardWeights.forEach((tier, weight) -> {
            if (tier == null || tier < 1 || tier > 5) {
                throw new IllegalArgumentException("globle.hard-weights: tiers are 1 to 5, not " + tier);
            }
            if (weight == null || weight < 0 || weight > 1000) {
                throw new IllegalArgumentException("globle.hard-weights." + tier + " must be from 0 to 1000");
            }
            checked.put(tier, weight);
        });
        if (checked.values().stream().noneMatch(weight -> weight > 0)) {
            throw new IllegalArgumentException("globle.hard-weights needs at least one tier above 0");
        }
        this.hardWeights = checked;
    }
}

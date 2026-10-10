package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * How a game is played: the classic game, or Hard (a blind globe, no suggestions, and
 * answers weighted toward countries few people could place).
 *
 * <p>Hard has its own daily puzzle, leaderboard and streak, and its own practice
 * rounds. The difference on the server is only which answers are drawn and where the
 * results are counted; the rules of a guess are the same.
 */
public enum Variant {
    /** The original game. Every row stored before Hard existed is this one. */
    CLASSIC,
    /** Answers weighted toward obscure countries; see AnswerPools. Countries only. */
    HARD;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The stored value as a variant. Rows written before Hard existed have no value,
     * and they were all classic.
     */
    public static Variant orClassic(Variant stored) {
        return stored == null ? CLASSIC : stored;
    }

    /** Empty for anything that is not one of the API names; blank means classic. */
    public static Optional<Variant> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(CLASSIC);
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Variant variant : values()) {
            if (variant.api().equals(wanted)) {
                return Optional.of(variant);
            }
        }
        return Optional.empty();
    }
}

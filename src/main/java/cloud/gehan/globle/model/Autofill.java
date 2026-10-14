package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * What a roll call run ("Name them all") fills in for the player when it starts, so
 * the countries they would always name need not be typed out. The server works out
 * the codes at creation and stores them first in the run's list, flagged as filled
 * in; difficulty tiers never leave the server, only which codes were filled. An
 * autofill that would fill in the run's whole region is refused: nothing would be
 * left to name.
 */
public enum Autofill {
    /** Nothing: every country is typed. */
    OFF,
    /** The run's pool countries of difficulty tier 1: 30 in the world. */
    EASY,
    /** Tiers 1 and 2: 70 in the world. */
    KNOWN,
    /**
     * The player's own sure things: the countries named in every one of their last
     * (up to) three runs that count in the same region (finished, with at least one
     * name typed). Needs at least two such runs there.
     */
    MINE;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The stored value as an autofill. Only roll call rows carry one; any other row,
     * and every row from before roll call existed, has none, which reads as OFF.
     */
    public static Autofill orOff(Autofill stored) {
        return stored == null ? OFF : stored;
    }

    /** Empty for anything that is not one of the API names; blank means off. */
    public static Optional<Autofill> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(OFF);
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Autofill autofill : values()) {
            if (autofill.api().equals(wanted)) {
                return Optional.of(autofill);
            }
        }
        return Optional.empty();
    }
}

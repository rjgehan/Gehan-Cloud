package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/** What the mystery is: a country, or a country's capital city. */
public enum Kind {
    /** Guess countries; distance is the gap between the two borders. */
    COUNTRY,
    /** Guess capitals; distance is city to city. */
    CAPITAL;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for anything that is not one of the API names; blank means country. */
    public static Optional<Kind> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.of(COUNTRY);
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Kind kind : values()) {
            if (kind.api().equals(wanted)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}

package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Where on the page a "request a name change" was sent from, so the admin knows what
 * the player was looking at: Name them all's list of countries named while playing
 * (NAMED), the end card's list of missed ones (MISSED), a study card (STUDY), or
 * anywhere else (OTHER).
 */
public enum NameRequestSource {
    NAMED, MISSED, STUDY, OTHER;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for anything that is not one of the API names (any case, surrounding spaces ignored). */
    public static Optional<NameRequestSource> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (NameRequestSource source : values()) {
            if (source.api().equals(wanted)) {
                return Optional.of(source);
            }
        }
        return Optional.empty();
    }
}

package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * The page's language when something was sent from it: English or Spanish. The server
 * itself names places in English; this only records which language a player was
 * reading, as on a "request a name change".
 */
public enum Lang {
    EN, ES;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for anything but "en" or "es" (any case, surrounding spaces ignored). */
    public static Optional<Lang> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (Lang lang : values()) {
            if (lang.api().equals(wanted)) {
                return Optional.of(lang);
            }
        }
        return Optional.empty();
    }
}

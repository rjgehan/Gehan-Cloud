package cloud.gehan.globle.model;

import java.util.Locale;
import java.util.Optional;

/**
 * Where a "request a name change" stands. A request is sent OPEN; the admin marks it
 * DONE (the data now has the name) or DISMISSED (it will not), and may open it again.
 * An OPEN request whose suggestion the data already names is closed as DONE by itself
 * (see NameRequestService).
 */
public enum NameRequestStatus {
    OPEN, DONE, DISMISSED;

    public String api() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Empty for anything that is not one of the API names (any case, surrounding spaces ignored). */
    public static Optional<NameRequestStatus> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String wanted = value.trim().toLowerCase(Locale.ROOT);
        for (NameRequestStatus status : values()) {
            if (status.api().equals(wanted)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}

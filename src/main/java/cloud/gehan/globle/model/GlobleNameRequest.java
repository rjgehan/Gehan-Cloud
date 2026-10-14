package cloud.gehan.globle.model;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A player's "request a name change": the name they think should count for a place
 * (any place in the catalogue, territories included), for the admin to add to the data
 * by hand. Nothing in the game reads it; the admin works through the open ones and
 * marks each DONE or DISMISSED.
 *
 * <p>Owned like a game, by user id and username, deliberately not a foreign key, so
 * deleting a user never fails over their requests (see {@link GlobleGame}). A deleted
 * player's requests stay: the name they suggested is still worth reading.
 *
 * <p>The table is new, so every column the app always writes can be NOT NULL in its
 * CREATE TABLE. The two that are empty while a request is open, {@code resolved_at}
 * and {@code resolved_by}, are nullable. Enums are stored by name through converters
 * ({@link GlobleColumns}), so a value added later needs no schema change.
 */
@Entity
@Table(name = "globle_name_request")
public class GlobleNameRequest {

    /** {@code resolved_by} on a request closed automatically because the data already names it so. */
    public static final String BY_DATA = "data";

    /** At most this many characters (code points) of suggestion. */
    public static final int MAX_SUGGESTION = 80;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "username", length = 64, nullable = false)
    private String username;

    /** The place the name is for: a code of countries.json, territories included. */
    @Column(name = "code", length = 8, nullable = false)
    private String code;

    /** As the player typed it, trimmed: 1 to 80 characters. Room for 80 outside the BMP. */
    @Column(name = "suggestion", length = 2 * MAX_SUGGESTION, nullable = false)
    private String suggestion;

    @Convert(converter = GlobleColumns.LangName.class)
    @Column(name = "lang", length = 8, nullable = false)
    private Lang lang;

    @Convert(converter = GlobleColumns.NameRequestSourceName.class)
    @Column(name = "source", length = 16, nullable = false)
    private NameRequestSource source;

    @Convert(converter = GlobleColumns.InstantMillis.class)
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Convert(converter = GlobleColumns.NameRequestStatusName.class)
    @Column(name = "status", length = 16, nullable = false)
    private NameRequestStatus status;

    /** When it was last marked DONE or DISMISSED; null while OPEN. */
    @Convert(converter = GlobleColumns.InstantMillis.class)
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** The admin's username, or {@link #BY_DATA}; null while OPEN. */
    @Column(name = "resolved_by", length = 64)
    private String resolvedBy;

    protected GlobleNameRequest() {
    }

    public GlobleNameRequest(Player player, String code, String suggestion, Lang lang, NameRequestSource source,
                             Instant now) {
        this.userId = player.id();
        this.username = player.username();
        this.code = code;
        this.suggestion = suggestion;
        this.lang = lang;
        this.source = source;
        this.createdAt = now;
        this.status = NameRequestStatus.OPEN;
    }

    /**
     * Marks it DONE or DISMISSED by {@code by}, or OPEN again, which clears who and when.
     * Setting the status it already has changes nothing, so a double tap keeps the first
     * time and name.
     */
    public void resolve(NameRequestStatus outcome, String by, Instant now) {
        if (outcome == status) {
            return;
        }
        this.status = outcome;
        if (outcome == NameRequestStatus.OPEN) {
            this.resolvedAt = null;
            this.resolvedBy = null;
        } else {
            this.resolvedAt = now;
            this.resolvedBy = by;
        }
    }

    public boolean isOpen() {
        return status == NameRequestStatus.OPEN;
    }

    public boolean belongsTo(Player player) {
        return userId == player.id() && player.username().equals(username);
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getCode() {
        return code;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public Lang getLang() {
        return lang;
    }

    public NameRequestSource getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public NameRequestStatus getStatus() {
        return status;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }
}

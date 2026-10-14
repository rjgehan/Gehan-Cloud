package cloud.gehan.globle.service;

import org.springframework.http.HttpStatus;

/**
 * A refusal the player's client should understand: an HTTP status plus a short code
 * the frontend switches on. The code is the whole message; nothing internal, and never
 * the answer, goes into one.
 */
public class GlobleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private GlobleException(HttpStatus status, String code) {
        super(code, null, false, false);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** The code is not a place in the catalogue. */
    public static GlobleException unknownCountry() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "unknown-country");
    }

    /** Already guessed in this game, or already named in this roll call run. */
    public static GlobleException alreadyGuessed() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "already-guessed");
    }

    /**
     * The game has been won or given up, or the roll call run is finished; another
     * device may have ended it.
     */
    public static GlobleException finished() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "finished");
    }

    /** A capitals game was sent a place that has no capital to measure from. */
    public static GlobleException noCapital() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "no-capital");
    }

    /**
     * "Name them all" was sent a territory (Greenland, Puerto Rico...): a real place,
     * but never one of the countries to name.
     */
    public static GlobleException notAnAnswer() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "not-an-answer");
    }

    /** "Name them all" was sent an answer country that is not in the run's region. */
    public static GlobleException outsideRegion() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "outside-region");
    }

    public static GlobleException badRequest() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "bad-request");
    }

    /**
     * A player sent a "request a name change" while already having as many open as one
     * player may (NameRequestService.MAX_OPEN_PER_PLAYER). Nothing was stored.
     */
    public static GlobleException tooMany() {
        return new GlobleException(HttpStatus.BAD_REQUEST, "too-many");
    }

    /**
     * Also the answer for a game that exists but is someone else's, and for the admin's
     * name-request routes asked by anyone else: no hint either way.
     */
    public static GlobleException notFound() {
        return new GlobleException(HttpStatus.NOT_FOUND, "not-found");
    }

    /**
     * A daily move sent for a puzzle date that is no longer today: the page was left open
     * across midnight. Nothing was stored; the page should load the new day's puzzle.
     */
    public static GlobleException newDay() {
        return new GlobleException(HttpStatus.CONFLICT, "new-day");
    }

    public static GlobleException signedOut() {
        return new GlobleException(HttpStatus.UNAUTHORIZED, "signed-out");
    }
}

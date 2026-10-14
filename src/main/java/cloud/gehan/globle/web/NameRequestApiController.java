package cloud.gehan.globle.web;

import cloud.gehan.globle.model.NameRequestStatus;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.service.GlobleException;
import cloud.gehan.globle.service.NameRequestService;
import cloud.gehan.globle.service.Players;
import cloud.gehan.globle.view.NameRequestView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * "Request a name change", beside the game's other controllers under {@code /globle/api},
 * so the same security chain covers it: signed in, CSRF on every POST. Errors are
 * {@code {"error": "<code>"}} through {@link GlobleApiErrors}.
 *
 * <p>Any player may send a request. Reading them and resolving them is the admin's
 * alone (the {@code ROLE_ADMIN} authority that guards {@code /users}); anyone else gets
 * {@code 404 not-found} from those two routes, whatever they send, so nothing in the
 * answer says the routes exist. That is why the resolve route takes its id and body as
 * text and reads them only once the caller is known to be the admin: typed arguments
 * would be parsed first, and a malformed one would answer {@code 400} to anybody.
 */
@RestController
@RequestMapping("/globle/api")
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NameRequestApiController {

    private final NameRequestService nameRequests;
    private final Players players;
    private final ObjectMapper json;

    public NameRequestApiController(NameRequestService nameRequests, Players players, ObjectMapper json) {
        this.nameRequests = nameRequests;
        this.players = players;
        this.json = json;
    }

    /**
     * Sends a request: {@code 201} with it, or {@code 200} with the same one already
     * open (same place, same suggestion as the matcher reads it).
     */
    @PostMapping("/name-requests")
    public ResponseEntity<NameRequestView> send(Authentication authentication, @RequestBody SendRequest body) {
        NameRequestService.Sent sent = nameRequests.send(players.require(authentication),
                body.code(), body.suggestion(), body.lang(), body.source());
        return ResponseEntity.status(sent.created() ? HttpStatus.CREATED : HttpStatus.OK).body(sent.request());
    }

    /** The admin's list, newest first: {@code status=open} (the default) or {@code all}. */
    @GetMapping("/name-requests")
    public List<NameRequestView> list(Authentication authentication,
                                      @RequestParam(name = "status", required = false) String status) {
        players.requireAdmin(authentication);
        return nameRequests.list(all(status));
    }

    /** The admin marks one {@code {"status": "done" | "dismissed" | "open"}}; the request as it now is. */
    @PostMapping("/name-requests/{id}/resolve")
    public NameRequestView resolve(Authentication authentication, @PathVariable("id") String id,
                                   @RequestBody(required = false) String body) {
        Player admin = players.requireAdmin(authentication);
        return nameRequests.resolve(admin, id(id), outcome(body));
    }

    private static boolean all(String status) {
        String wanted = status == null || status.isBlank() ? "open" : status.trim().toLowerCase(Locale.ROOT);
        return switch (wanted) {
            case "open" -> false;
            case "all" -> true;
            default -> throw GlobleException.badRequest();
        };
    }

    private static long id(String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw GlobleException.badRequest();
        }
    }

    private NameRequestStatus outcome(String body) {
        if (body == null || body.isBlank()) {
            throw GlobleException.badRequest();
        }
        JsonNode status;
        try {
            status = json.readTree(body).path("status");
        } catch (JsonProcessingException e) {
            throw GlobleException.badRequest();
        }
        if (!status.isTextual()) {
            throw GlobleException.badRequest();
        }
        return NameRequestStatus.parse(status.asText()).orElseThrow(GlobleException::badRequest);
    }

    /**
     * {@code {"code": "GBR", "suggestion": "Great Britain", "lang": "en", "source": "missed"}}.
     * All four are needed: lang is "en" or "es", source "named", "missed", "study" or "other".
     */
    public record SendRequest(String code, String suggestion, String lang, String source) {
    }
}

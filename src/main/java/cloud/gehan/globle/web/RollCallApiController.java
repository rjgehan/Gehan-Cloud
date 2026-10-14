package cloud.gehan.globle.web;

import cloud.gehan.globle.model.Autofill;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.service.GlobleException;
import cloud.gehan.globle.service.Players;
import cloud.gehan.globle.service.RollCallService;
import cloud.gehan.globle.service.RollCallStudyService;
import cloud.gehan.globle.view.RollCallOptions;
import cloud.gehan.globle.view.RollCallState;
import cloud.gehan.globle.view.RollCallStudy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The API of "Name them all" (roll call), beside {@link GlobleApiController} under the
 * same {@code /globle/api} prefix, so the same security chain covers it: signed in,
 * CSRF on every POST. Errors are {@code {"error": "<code>"}} through
 * {@link GlobleApiErrors}, which is scoped to both controllers.
 *
 * <p>{@code region} is "world", "africa", "americas", "asia", "europe", "oceania" or
 * "mainland", and means the world when left out. {@code autofill} is "off", "easy",
 * "known" or "mine", and means off when left out.
 */
@RestController
@RequestMapping("/globle/api")
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RollCallApiController {

    private final RollCallService rollCall;
    private final RollCallStudyService studies;
    private final Players players;

    public RollCallApiController(RollCallService rollCall, RollCallStudyService studies, Players players) {
        this.rollCall = rollCall;
        this.studies = studies;
        this.players = players;
    }

    /** What each autofill would fill in here, and the player's record in this region. */
    @GetMapping("/rollcall/options")
    public RollCallOptions options(Authentication authentication,
                                   @RequestParam(name = "region", required = false) String region) {
        return rollCall.options(players.require(authentication), region(region));
    }

    /** The run in progress in the region, or 204 when there is none, so the client shows the setup card. */
    @GetMapping("/rollcall/current")
    public ResponseEntity<RollCallState> current(Authentication authentication,
                                                 @RequestParam(name = "region", required = false) String region) {
        return rollCall.current(players.require(authentication), region(region))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * A new run. The run in progress in the same region, if any, is finished or dropped.
     * "mine" before two runs that count, or any autofill that would fill in the whole
     * region, is a bad request.
     */
    @PostMapping("/rollcall/new")
    public RollCallState start(Authentication authentication, @RequestBody(required = false) NewRunRequest body) {
        NewRunRequest request = body == null ? new NewRunRequest(null, null) : body;
        return rollCall.start(players.require(authentication), region(request.region()),
                autofill(request.autofill()));
    }

    @PostMapping("/rollcall/{id}/name")
    public RollCallState name(Authentication authentication, @PathVariable("id") long id,
                              @RequestBody NameRequest body) {
        return rollCall.name(players.require(authentication), id, body.code());
    }

    /**
     * "Study what you missed": the player's newest finished run in the region, its missed
     * countries most worth learning first, or 204 when they have finished none there.
     * Read-only.
     */
    @GetMapping("/rollcall/study")
    public ResponseEntity<RollCallStudy> study(Authentication authentication,
                                               @RequestParam(name = "region", required = false) String region) {
        return studies.study(players.require(authentication), region(region))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** "I'm done". No body. */
    @PostMapping("/rollcall/{id}/finish")
    public RollCallState finish(Authentication authentication, @PathVariable("id") long id) {
        return rollCall.finish(players.require(authentication), id);
    }

    private static Region region(String value) {
        return Region.parse(value).orElseThrow(GlobleException::badRequest);
    }

    private static Autofill autofill(String value) {
        return Autofill.parse(value).orElseThrow(GlobleException::badRequest);
    }

    /** {@code {"region": "europe", "autofill": "easy"}}; both optional (world, off). */
    public record NewRunRequest(String region, String autofill) {
    }

    /** {@code {"code": "FRA"}}: the country named, by its code. */
    public record NameRequest(String code) {
    }
}

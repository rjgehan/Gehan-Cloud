package cloud.gehan.globle.web;

import cloud.gehan.globle.service.DailyPuzzles;
import cloud.gehan.globle.service.GlobleClock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;

/**
 * Serves the game page. Sign-in is the main security chain's job, the same as for the
 * launcher: an anonymous visitor is redirected to the login page before reaching here.
 *
 * <p>The page itself holds no game state; the script loads it from the API. The model
 * carries just enough to draw the header before that arrives.
 */
@Controller
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GloblePageController {

    private final GlobleClock clock;
    private final DailyPuzzles puzzles;

    public GloblePageController(GlobleClock clock, DailyPuzzles puzzles) {
        this.clock = clock;
        this.puzzles = puzzles;
    }

    @GetMapping({"/globle", "/globle/"})
    public String page(Model model, Authentication authentication) {
        LocalDate today = clock.today();
        model.addAttribute("puzzleNumber", puzzles.puzzleNumber(today));
        model.addAttribute("puzzleDate", today.toString());
        model.addAttribute("username", authentication == null ? null : authentication.getName());
        return "globle/index";
    }
}

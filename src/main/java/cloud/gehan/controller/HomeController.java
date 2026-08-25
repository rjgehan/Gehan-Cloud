package cloud.gehan.controller;

import cloud.gehan.config.PortalProperties;
import cloud.gehan.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PortalProperties portal;

    public HomeController(PortalProperties portal) {
        this.portal = portal;
    }

    /** Serves the portal with the tiles this viewer is allowed to see. */
    @GetMapping("/")
    public String index(Model model, Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + UserService.ROLE_ADMIN)::equals);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("apps", portal.visibleTo(admin));
        return "index";
    }
}

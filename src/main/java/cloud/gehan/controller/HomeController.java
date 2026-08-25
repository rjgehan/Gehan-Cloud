package cloud.gehan.controller;

import cloud.gehan.config.PortalProperties;
import cloud.gehan.security.LocalNetwork;
import cloud.gehan.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final PortalProperties portal;
    private final LocalNetwork localNetwork;

    public HomeController(PortalProperties portal, LocalNetwork localNetwork) {
        this.portal = portal;
        this.localNetwork = localNetwork;
    }

    /** Serves the portal with the tiles this viewer is allowed to see. */
    @GetMapping("/")
    public String index(Model model, Authentication authentication, HttpServletRequest request) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + UserService.ROLE_ADMIN)::equals);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("apps", portal.visibleTo(admin));
        // getRemoteAddr carries the real client address: ForwardedHeaderFilter applies
        // X-Forwarded-For to the request when running behind the proxy.
        model.addAttribute("onLan", localNetwork.includes(request.getRemoteAddr()));
        return "index";
    }
}

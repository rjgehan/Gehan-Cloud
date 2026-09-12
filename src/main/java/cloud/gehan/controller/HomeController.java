package cloud.gehan.controller;

import cloud.gehan.config.PortalProperties;
import cloud.gehan.security.ClientAddress;
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
    private final ClientAddress clientAddress;

    public HomeController(PortalProperties portal, LocalNetwork localNetwork,
                          ClientAddress clientAddress) {
        this.portal = portal;
        this.localNetwork = localNetwork;
        this.clientAddress = clientAddress;
    }

    /** Serves the portal with the tiles this viewer is allowed to see. */
    @GetMapping("/")
    public String index(Model model, Authentication authentication, HttpServletRequest request) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("ROLE_" + UserService.ROLE_ADMIN)::equals);
        model.addAttribute("isAdmin", admin);
        model.addAttribute("apps", portal.visibleTo(admin));
        // ClientAddress resolves which of the forwarding headers to believe; behind
        // Cloudflare that is CF-Connecting-IP rather than X-Forwarded-For.
        String address = clientAddress.of(request);
        model.addAttribute("onLan", localNetwork.includes(address));
        // Which trusted network, where the groups are named. A tile with per-network
        // urls uses this to pick one; null just means every tile falls back to its own.
        model.addAttribute("network", localNetwork.nameFor(address));
        return "index";
    }
}

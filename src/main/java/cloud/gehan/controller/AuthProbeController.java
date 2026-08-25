package cloud.gehan.controller;

import cloud.gehan.config.PortalProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Session probe for a reverse-proxy forward-auth middleware (Traefik ForwardAuth,
 * Nginx auth_request). The proxy calls this before serving another service on the
 * domain; 2xx lets the request through, anything else is returned to the browser.
 *
 * <p>Answering an unauthenticated browser with 302 rather than 401 is what makes
 * single sign-on work: the visitor lands on the portal login and comes back to
 * where they were going.
 */
@RestController
public class AuthProbeController {

    private final PortalProperties portal;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    public AuthProbeController(PortalProperties portal) {
        this.portal = portal;
    }

    @GetMapping("/__auth")
    public ResponseEntity<Void> probe(Authentication authentication, HttpServletRequest request) {
        if (isSignedIn(authentication)) {
            // Traefik copies this to the backend only if the middleware lists it under
            // authResponseHeaders, so it costs nothing when unused.
            return ResponseEntity.ok()
                    .header("X-Auth-User", authentication.getName())
                    .build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, loginUrlReturningTo(originalUrl(request)))
                .build();
    }

    /**
     * An anonymous request still carries an Authentication whose isAuthenticated() is
     * true, so checking that alone would approve every visitor. The trust resolver is
     * what separates a real session from an anonymous one.
     */
    private boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !trustResolver.isAnonymous(authentication);
    }

    /**
     * Rebuilds the URL the visitor was trying to reach.
     *
     * <p>With server.forward-headers-strategy=framework, Spring's ForwardedHeaderFilter
     * applies X-Forwarded-Proto and X-Forwarded-Host to the request and then strips
     * them, so reading those headers here returns nothing and getScheme()/getServerName()
     * carry the values instead. Both are checked so this works either way.
     *
     * <p>X-Forwarded-Uri is Traefik's own header and is not one the filter consumes; it
     * is the only place the original path survives, since the request path here is
     * /__auth.
     */
    private String originalUrl(HttpServletRequest request) {
        String host = header(request, "X-Forwarded-Host");
        if (host == null) {
            host = request.getServerName();
        }
        if (host == null || host.isBlank()) {
            return "";
        }

        String proto = header(request, "X-Forwarded-Proto");
        if (proto == null) {
            proto = request.getScheme();
        }

        String uri = header(request, "X-Forwarded-Uri");
        return (proto == null || proto.isBlank() ? "https" : proto)
                + "://" + host
                + (uri == null ? "/" : uri);
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String loginUrlReturningTo(String target) {
        String domain = portal.getBaseDomain();
        // Relative only makes sense when there is no other host in play; in a real
        // deployment the browser is on a different subdomain and needs an absolute URL.
        String login = (domain == null || domain.isBlank())
                ? "/login"
                : "https://" + domain + "/login";

        // Anything that fails validation simply loses the return trip; the visitor
        // still reaches the login page.
        String safe = cloud.gehan.security.RedirectTargets.resolve(target, domain, "");
        return safe.isEmpty()
                ? login
                : login + "?continue=" + URLEncoder.encode(safe, StandardCharsets.UTF_8);
    }
}

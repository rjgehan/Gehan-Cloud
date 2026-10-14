package cloud.gehan.globle.service;

import cloud.gehan.globle.model.Player;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Turns the signed-in session into a {@link Player}.
 *
 * <p>This is the one place Globle reaches into the rest of the app, and it only reads:
 * the users table is how it knows who exists. A session whose account has since been
 * deleted is treated as signed out rather than allowed to keep playing as nobody.
 *
 * <p>Who is an admin is the session's own answer, the authority {@code ROLE_ADMIN}:
 * the same role, read the same way, that guards the user console at {@code /users}.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Players {

    /** The authority SecurityConfig's {@code hasRole("ADMIN")} looks for. */
    static final String ADMIN_AUTHORITY = "ROLE_" + UserService.ROLE_ADMIN;

    private final UserRepository users;
    private final AuthenticationTrustResolver trust = new AuthenticationTrustResolverImpl();

    public Players(UserRepository users) {
        this.users = users;
    }

    public Player require(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || trust.isAnonymous(authentication)) {
            throw GlobleException.signedOut();
        }
        return users.findByUsername(authentication.getName())
                .map(user -> new Player(user.getId(), user.getUsername()))
                .orElseThrow(GlobleException::signedOut);
    }

    /** Whether the signed-in session has the admin role. False for no session. */
    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITY::equals);
    }

    /**
     * The signed-in admin. Anyone else who is signed in gets {@code 404 not-found}, the
     * answer for a route that does not exist, so an admin-only route gives no hint that
     * it is there; no session at all is still {@code 401 signed-out}.
     */
    public Player requireAdmin(Authentication authentication) {
        Player player = require(authentication);
        if (!isAdmin(authentication)) {
            throw GlobleException.notFound();
        }
        return player;
    }
}

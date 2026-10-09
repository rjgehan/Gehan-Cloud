package cloud.gehan.globle.service;

import cloud.gehan.globle.model.Player;
import cloud.gehan.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Turns the signed-in session into a {@link Player}.
 *
 * <p>This is the one place Globle reaches into the rest of the app, and it only reads:
 * the users table is how it knows who exists. A session whose account has since been
 * deleted is treated as signed out rather than allowed to keep playing as nobody.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class Players {

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
}

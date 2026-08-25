package com.example.demo.security;

import com.example.demo.model.User;
import com.example.demo.service.UserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;

/**
 * Handles accounts that an admin created (or reset) but that nobody has logged into yet.
 * Those rows carry no password, so the standard DAO provider cannot authenticate them.
 * Here the first password presented is saved and the login succeeds.
 *
 * <p>Returning {@code null} means "not my case" -- Spring Security then falls through to
 * the normal password-checking provider.
 *
 * <p>Deliberately not a {@code @Component}: a lone AuthenticationProvider bean makes
 * Spring Security build a global AuthenticationManager out of just that provider,
 * which would shadow the chain assembled in SecurityConfig. It is constructed there.
 */
public class FirstLoginAuthenticationProvider implements AuthenticationProvider {

    private final UserService userService;

    public FirstLoginAuthenticationProvider(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        if (username == null || credentials == null) {
            return null;
        }

        Optional<User> claimed = userService.claim(username, credentials.toString());
        if (claimed.isEmpty()) {
            return null;
        }

        User user = claimed.get();
        return UsernamePasswordAuthenticationToken.authenticated(
                user.getUsername(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

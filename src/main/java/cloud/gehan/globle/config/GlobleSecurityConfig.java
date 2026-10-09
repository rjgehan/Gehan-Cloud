package cloud.gehan.globle.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Security for Globle's JSON API, added beside the app's own chain rather than by
 * editing SecurityConfig, so removing the game cannot disturb the launcher's security.
 *
 * <p>It claims only {@code /globle/api/**}. The page at {@code /globle} and its static
 * files stay under the main chain: signed in, or redirected to the login page like
 * every other page.
 *
 * <p>The API differs from a page in one way that matters: a request that is not
 * signed in gets a 401 with a JSON body instead of a 302 to the login form. A script
 * that follows the redirect would be handed the login page as if it were data; a 401
 * lets the game say "your session expired" instead.
 *
 * <p>What it shares with the main chain is the session. The same session cookie and
 * HttpSession-backed SecurityContext mean a family member who signed in on the
 * launcher is signed in here; nothing about login is configured, because nobody logs
 * in through the API. CSRF stays on with the default session-backed token repository,
 * which is the same one the main chain uses, so the token Thymeleaf renders into the
 * Globle page is the one this chain checks.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleSecurityConfig {

    static final String API = "/globle/api/**";

    private static final AuthenticationTrustResolver TRUST = new AuthenticationTrustResolverImpl();

    /**
     * Ordered ahead of the main chain, which matches every request and would otherwise
     * claim these first. HIGHEST_PRECEDENCE + 10 rather than HIGHEST_PRECEDENCE leaves
     * room for anything that genuinely has to come before it.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public SecurityFilterChain globleApiFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(API)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                // CSRF left at its defaults on purpose: every POST here changes a game
                // and is made by a browser carrying the session cookie.
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, e) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "signed-out"))
                        // A missing or stale CSRF token lands here too. For a visitor
                        // with no session the token is only missing because the session
                        // is gone, so say that rather than "forbidden".
                        .accessDeniedHandler((request, response, e) -> {
                            if (isSignedIn(SecurityContextHolder.getContext().getAuthentication())) {
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "forbidden");
                            } else {
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "signed-out");
                            }
                        }))
                // Nothing to return to after a login: nobody browses to a JSON endpoint.
                .requestCache(cache -> cache.disable())
                // Logging out belongs to the main chain; this one never sees /logout.
                .logout(logout -> logout.disable())
                // Spring Security's defaults (nosniff, X-Frame-Options, HSTS, no-store
                // caching) stay. A JSON response never needs to load anything, so its
                // CSP can be the strictest there is.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'")));
        return http.build();
    }

    private static boolean isSignedIn(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !TRUST.isAnonymous(authentication);
    }

    /** Matches the body GlobleApiErrors writes, so the client has one shape to read. */
    private static void writeError(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + code + "\"}");
    }
}

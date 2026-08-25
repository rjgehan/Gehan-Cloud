package cloud.gehan.config;

import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.security.FirstLoginAuthenticationProvider;
import cloud.gehan.security.RedirectTargets;
import cloud.gehan.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.util.List;

/**
 * Session-based form login. There is no API surface: every route is either the
 * login page, a static asset, or a page that requires a signed-in session.
 */
@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;
    private final PortalProperties portal;

    public SecurityConfig(UserRepository userRepository, PortalProperties portal) {
        this.userRepository = userRepository;
        this.portal = portal;
    }

    // 1) Load users from the database
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));
            if (user.isUnclaimed()) {
                // FirstLoginAuthenticationProvider claims these before the DAO provider
                // runs. Getting here means it did not, so refuse rather than NPE on a
                // null password.
                throw new UsernameNotFoundException("Account has no password set");
            }
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .roles(user.getRole())
                    .build();
        };
    }

    // 2) Password encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 3) AuthenticationManager: try the first-login claim, then normal password checking
    @Bean
    public AuthenticationManager authenticationManager(
            UserService userService,
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider daoProvider = new DaoAuthenticationProvider(userDetailsService);
        daoProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(
                List.of(new FirstLoginAuthenticationProvider(userService), daoProvider));
    }

    // 4) Security filter chain
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, AuthenticationManager authenticationManager)
            throws Exception {
        http
                // Bind explicitly; otherwise the auto-configured global manager can win.
                .authenticationManager(authenticationManager)
                // CSRF left at defaults: every state-changing route here is a browser
                // form backed by a session cookie, which is exactly what it protects.
                // It was previously disabled for a JSON API that no longer exists.
                .authorizeHttpRequests(auth -> auth
                        // The login page, the assets it needs, and error pages
                        .requestMatchers(
                                "/login",
                                // Reached anonymously so it can answer with a redirect
                                // to the login page rather than a dead end.
                                "/__auth", "/__lan",
                                "/error", "/error/**",
                                "/indexjs.js",
                                "/favicon.ico", "/favicon-*.png",
                                "/apple-touch-icon.png", "/apple-touch-icon-*.png",
                                "/site.webmanifest", "/robots.txt",
                                "/css/**", "/js/**", "/images/**", "/fonts/**", "/webjars/**"
                        ).permitAll()
                        // User administration
                        .requestMatchers("/users/**").hasRole("ADMIN")
                        // The launcher and everything else
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login").permitAll()
                        .successHandler((req, res, authn) -> res.sendRedirect(
                                RedirectTargets.resolve(
                                        req.getParameter("continue"), portal.getBaseDomain(), "/")))
                )
                .logout(logout -> logout.permitAll())
                // Spring Security already sends nosniff, X-Frame-Options and HSTS.
                // These two it does not. script-src stays at 'self' because no page
                // has inline script; styles still need 'unsafe-inline' for the login
                // page's embedded stylesheet, which is the weaker half but not the
                // half that stops injected script running.
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(String.join("; ",
                                "default-src 'self'",
                                "script-src 'self'",
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net "
                                        + "https://cdnjs.cloudflare.com https://fonts.googleapis.com",
                                "font-src 'self' https://cdn.jsdelivr.net "
                                        + "https://cdnjs.cloudflare.com https://fonts.gstatic.com",
                                "img-src 'self' data:",
                                "connect-src 'self' https://api.open-meteo.com",
                                "form-action 'self'",
                                "frame-ancestors 'none'",
                                "base-uri 'self'",
                                "object-src 'none'")))
                        .referrerPolicy(referrer -> referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
                .requestCache(cache -> cache.disable());

        return http.build();
    }
}

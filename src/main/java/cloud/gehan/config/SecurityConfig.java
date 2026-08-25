package cloud.gehan.config;

import cloud.gehan.repository.UserRepository;
import cloud.gehan.model.User;
import cloud.gehan.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import cloud.gehan.security.FirstLoginAuthenticationProvider;
import cloud.gehan.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public SecurityConfig(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    // 1) Load users from DB
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
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(auth -> auth
            // Public: home, login, auth probe, static assets, public APIs, and error pages
            .requestMatchers(
                "/login", "/__auth", "/api/grocery/**",
                "/auth/login",               // issues API bearer tokens; must be reachable anonymously
                "/api/share",
                "/error", "/error/**",        // <-- add these
                "/style.css", "/indexjs.js",
                "/favicon.ico",
                "/apple-touch-icon.png", "/apple-touch-icon-*.png",
                "/site.webmanifest", "/robots.txt",
                "/hamilton.jpeg", "/ashburn.png", "/manasquan.jpeg", "/newyork.jpeg",
                "/css/**", "/js/**", "/images/**", "/fonts/**", "/webjars/**"
            ).permitAll()
            // User administration
            .requestMatchers("/users/**").hasRole("ADMIN")
            // Protected API
            .requestMatchers("/api/**").authenticated()
            // Everything else requires login
            .anyRequest().authenticated()
        )
        .formLogin(form -> form
            .loginPage("/login").permitAll()
            .successHandler((req, res, authn) -> {
                String cont = req.getParameter("continue");
                res.sendRedirect((cont != null && !cont.isBlank()) ? cont : "/");
            })
        )
        .logout(logout -> logout.permitAll())
        // An unauthenticated /api/** call should get a 401 it can act on; browser
        // traffic still goes to the login form. Delegating explicitly rather than
        // via defaultAuthenticationEntryPointFor, whose fallback depends on the
        // order entry points happen to be registered in.
        .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, authEx) -> {
            if (isApiRequest(req)) {
                res.sendError(HttpStatus.UNAUTHORIZED.value());
            } else {
                LOGIN_ENTRY_POINT.commence(req, res, authEx);
            }
        }))
        .requestCache(cache -> cache.disable())
        .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

    return http.build();
}


    private static final LoginUrlAuthenticationEntryPoint LOGIN_ENTRY_POINT =
            new LoginUrlAuthenticationEntryPoint("/login");

    /** True for the JSON API surface. Mirrors ApiKeyFilter, which also matches on the URI. */
    static boolean isApiRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/api/");
    }

    // 5) JWT filter (protects /api/**)
    public static class JwtFilter extends OncePerRequestFilter {
        private final JwtUtil jwtUtil;

        public JwtFilter(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
            return !isApiRequest(request);
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String username = jwtUtil.validateToken(authHeader.substring(7));
                if (username != null) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()));
                }
            }
            chain.doFilter(request, response);
        }
    }

    // 6) Auth probe for Nginx auth_request
    @RestController
    static class AuthProbeController {
        @GetMapping("/__auth")
        public ResponseEntity<Void> authProbe(Authentication auth) {
            return (auth != null && auth.isAuthenticated())
                ? ResponseEntity.ok().build()
                : ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}

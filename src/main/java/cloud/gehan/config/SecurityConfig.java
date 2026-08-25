package cloud.gehan.config;

import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.security.FirstLoginAuthenticationProvider;
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

import java.util.List;

/**
 * Session-based form login. There is no API surface: every route is either the
 * login page, a static asset, or a page that requires a signed-in session.
 */
@Configuration
public class SecurityConfig {

    private final UserRepository userRepository;

    public SecurityConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
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
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // The login page, the assets it needs, and error pages
                        .requestMatchers(
                                "/login",
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
                        .successHandler((req, res, authn) -> {
                            String cont = req.getParameter("continue");
                            res.sendRedirect((cont != null && !cont.isBlank()) ? cont : "/");
                        })
                )
                .logout(logout -> logout.permitAll())
                .requestCache(cache -> cache.disable());

        return http.build();
    }
}

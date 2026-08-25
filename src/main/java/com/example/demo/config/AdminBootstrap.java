package com.example.demo.config;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * On an empty database, creates the first admin so there is a way in. It is created
 * unclaimed, so whoever logs in as it first sets the password -- log in immediately
 * after a fresh deploy.
 */
@Configuration
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    @Bean
    CommandLineRunner createFirstAdmin(UserRepository users,
                                       UserService userService,
                                       @Value("${app.bootstrap-admin:admin}") String username) {
        return args -> {
            if (users.count() > 0) {
                return;
            }
            userService.create(username, UserService.ROLE_ADMIN);
            log.warn("No users found. Created admin account \"{}\" with no password - "
                    + "log in now to set one before anyone else does.", username);
        };
    }
}

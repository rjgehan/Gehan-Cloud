
package com.example.demo.config;

import com.example.demo.repository.UserRepository;
import com.example.demo.model.User;



import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;


@Configuration
public class DbTestConfig {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();


    @Bean
    CommandLineRunner testDatabaseWrite(UserRepository userRepo) {
        return args -> {

            // delete user
            // userRepo.findByUsername("admin").ifPresent(user -> {
            //         userRepo.delete(user);
            //         System.out.println("DEBUG: Deleted old admin user");
            //     });     
            
            
            // User testUser = new User();


            // // save new user
            // String test = encoder.encode("Marianne");
            // testUser.setUsername("family");
            // testUser.setPassword(test);
            // testUser.setRole("USER");
            // userRepo.save(testUser);
            // System.out.println("DEBUG: Saved test user with ID " + testUser.getId());

            // System.out.println("DEBUG: Total users = " + userRepo.count());

            // Optionally delete it after test
            // userRepo.delete(testUser);
            // System.out.println("DEBUG: Deleted test user");
        };
    }
}

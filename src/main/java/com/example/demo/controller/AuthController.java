package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
    }

    // @PostMapping("/register")
    // public String register(@RequestBody Map<String, String> user) {
    //     User u = new User();
    //     u.setUsername(user.get("username"));
    //     u.setPassword(encoder.encode(user.get("password")));
    //     u.setRole("USER");
    //     userRepo.save(u);
    //     return "User registered";
    // }

    @PostMapping("/login")
    public String login(@RequestBody Map<String, String> user) {
        System.out.println("DEBUG: Username = " + user.get("username"));
        System.out.println("DEBUG: Password = " + user.get("password"));
        User u = userRepo.findByUsername(user.get("username"))
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (encoder.matches(user.get("password"), u.getPassword())) {
            return jwtUtil.generateToken(u.getUsername());
        }
        throw new RuntimeException("Invalid password");
    }
}

package cloud.gehan.controller;

import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/** Exchanges a username/password pair for a bearer token used by {@code /api/**}. */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepo;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder encoder;

    public AuthController(UserRepository userRepo, JwtUtil jwtUtil, PasswordEncoder encoder) {
        this.userRepo = userRepo;
        this.jwtUtil = jwtUtil;
        this.encoder = encoder;
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().build();
        }

        // One generic 401 for every failure mode, so the response cannot be used to
        // probe which usernames exist.
        Optional<User> found = userRepo.findByUsername(username);
        if (found.isEmpty() || found.get().isUnclaimed()
                || !encoder.matches(password, found.get().getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(jwtUtil.generateToken(found.get().getUsername()));
    }
}

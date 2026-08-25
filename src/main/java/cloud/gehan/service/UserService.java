package cloud.gehan.service;

import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * All user administration goes through here so the guards that keep an admin from
 * locking themselves out live in exactly one place.
 */
@Service
public class UserService {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    private static final Pattern VALID_USERNAME = Pattern.compile("^[A-Za-z0-9._-]{2,32}$");

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /** Thrown when an admin action is rejected; the message is shown back on the users page. */
    public static class RejectedException extends RuntimeException {
        public RejectedException(String message) {
            super(message);
        }
    }

    /** One row of the users table, without the password hash. */
    public record UserRow(Long id, String username, String role, boolean unclaimed) {
        public boolean isAdmin() {
            return ROLE_ADMIN.equals(role);
        }
    }

    public List<UserRow> list() {
        return users.findAllByOrderByUsernameAsc().stream()
                .map(u -> new UserRow(u.getId(), u.getUsername(), u.getRole(), u.isUnclaimed()))
                .toList();
    }

    /**
     * Creates an account with no password. The first login claims it.
     */
    @Transactional
    public User create(String username, String role) {
        String name = username == null ? "" : username.trim();
        if (!VALID_USERNAME.matcher(name).matches()) {
            throw new RejectedException(
                    "Username must be 2-32 characters, letters/numbers/dot/dash/underscore only.");
        }
        if (users.existsByUsernameIgnoreCase(name)) {
            throw new RejectedException("A user named \"" + name + "\" already exists.");
        }

        User user = new User();
        user.setUsername(name);
        user.setPassword(null);
        user.setRole(normalizeRole(role));
        return users.save(user);
    }

    @Transactional
    public String delete(long id, String actingUsername) {
        User target = require(id);
        if (target.getUsername().equalsIgnoreCase(actingUsername)) {
            throw new RejectedException("You cannot delete your own account.");
        }
        if (isLastAdmin(target)) {
            throw new RejectedException("Cannot delete the last remaining admin.");
        }
        users.delete(target);
        return target.getUsername();
    }

    /**
     * Clears the password, putting the account back to unclaimed. The next login sets
     * a new one -- the same flow as a freshly created user.
     */
    @Transactional
    public String resetPassword(long id) {
        User target = require(id);
        target.setPassword(null);
        users.save(target);
        return target.getUsername();
    }

    @Transactional
    public String changeRole(long id, String role, String actingUsername) {
        User target = require(id);
        String next = normalizeRole(role);
        if (next.equals(target.getRole())) {
            return target.getUsername();
        }
        if (ROLE_USER.equals(next)) {
            if (target.getUsername().equalsIgnoreCase(actingUsername)) {
                throw new RejectedException("You cannot remove your own admin role.");
            }
            if (isLastAdmin(target)) {
                throw new RejectedException("Cannot demote the last remaining admin.");
            }
        }
        target.setRole(next);
        users.save(target);
        return target.getUsername();
    }

    /**
     * Saves {@code rawPassword} as the account's password if it has never been claimed.
     * Returns empty when the account is missing or already has a password, which tells
     * the caller to fall through to normal password checking.
     */
    @Transactional
    public Optional<User> claim(String username, String rawPassword) {
        if (username == null || rawPassword == null || rawPassword.isBlank()) {
            return Optional.empty();
        }
        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty() || !found.get().isUnclaimed()) {
            return Optional.empty();
        }
        User user = found.get();
        user.setPassword(encoder.encode(rawPassword));
        return Optional.of(users.save(user));
    }

    private boolean isLastAdmin(User target) {
        return ROLE_ADMIN.equals(target.getRole()) && users.countByRole(ROLE_ADMIN) <= 1;
    }

    private User require(long id) {
        return users.findById(id)
                .orElseThrow(() -> new RejectedException("That user no longer exists."));
    }

    private static String normalizeRole(String role) {
        return ROLE_ADMIN.equalsIgnoreCase(role) ? ROLE_ADMIN : ROLE_USER;
    }
}

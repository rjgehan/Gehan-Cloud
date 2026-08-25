package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void reset() {
        users.deleteAll();
    }

    @Test
    void newUserStartsWithoutAPassword() {
        User created = userService.create("Jen", UserService.ROLE_USER);

        assertThat(created.isUnclaimed()).isTrue();
        assertThat(created.getPassword()).isNull();
        assertThat(created.getRole()).isEqualTo(UserService.ROLE_USER);
    }

    @Test
    void firstLoginPasswordIsSaved() {
        userService.create("Jen", UserService.ROLE_USER);

        Optional<User> claimed = userService.claim("Jen", "sunflower");

        assertThat(claimed).isPresent();
        assertThat(encoder.matches("sunflower", claimed.get().getPassword())).isTrue();
        assertThat(users.findByUsername("Jen").orElseThrow().isUnclaimed()).isFalse();
    }

    @Test
    void secondLoginDoesNotOverwriteTheSavedPassword() {
        userService.create("Jen", UserService.ROLE_USER);
        userService.claim("Jen", "sunflower");

        // A later attempt must not re-claim the account, whatever password is offered.
        assertThat(userService.claim("Jen", "someoneElsesGuess")).isEmpty();
        assertThat(encoder.matches("sunflower",
                users.findByUsername("Jen").orElseThrow().getPassword())).isTrue();
    }

    @Test
    void claimingIsIgnoredForUnknownUsersAndBlankPasswords() {
        userService.create("Jen", UserService.ROLE_USER);

        assertThat(userService.claim("NoSuchUser", "pw")).isEmpty();
        assertThat(userService.claim("Jen", "   ")).isEmpty();
        assertThat(userService.claim("Jen", null)).isEmpty();
    }

    @Test
    void resetPutsTheAccountBackToUnclaimed() {
        userService.create("Jen", UserService.ROLE_USER);
        userService.claim("Jen", "sunflower");

        userService.resetPassword(users.findByUsername("Jen").orElseThrow().getId());

        assertThat(users.findByUsername("Jen").orElseThrow().isUnclaimed()).isTrue();
        // and the next login sets a fresh one
        assertThat(userService.claim("Jen", "brandNew")).isPresent();
    }

    @Test
    void duplicateUsernamesAreRejectedRegardlessOfCase() {
        userService.create("Jen", UserService.ROLE_USER);

        assertThatThrownBy(() -> userService.create("jen", UserService.ROLE_USER))
                .isInstanceOf(UserService.RejectedException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void malformedUsernamesAreRejected() {
        assertThatThrownBy(() -> userService.create("a", UserService.ROLE_USER))
                .isInstanceOf(UserService.RejectedException.class);
        assertThatThrownBy(() -> userService.create("has space", UserService.ROLE_USER))
                .isInstanceOf(UserService.RejectedException.class);
        assertThatThrownBy(() -> userService.create("  ", UserService.ROLE_USER))
                .isInstanceOf(UserService.RejectedException.class);
    }

    @Test
    void adminCannotDeleteThemselves() {
        User admin = userService.create("admin", UserService.ROLE_ADMIN);

        assertThatThrownBy(() -> userService.delete(admin.getId(), "admin"))
                .isInstanceOf(UserService.RejectedException.class)
                .hasMessageContaining("your own account");
    }

    @Test
    void lastAdminCannotBeDeletedOrDemoted() {
        User admin = userService.create("admin", UserService.ROLE_ADMIN);
        userService.create("Jen", UserService.ROLE_USER);

        assertThatThrownBy(() -> userService.delete(admin.getId(), "Jen"))
                .isInstanceOf(UserService.RejectedException.class)
                .hasMessageContaining("last remaining admin");
        assertThatThrownBy(() -> userService.changeRole(admin.getId(), UserService.ROLE_USER, "Jen"))
                .isInstanceOf(UserService.RejectedException.class)
                .hasMessageContaining("last remaining admin");
    }

    @Test
    void secondAdminUnblocksDeletingTheFirst() {
        User first = userService.create("admin", UserService.ROLE_ADMIN);
        User second = userService.create("Ryan", UserService.ROLE_ADMIN);

        userService.delete(first.getId(), second.getUsername());

        assertThat(users.findByUsername("admin")).isEmpty();
    }

    @Test
    void rolesCanBePromotedAndDemoted() {
        userService.create("admin", UserService.ROLE_ADMIN);
        User jen = userService.create("Jen", UserService.ROLE_USER);

        userService.changeRole(jen.getId(), UserService.ROLE_ADMIN, "admin");
        assertThat(users.findByUsername("Jen").orElseThrow().getRole())
                .isEqualTo(UserService.ROLE_ADMIN);

        userService.changeRole(jen.getId(), UserService.ROLE_USER, "admin");
        assertThat(users.findByUsername("Jen").orElseThrow().getRole())
                .isEqualTo(UserService.ROLE_USER);
    }
}

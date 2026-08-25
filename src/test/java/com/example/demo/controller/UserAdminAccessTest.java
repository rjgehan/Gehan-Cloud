package com.example.demo.controller;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserAdminAccessTest {

    @Autowired
    private MockMvc mvc;

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
    void anonymousVisitorIsSentToLogin() throws Exception {
        mvc.perform(get("/users"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mvc.perform(get("/users").with(user("Jen").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonAdminCannotCreateUsers() throws Exception {
        mvc.perform(post("/users").param("username", "Sneaky").param("role", "ADMIN")
                        .with(user("Jen").roles("USER")))
                .andExpect(status().isForbidden());

        assertThat(users.existsByUsernameIgnoreCase("Sneaky")).isFalse();
    }

    @Test
    void adminSeesTheUsersPage() throws Exception {
        mvc.perform(get("/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminCreatesAUserThroughTheForm() throws Exception {
        mvc.perform(post("/users").param("username", "Colleen").param("role", "USER")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());

        assertThat(users.findByUsername("Colleen").orElseThrow().isUnclaimed()).isTrue();
    }

    @Test
    void createdUserClaimsTheirAccountOnFirstLogin() throws Exception {
        userService.create("Colleen", UserService.ROLE_USER);

        // First login: any password is accepted and saved.
        mvc.perform(formLogin().user("Colleen").password("firstChoice"))
                .andExpect(authenticated().withUsername("Colleen"));
        assertThat(encoder.matches("firstChoice",
                users.findByUsername("Colleen").orElseThrow().getPassword())).isTrue();

        // Now it behaves like a normal account.
        mvc.perform(formLogin().user("Colleen").password("firstChoice"))
                .andExpect(authenticated());
        mvc.perform(formLogin().user("Colleen").password("wrongPassword"))
                .andExpect(unauthenticated());
    }

    @Test
    void resetLetsTheUserPickANewPasswordAndInvalidatesTheOld() throws Exception {
        userService.create("Colleen", UserService.ROLE_USER);
        mvc.perform(formLogin().user("Colleen").password("firstChoice"))
                .andExpect(authenticated());

        long id = users.findByUsername("Colleen").orElseThrow().getId();
        mvc.perform(post("/users/" + id + "/reset").with(user("admin").roles("ADMIN")))
                .andExpect(status().is3xxRedirection());

        mvc.perform(formLogin().user("Colleen").password("secondChoice"))
                .andExpect(authenticated());
        assertThat(encoder.matches("secondChoice",
                users.findByUsername("Colleen").orElseThrow().getPassword())).isTrue();
        mvc.perform(formLogin().user("Colleen").password("firstChoice"))
                .andExpect(unauthenticated());
    }

    @Test
    void loginFailsForUsernamesThatDoNotExist() throws Exception {
        mvc.perform(formLogin().user("Ghost").password("anything"))
                .andExpect(unauthenticated());
        assertThat(users.existsByUsernameIgnoreCase("Ghost")).isFalse();
    }

    @Test
    void adminCreatedThroughTheFormCanReachTheUsersPage() throws Exception {
        userService.create("Ryan", UserService.ROLE_ADMIN);
        mvc.perform(formLogin().user("Ryan").password("chosenAtFirstLogin"))
                .andExpect(authenticated().withRoles("ADMIN"));
    }
}

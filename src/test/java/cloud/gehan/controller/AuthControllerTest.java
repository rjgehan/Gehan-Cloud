package cloud.gehan.controller;

import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers the bearer-token path: POST /auth/login, then Authorization on /api/**. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder encoder;

    @BeforeEach
    void reset() {
        users.deleteAll();
    }

    private void claimedUser(String username, String password) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        u.setRole(UserService.ROLE_USER);
        users.save(u);
    }

    private String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    void validCredentialsReturnAToken() throws Exception {
        claimedUser("alice", "correct-horse");

        String token = mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice", "correct-horse")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(token).isNotBlank();

        mvc.perform(get("/api/hello").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        claimedUser("alice", "correct-horse");

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("alice", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    /** Same 401 as a wrong password, so the response cannot enumerate usernames. */
    @Test
    void unknownUserIsRejectedIdentically() throws Exception {
        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("nobody", "whatever")))
                .andExpect(status().isUnauthorized());
    }

    /** An account that has never been claimed has no password to match against. */
    @Test
    void unclaimedAccountCannotGetAToken() throws Exception {
        userService.create("bob", UserService.ROLE_USER);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("bob", "anything")))
                .andExpect(status().isUnauthorized());
    }

    /** API clients get a 401 they can act on, not a redirect to the HTML login form. */
    @Test
    void garbageTokenDoesNotAuthenticate() throws Exception {
        mvc.perform(get("/api/hello").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        mvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }
}

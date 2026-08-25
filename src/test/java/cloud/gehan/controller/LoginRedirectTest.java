package cloud.gehan.controller;

import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "portal.base-domain=gehan.cloud")
class LoginRedirectTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @BeforeEach
    void reset() {
        users.deleteAll();
        userService.create("Jen", UserService.ROLE_USER);
    }

    /** Posts the login form exactly as the template does, hidden field included. */
    private org.springframework.test.web.servlet.ResultActions signIn(String continueTo) throws Exception {
        var request = post("/login").with(csrf())
                .param("username", "Jen").param("password", "first-login");
        if (continueTo != null) {
            request = request.param("continue", continueTo);
        }
        return mvc.perform(request);
    }

    @Test
    void loginReturnsYouToAnotherServiceOnTheDomain() throws Exception {
        signIn("https://plex.gehan.cloud/library")
                .andExpect(authenticated())
                .andExpect(redirectedUrl("https://plex.gehan.cloud/library"));
    }

    @Test
    void loginWillNotForwardYouOffTheDomain() throws Exception {
        signIn("https://evil.com/phish")
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginWithNoContinueGoesToTheLauncher() throws Exception {
        signIn(null)
                .andExpect(authenticated())
                .andExpect(redirectedUrl("/"));
    }

    /** The hidden field must be present, or the round trip silently loses its target. */
    @Test
    void loginPageCarriesTheContinueParameterIntoTheForm() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/login").param("continue", "https://plex.gehan.cloud/library"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(
                                "name=\"continue\" value=\"https://plex.gehan.cloud/library\"")));
    }
}

package cloud.gehan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void portalRendersForASignedInUser() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"apps\"")))
                .andExpect(content().string(containsString("photos.gehan.cloud")));
    }

    @Test
    void usersTileIsHiddenFromNonAdmins() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")))
                .andExpect(content().string(not(containsString("href=\"/users\""))));
    }

    @Test
    void usersTileIsShownToAdmins() throws Exception {
        mvc.perform(get("/").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/users\"")));
    }

    @Test
    void portalRequiresSignIn() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection());
    }
}

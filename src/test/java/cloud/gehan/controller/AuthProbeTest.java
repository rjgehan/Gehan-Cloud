package cloud.gehan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "portal.base-domain=gehan.cloud")
class AuthProbeTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void signedInVisitorIsApproved() throws Exception {
        mvc.perform(get("/__auth").with(user("Jen").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Auth-User", "Jen"));
    }

    /**
     * An anonymous request still carries an Authentication whose isAuthenticated() is
     * true. If the probe checked only that, it would approve the whole internet and the
     * proxy would wave every request through.
     */
    @Test
    void anonymousVisitorIsNotApproved() throws Exception {
        mvc.perform(get("/__auth"))
                .andExpect(status().isFound());
    }

    @Test
    void anonymousVisitorIsSentToLoginAndBack() throws Exception {
        mvc.perform(get("/__auth")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "plex.gehan.cloud")
                        .header("X-Forwarded-Uri", "/library?section=2"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://gehan.cloud/login?continue=")))
                .andExpect(header().string("Location",
                        containsString("https%3A%2F%2Fplex.gehan.cloud%2Flibrary%3Fsection%3D2")));
    }

    /**
     * X-Forwarded-Host is only as trustworthy as the proxy in front. A spoofed one must
     * not turn the login page into an open redirect.
     */
    @Test
    void spoofedForwardedHostIsNotUsedAsAReturnTarget() throws Exception {
        mvc.perform(get("/__auth")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "evil.com")
                        .header("X-Forwarded-Uri", "/"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://gehan.cloud/login"));
    }

    @Test
    void probeWithoutProxyHeadersStillRedirectsToLogin() throws Exception {
        mvc.perform(get("/__auth"))
                .andExpect(header().string("Location", "https://gehan.cloud/login"));
    }
}

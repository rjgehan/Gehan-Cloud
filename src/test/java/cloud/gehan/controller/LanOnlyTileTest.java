package cloud.gehan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "portal.trusted-networks=192.168.1.0/24",
        "portal.apps[0].label=NAS",
        "portal.apps[0].url=http://192.168.1.50:5000",
        "portal.apps[0].icon=bi-hdd-network-fill",
        "portal.apps[0].lanOnly=true"
})
class LanOnlyTileTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void onTheHomeNetworkTheTileLinksToTheService() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("192.168.1.20")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"http://192.168.1.50:5000\"")))
                .andExpect(content().string(not(containsString("app-off"))));
    }

    @Test
    void fromOutsideTheTileCarriesNoLinkAtAll() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("203.0.113.9")))
                .andExpect(status().isOk())
                // still visible, so the service is not a secret from the family
                .andExpect(content().string(containsString("NAS")))
                .andExpect(content().string(containsString("app-off")))
                .andExpect(content().string(containsString("aria-disabled=\"true\"")))
                // the address must not be in the page at all, or it is still clickable
                .andExpect(content().string(not(containsString("192.168.1.50"))));
    }

    /**
     * Behind the proxy the socket peer is Traefik, not the visitor. This asserts the
     * assumption the whole feature rests on: that ForwardedHeaderFilter replaces
     * getRemoteAddr() with the address from X-Forwarded-For.
     */
    @Test
    void theAddressIsTakenFromTheProxyHeader() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER"))
                        .with(from("172.20.0.2"))                 // the proxy itself
                        .header("X-Forwarded-For", "192.168.1.20"))
                .andExpect(content().string(containsString("href=\"http://192.168.1.50:5000\"")));
    }

    @Test
    void aSpoofedHeaderFromOutsideStillOnlyRevealsTheTileNotTheService() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER"))
                        .with(from("203.0.113.9"))
                        .header("X-Forwarded-For", "192.168.1.20"))
                .andExpect(status().isOk());
        // Documented rather than defended: the proxy is what decides whether this
        // header can be trusted, and the service is unreachable from outside anyway.
    }
}

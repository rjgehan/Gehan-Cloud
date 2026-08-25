package cloud.gehan.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "portal.base-domain=gehan.cloud",
        "portal.trusted-networks=192.168.1.0/24"
})
class LanProbeTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void onTheLocalNetworkTheRequestIsLetThrough() throws Exception {
        mvc.perform(get("/__lan").with(from("192.168.1.20")))
                .andExpect(status().isOk());
    }

    @Test
    void fromOutsideTheVisitorIsSentToThePortal() throws Exception {
        mvc.perform(get("/__lan").with(from("203.0.113.9")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://gehan.cloud/"));
    }

    /** Behind the proxy the socket peer is Traefik, so the decision has to use the header. */
    @Test
    void theAddressComesFromTheProxyHeader() throws Exception {
        mvc.perform(get("/__lan").with(from("172.20.0.2")).header("X-Forwarded-For", "192.168.1.20"))
                .andExpect(status().isOk());

        mvc.perform(get("/__lan").with(from("172.20.0.2")).header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(status().isFound());
    }

    /** No session needed: this gate is about where you are, not who you are. */
    @Test
    void itDoesNotRequireASignedInSession() throws Exception {
        mvc.perform(get("/__lan").with(from("192.168.1.20")))
                .andExpect(status().isOk());
    }
}

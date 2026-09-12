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

/**
 * One tile, a different box in each house. Before this the two dashboards had to
 * answer at the same private address, and a DHCP lease moving one of them left the
 * tile linking to nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "portal.networks.home=203.0.113.9/32",
        "portal.networks.beach=198.51.100.7/32",
        "portal.apps[0].label=Dashboard",
        "portal.apps[0].icon=bi-speedometer2",
        "portal.apps[0].lanOnly=true",
        "portal.apps[0].urls.home=http://192.168.1.23:8088",
        "portal.apps[0].urls.beach=http://192.168.1.210:8088"
})
class PerNetworkTileTest {

    @Autowired
    private MockMvc mvc;

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void atHomeTheTilePointsAtTheHomeBox() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("203.0.113.9")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"http://192.168.1.23:8088\"")))
                .andExpect(content().string(not(containsString("192.168.1.210"))))
                .andExpect(content().string(not(containsString("app-off"))));
    }

    @Test
    void atTheBeachTheSameTilePointsAtTheBeachBox() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("198.51.100.7")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"http://192.168.1.210:8088\"")))
                .andExpect(content().string(not(containsString("192.168.1.23"))))
                .andExpect(content().string(not(containsString("app-off"))));
    }

    /**
     * Configuring only the named groups - no combined trusted list - must still light
     * the tile. Seen in the wild: the old list was replaced rather than added to, and
     * the tile went grey in both houses.
     */
    @Test
    void namedGroupsAloneAreEnoughToLightTheTile() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("198.51.100.7")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("app-off"))));
    }

    /** Neither house's address may reach the page from anywhere else. */
    @Test
    void fromOutsideNeitherAddressAppearsAtAll() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")).with(from("192.0.2.5")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dashboard")))
                .andExpect(content().string(containsString("app-off")))
                .andExpect(content().string(containsString("aria-disabled=\"true\"")))
                .andExpect(content().string(not(containsString("192.168.1.23"))))
                .andExpect(content().string(not(containsString("192.168.1.210"))));
    }
}

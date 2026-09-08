package cloud.gehan.security;

import cloud.gehan.config.PortalProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    private ClientAddress with(String header) {
        PortalProperties portal = new PortalProperties();
        portal.setClientIpHeader(header);
        return new ClientAddress(portal);
    }

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @Test
    void unconfiguredFallsBackToTheRemoteAddress() {
        assertThat(with("").of(request("203.0.113.9"))).isEqualTo("203.0.113.9");
    }

    @Test
    void unconfiguredIgnoresTheHeaderEntirely() {
        MockHttpServletRequest request = request("203.0.113.9");
        request.addHeader("CF-Connecting-IP", "192.168.1.20");
        assertThat(with("").of(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void configuredPrefersTheHeader() {
        MockHttpServletRequest request = request("172.20.0.2");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");
        assertThat(with("CF-Connecting-IP").of(request)).isEqualTo("203.0.113.9");
    }

    /** cloudflared reaches the app over a Docker network, so its own address is useless. */
    @Test
    void configuredFallsBackWhenTheHeaderIsAbsent() {
        assertThat(with("CF-Connecting-IP").of(request("192.168.1.20"))).isEqualTo("192.168.1.20");
    }

    @Test
    void configuredFallsBackWhenTheHeaderIsBlank() {
        MockHttpServletRequest request = request("192.168.1.20");
        request.addHeader("CF-Connecting-IP", "   ");
        assertThat(with("CF-Connecting-IP").of(request)).isEqualTo("192.168.1.20");
    }

    /**
     * The point of the setting. Cloudflare appends the real address to any
     * X-Forwarded-For the visitor sent, so ForwardedHeaderFilter resolves
     * getRemoteAddr() to the spoofed first entry. CF-Connecting-IP is written by the
     * edge on every request and wins over it.
     */
    @Test
    void configuredBeatsASpoofedForwardedForAlreadyAppliedToRemoteAddr() {
        MockHttpServletRequest request = request("192.168.1.20");   // the spoofed XFF
        request.addHeader("CF-Connecting-IP", "203.0.113.9");       // what Cloudflare saw
        assertThat(with("CF-Connecting-IP").of(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void headerNameIsMatchedCaseInsensitively() {
        MockHttpServletRequest request = request("172.20.0.2");
        request.addHeader("cf-connecting-ip", "203.0.113.9");
        assertThat(with("CF-Connecting-IP").of(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void takesTheLeftmostEntryIfAListEverArrives() {
        MockHttpServletRequest request = request("172.20.0.2");
        request.addHeader("CF-Connecting-IP", "203.0.113.9, 198.51.100.4");
        assertThat(with("CF-Connecting-IP").of(request)).isEqualTo("203.0.113.9");
    }
}

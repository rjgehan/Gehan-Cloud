package cloud.gehan.security;

import cloud.gehan.config.PortalProperties;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LocalNetworkTest {

    private LocalNetwork with(String... networks) {
        PortalProperties portal = new PortalProperties();
        portal.setTrustedNetworks(List.of(networks));
        return new LocalNetwork(portal);
    }

    private LocalNetwork named(Map<String, List<String>> groups) {
        PortalProperties portal = new PortalProperties();
        portal.setTrustedNetworks(groups.values().stream().flatMap(List::stream).toList());
        portal.setNetworks(groups);
        return new LocalNetwork(portal);
    }

    @Test
    void namesTheGroupAnAddressFallsIn() {
        LocalNetwork lan = named(new LinkedHashMap<>(Map.of(
                "home", List.of("203.0.113.9/32"))));
        assertThat(lan.nameFor("203.0.113.9")).isEqualTo("home");
    }

    @Test
    void tellsTwoGroupsApart() {
        LinkedHashMap<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("home", List.of("203.0.113.9/32"));
        groups.put("beach", List.of("198.51.100.7/32"));
        LocalNetwork lan = named(groups);
        assertThat(lan.nameFor("203.0.113.9")).isEqualTo("home");
        assertThat(lan.nameFor("198.51.100.7")).isEqualTo("beach");
    }

    /** Trusted but unnamed: reachable, yet no group's url applies. */
    @Test
    void returnsNullForAnAddressNoGroupClaims() {
        LocalNetwork lan = named(new LinkedHashMap<>(Map.of(
                "home", List.of("203.0.113.9/32"))));
        assertThat(lan.nameFor("198.51.100.7")).isNull();
        assertThat(lan.nameFor(null)).isNull();
    }

    @Test
    void namedGroupsDoNotDisturbTheTrustedList() {
        LocalNetwork lan = with("192.168.1.0/24");
        assertThat(lan.includes("192.168.1.5")).isTrue();
        assertThat(lan.nameFor("192.168.1.5")).isNull();
    }

    @Test
    void matchesAddressesInsideTheRange() {
        LocalNetwork lan = with("192.168.1.0/24");
        assertThat(lan.includes("192.168.1.1")).isTrue();
        assertThat(lan.includes("192.168.1.254")).isTrue();
    }

    @Test
    void rejectsAddressesOutsideTheRange() {
        LocalNetwork lan = with("192.168.1.0/24");
        assertThat(lan.includes("192.168.2.1")).isFalse();
        assertThat(lan.includes("8.8.8.8")).isFalse();
    }

    @Test
    void handlesSeveralRanges() {
        LocalNetwork lan = with("192.168.0.0/16", "10.0.0.0/8");
        assertThat(lan.includes("192.168.50.4")).isTrue();
        assertThat(lan.includes("10.1.2.3")).isTrue();
        assertThat(lan.includes("172.16.0.1")).isFalse();
    }

    @Test
    void acceptsASingleAddressForNatArrangementsThatCollapseTheLan() {
        // Hairpin NAT can make every visitor at home arrive as one address
        LocalNetwork lan = with("203.0.113.7/32");
        assertThat(lan.includes("203.0.113.7")).isTrue();
        assertThat(lan.includes("203.0.113.8")).isFalse();
    }

    @Test
    void understandsIpv4MappedIntoIpv6() {
        LocalNetwork lan = with("192.168.1.0/24");
        assertThat(lan.includes("::ffff:192.168.1.9")).isTrue();
    }

    @Test
    void ignoresAZoneIndex() {
        LocalNetwork lan = with("fe80::/10");
        assertThat(lan.includes("fe80::1%eth0")).isTrue();
    }

    @Test
    void unparseableConfigurationFailsClosedRatherThanOpen() {
        LocalNetwork lan = with("not-a-cidr", "192.168.1.0/24");
        assertThat(lan.includes("192.168.1.5")).isTrue();   // the good entry still works
        assertThat(lan.includes("8.8.8.8")).isFalse();      // the bad one matches nothing
    }

    @Test
    void nothingConfiguredMeansNobodyIsLocal() {
        LocalNetwork lan = with();
        assertThat(lan.includes("192.168.1.5")).isFalse();
    }

    @Test
    void missingOrJunkAddressesAreNotLocal() {
        LocalNetwork lan = with("192.168.1.0/24");
        assertThat(lan.includes(null)).isFalse();
        assertThat(lan.includes("")).isFalse();
        assertThat(lan.includes("unknown")).isFalse();
    }
}

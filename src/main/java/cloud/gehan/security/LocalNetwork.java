package cloud.gehan.security;

import cloud.gehan.config.PortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a visitor is on a network from which the host's unpublished
 * services are reachable.
 *
 * <p>This is presentation, not access control. What actually keeps those services
 * private is that their addresses do not route from the internet. All this does is
 * stop the launcher offering a link that would hang.
 */
@Component
public class LocalNetwork {

    private static final Logger log = LoggerFactory.getLogger(LocalNetwork.class);

    private final List<IpAddressMatcher> matchers = new ArrayList<>();

    /** Trusted networks split into named groups, in configured order. */
    private final Map<String, List<IpAddressMatcher>> named = new LinkedHashMap<>();

    public LocalNetwork(PortalProperties portal) {
        matchers.addAll(parse(portal.getTrustedNetworks(), "portal.trusted-networks"));
        portal.getNetworks().forEach((name, networks) -> {
            List<IpAddressMatcher> group = parse(networks, "portal.networks." + name);
            if (!group.isEmpty()) {
                named.put(name, group);
            }
        });
    }

    private static List<IpAddressMatcher> parse(List<String> networks, String source) {
        List<IpAddressMatcher> parsed = new ArrayList<>();
        if (networks == null) {
            return parsed;
        }
        for (String network : networks) {
            if (network == null || network.isBlank()) {
                continue;
            }
            try {
                parsed.add(new IpAddressMatcher(network.trim()));
            } catch (IllegalArgumentException e) {
                // Fail closed: a typo means nobody counts as local, rather than everybody.
                log.warn("Ignoring unparseable entry in {}: {}", source, network);
            }
        }
        return parsed;
    }

    public boolean includes(String address) {
        String ip = normalise(address);
        if (ip == null) {
            return false;
        }
        for (IpAddressMatcher matcher : matchers) {
            try {
                if (matcher.matches(ip)) {
                    return true;
                }
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return false;
    }

    /**
     * The name of the group this address falls in, or null when none claims it. First
     * match wins, so overlapping groups resolve in configured order.
     *
     * <p>{@link #includes} answers "can this visitor reach unpublished services at all";
     * this answers "which of my places are they standing in", which is what a tile needs
     * to pick between two addresses that are private to different networks.
     */
    public String nameFor(String address) {
        String ip = normalise(address);
        if (ip == null) {
            return null;
        }
        for (Map.Entry<String, List<IpAddressMatcher>> group : named.entrySet()) {
            for (IpAddressMatcher matcher : group.getValue()) {
                try {
                    if (matcher.matches(ip)) {
                        return group.getKey();
                    }
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Strips the forms an address can arrive in that a CIDR matcher will not accept. */
    private static String normalise(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String ip = address.trim();
        int zone = ip.indexOf('%');           // fe80::1%eth0
        if (zone > -1) {
            ip = ip.substring(0, zone);
        }
        if (ip.startsWith("::ffff:") && ip.indexOf('.') > -1) {
            ip = ip.substring("::ffff:".length());   // IPv4 mapped into IPv6
        }
        return ip.isEmpty() ? null : ip;
    }
}

package cloud.gehan.security;

import cloud.gehan.config.PortalProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    public LocalNetwork(PortalProperties portal) {
        for (String network : portal.getTrustedNetworks()) {
            if (network == null || network.isBlank()) {
                continue;
            }
            try {
                matchers.add(new IpAddressMatcher(network.trim()));
            } catch (IllegalArgumentException e) {
                // Fail closed: a typo means nobody counts as local, rather than everybody.
                log.warn("Ignoring unparseable entry in portal.trusted-networks: {}", network);
            }
        }
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

package cloud.gehan.security;

import cloud.gehan.config.PortalProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * The address a visitor appears to have arrived from.
 *
 * <p>By default this is {@code getRemoteAddr()}, which behind a proxy is the first
 * entry of {@code X-Forwarded-For} once Spring's {@code ForwardedHeaderFilter} has
 * applied it. That is the right answer for a proxy that owns the header, and the
 * wrong one for Cloudflare: the edge <em>appends</em> to {@code X-Forwarded-For}
 * rather than replacing it, so a visitor who sends their own header is the first
 * entry and the real client address is the last. The first token is then whatever
 * the visitor typed.
 *
 * <p>Setting {@code portal.client-ip-header=CF-Connecting-IP} reads Cloudflare's own
 * header instead, which the edge overwrites on every request and a visitor therefore
 * cannot supply. When the header is absent the remote address is used, so reaching
 * the container directly on the LAN still works.
 *
 * <p>This only holds while the app is unreachable except through the tunnel. Anything
 * that can open a socket to port 8080 can send the header itself. That is the same
 * caveat the tiles have always carried — see {@link LocalNetwork}.
 */
@Component
public class ClientAddress {

    private final String header;

    public ClientAddress(PortalProperties portal) {
        String configured = portal.getClientIpHeader();
        this.header = configured == null ? "" : configured.trim();
    }

    public String of(HttpServletRequest request) {
        if (!header.isEmpty()) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank()) {
                // CF-Connecting-IP is a single address by contract. Should a list ever
                // arrive, the leftmost entry is the one the edge wrote.
                int comma = value.indexOf(',');
                String first = (comma > -1 ? value.substring(0, comma) : value).trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
        }
        return request.getRemoteAddr();
    }
}

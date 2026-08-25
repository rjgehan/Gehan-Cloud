package cloud.gehan.security;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates where a login is allowed to send someone afterwards.
 *
 * <p>The {@code continue} parameter is attacker-supplied: anyone can send a family
 * member a link to /login?continue=... . Without this check that is an open
 * redirect, and it carries real traffic once other subdomains sign in through
 * this app.
 */
public final class RedirectTargets {

    private RedirectTargets() {
    }

    /**
     * Returns {@code candidate} when it is safe to redirect to, otherwise {@code fallback}.
     *
     * <p>Safe means either a path on this host, or an absolute http(s) URL whose host is
     * {@code baseDomain} or a subdomain of it. With no baseDomain configured only paths
     * are allowed.
     */
    public static String resolve(String candidate, String baseDomain, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }

        // A backslash or a control character means someone is trying to confuse either
        // this parser or the browser's; they never appear in a legitimate target.
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (c == '\\' || c < 0x20 || c == 0x7f) {
                return fallback;
            }
        }

        // "//evil.com" is protocol-relative: it looks like a path but leaves the site.
        if (candidate.startsWith("//")) {
            return fallback;
        }
        if (candidate.startsWith("/")) {
            return candidate;
        }

        if (baseDomain == null || baseDomain.isBlank()) {
            return fallback;
        }

        URI uri;
        try {
            uri = new URI(candidate);
        } catch (URISyntaxException e) {
            return fallback;
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return fallback;
        }

        // getHost() is what the browser will actually connect to, so userinfo tricks
        // like https://gehan.cloud@evil.com resolve to evil.com here and are rejected.
        String host = uri.getHost();
        if (host == null) {
            return fallback;
        }

        String domain = baseDomain.toLowerCase();
        host = host.toLowerCase();
        return host.equals(domain) || host.endsWith("." + domain) ? candidate : fallback;
    }
}

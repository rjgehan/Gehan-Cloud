package cloud.gehan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The portal's app tiles, loaded from apps.yml. Adding a service is a single
 * entry there rather than an edit to the template.
 */
@Component
@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private List<AppLink> apps = new ArrayList<>();

    /**
     * The domain this portal serves, e.g. gehan.cloud. Used to decide which redirect
     * targets are ours, and to build the absolute login URL a forward-auth redirect
     * needs. Unset means only same-host paths are accepted, which suits local runs.
     */
    private String baseDomain = "";

    public String getBaseDomain() {
        return baseDomain;
    }

    public void setBaseDomain(String baseDomain) {
        this.baseDomain = baseDomain;
    }

    /**
     * Networks a visitor can be on and still reach services that are not published to
     * the internet. Tiles marked lanOnly are live only for these, and inert otherwise.
     */
    private List<String> trustedNetworks = new ArrayList<>(List.of(
            "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.1/32", "::1/128"));

    public List<String> getTrustedNetworks() {
        return trustedNetworks;
    }

    public void setTrustedNetworks(List<String> trustedNetworks) {
        this.trustedNetworks = trustedNetworks;
    }

    /**
     * Named groups within the trusted networks, e.g. portal.networks.home. A tile can
     * then give a different url per group, for a service that sits at a different
     * address in each place. Without this the app knows only that a visitor is on one
     * of the trusted networks, not which - so two houses could not be told apart.
     *
     * <p>These are public addresses, so they belong in the environment, not in here.
     */
    private Map<String, List<String>> networks = new LinkedHashMap<>();

    public Map<String, List<String>> getNetworks() {
        return networks;
    }

    public void setNetworks(Map<String, List<String>> networks) {
        this.networks = networks;
    }

    /**
     * Header carrying the visitor's address, when a proxy in front supplies one it
     * fully controls. Behind Cloudflare set this to CF-Connecting-IP: the edge appends
     * to X-Forwarded-For rather than replacing it, so the first entry there is
     * whatever the visitor sent. Empty means trust the remote address as-is.
     */
    private String clientIpHeader = "";

    public String getClientIpHeader() {
        return clientIpHeader;
    }

    public void setClientIpHeader(String clientIpHeader) {
        this.clientIpHeader = clientIpHeader;
    }

    public List<AppLink> getApps() {
        return apps;
    }

    public void setApps(List<AppLink> apps) {
        this.apps = apps;
    }

    /** The tiles a given viewer may see, in configured order. */
    public List<AppLink> visibleTo(boolean admin) {
        return apps.stream()
                .filter(app -> admin || !app.isAdminOnly())
                .toList();
    }

    public static class AppLink {

        private String label;
        private String url;
        private String icon = "bi-box-arrow-up-right";
        private String color = "slate";
        private boolean adminOnly;
        private boolean lanOnly;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        /**
         * Per-network urls, keyed by a name from portal.networks. The entry matching the
         * visitor's network wins, and {@code url} is the fallback when none does.
         *
         * <p>This is what lets one tile serve a service that lives in two places without
         * the two boxes having to answer at the same address - which they did have to
         * before, and which broke the moment one of them was handed a different lease.
         */
        private Map<String, String> urls = new LinkedHashMap<>();

        public Map<String, String> getUrls() {
            return urls;
        }

        public void setUrls(Map<String, String> urls) {
            this.urls = urls;
        }

        /**
         * The url to use for a visitor on the named network, or null when the tile has
         * nothing for them. Null is what makes a lanOnly tile render without an href, so
         * a private address belonging to the other house never reaches the page.
         */
        public String urlFor(String network) {
            if (network != null) {
                String match = urls.get(network);
                if (match != null && !match.isBlank()) {
                    return match;
                }
            }
            return url;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public boolean isAdminOnly() {
            return adminOnly;
        }

        public void setAdminOnly(boolean adminOnly) {
            this.adminOnly = adminOnly;
        }

        /**
         * True for a service that is only routable from the host's own network. The
         * tile is shown to everyone but only links anywhere for a visitor on that
         * network, so nobody is left clicking a link that quietly times out.
         */
        public boolean isLanOnly() {
            return lanOnly;
        }

        public void setLanOnly(boolean lanOnly) {
            this.lanOnly = lanOnly;
        }

        /** True when the resolved link leaves this app, so those tiles open in a new tab. */
        public boolean isExternalFor(String network) {
            String target = urlFor(network);
            return target != null && (target.startsWith("http://") || target.startsWith("https://"));
        }

        /** True when the link leaves this app, so those tiles can open in a new tab. */
        public boolean isExternal() {
            return isExternalFor(null);
        }
    }
}

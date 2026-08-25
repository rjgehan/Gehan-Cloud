package cloud.gehan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The portal's app tiles, loaded from apps.yml. Adding a service is a single
 * entry there rather than an edit to the template.
 */
@Component
@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private List<AppLink> apps = new ArrayList<>();

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

        /** True when the link leaves this app, so those tiles can open in a new tab. */
        public boolean isExternal() {
            return url != null && (url.startsWith("http://") || url.startsWith("https://"));
        }
    }
}

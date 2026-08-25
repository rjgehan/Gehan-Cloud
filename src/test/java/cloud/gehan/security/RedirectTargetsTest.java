package cloud.gehan.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectTargetsTest {

    private static final String DOMAIN = "gehan.cloud";
    private static final String FALLBACK = "/";

    private String resolve(String candidate) {
        return RedirectTargets.resolve(candidate, DOMAIN, FALLBACK);
    }

    @Test
    void pathsOnThisHostAreAllowed() {
        assertThat(resolve("/users")).isEqualTo("/users");
        assertThat(resolve("/pages/thing.html?x=1")).isEqualTo("/pages/thing.html?x=1");
    }

    @Test
    void ourOwnDomainAndSubdomainsAreAllowed() {
        assertThat(resolve("https://gehan.cloud/users")).isEqualTo("https://gehan.cloud/users");
        assertThat(resolve("https://plex.gehan.cloud/library"))
                .isEqualTo("https://plex.gehan.cloud/library");
        assertThat(resolve("https://PLEX.GEHAN.CLOUD/x")).isEqualTo("https://PLEX.GEHAN.CLOUD/x");
    }

    @Test
    void otherHostsAreRejected() {
        assertThat(resolve("https://evil.com/steal")).isEqualTo(FALLBACK);
        assertThat(resolve("http://evil.com")).isEqualTo(FALLBACK);
    }

    @Test
    void lookalikeDomainsAreRejected() {
        // endsWith without the dot would have let these through
        assertThat(resolve("https://notgehan.cloud/x")).isEqualTo(FALLBACK);
        assertThat(resolve("https://gehan.cloud.evil.com/x")).isEqualTo(FALLBACK);
    }

    @Test
    void userinfoTrickIsRejected() {
        // The browser connects to evil.com here, whatever it looks like
        assertThat(resolve("https://gehan.cloud@evil.com/")).isEqualTo(FALLBACK);
        assertThat(resolve("https://gehan.cloud:pass@evil.com/")).isEqualTo(FALLBACK);
    }

    @Test
    void protocolRelativeUrlIsRejected() {
        // Looks like a path, but the browser treats it as a different site
        assertThat(resolve("//evil.com/steal")).isEqualTo(FALLBACK);
    }

    @Test
    void backslashAndControlCharactersAreRejected() {
        assertThat(resolve("/\\evil.com")).isEqualTo(FALLBACK);
        assertThat(resolve("https:/\\evil.com")).isEqualTo(FALLBACK);
        // a real newline, which is how header-injection attempts look
        assertThat(resolve("/users\nLocation: https://evil.com")).isEqualTo(FALLBACK);
    }

    @Test
    void nonHttpSchemesAreRejected() {
        assertThat(resolve("javascript:alert(1)")).isEqualTo(FALLBACK);
        assertThat(resolve("data:text/html,<script>alert(1)</script>")).isEqualTo(FALLBACK);
        assertThat(resolve("ftp://gehan.cloud/x")).isEqualTo(FALLBACK);
    }

    @Test
    void emptyAndMalformedFallBack() {
        assertThat(resolve(null)).isEqualTo(FALLBACK);
        assertThat(resolve("")).isEqualTo(FALLBACK);
        assertThat(resolve("   ")).isEqualTo(FALLBACK);
        assertThat(resolve("http://[::bad::]/")).isEqualTo(FALLBACK);
    }

    @Test
    void withoutABaseDomainOnlyPathsAreAllowed() {
        assertThat(RedirectTargets.resolve("/users", "", FALLBACK)).isEqualTo("/users");
        assertThat(RedirectTargets.resolve("https://gehan.cloud/users", "", FALLBACK))
                .isEqualTo(FALLBACK);
    }
}

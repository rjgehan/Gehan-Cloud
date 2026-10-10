package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.GlobleDay;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * The real template, rendered by real Thymeleaf through the main security chain - the
 * one thing the controller test deliberately does not do. It pins down what the page's
 * script relies on (the CSRF meta tags, the data attributes, the script order) and what
 * the site's CSP relies on (no inline script, no inline handlers), and that the page
 * never carries the day's answer, which only the API reveals and only once a game ends.
 */
class GloblePageRenderTest extends GlobleTestBase {

    private static final Pattern CSRF_TOKEN = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"");
    private static final Pattern CSRF_HEADER = Pattern.compile("<meta name=\"_csrf_header\" content=\"([^\"]+)\"");
    /** A script tag without a src attribute is inline script, which script-src 'self' blocks. */
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
    /** onclick=, onload=, ... anywhere in a tag. */
    private static final Pattern INLINE_HANDLER = Pattern.compile("<[^>]+\\son[a-z]+\\s*=", Pattern.CASE_INSENSITIVE);

    @Test
    void thePageRendersForASignedInPlayer() throws Exception {
        String html = render("jen");

        Matcher token = CSRF_TOKEN.matcher(html);
        assertThat(token.find()).as("CSRF token meta tag").isTrue();
        assertThat(token.group(1)).isNotBlank();
        Matcher header = CSRF_HEADER.matcher(html);
        assertThat(header.find()).as("CSRF header meta tag").isTrue();
        assertThat(header.group(1)).isEqualTo("X-CSRF-TOKEN");

        assertThat(html)
                .contains("data-puzzle=\"1\"")
                .contains("data-date=\"2026-10-09\"")
                .contains("data-username=\"jen\"")
                .contains("<span class=\"gl-title-tag\" id=\"title-tag\">#1</span>")
                .contains("href=\"/globle/css/globle.css\"");
    }

    /** d3 and topojson are classic scripts the modules read off window, so they must come first. */
    @Test
    void theVendorScriptsLoadBeforeTheGameModule() throws Exception {
        String html = render("jen");
        int d3 = html.indexOf("<script src=\"/globle/vendor/d3.min.js\"></script>");
        int topojson = html.indexOf("<script src=\"/globle/vendor/topojson-client.min.js\"></script>");
        int main = html.indexOf("<script type=\"module\" src=\"/globle/js/main.js\"></script>");
        assertThat(d3).as("d3 script").isPositive();
        assertThat(topojson).as("topojson script").isGreaterThan(d3);
        assertThat(main).as("module script").isGreaterThan(topojson);
    }

    @Test
    void thePageHasNothingTheSitesContentSecurityPolicyWouldBlock() throws Exception {
        MvcResult result = mvc.perform(get("/globle").with(player("jen")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("script-src 'self'")))
                .andReturn();
        String html = result.getResponse().getContentAsString();
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("inline <script>").isFalse();
        assertThat(INLINE_HANDLER.matcher(html).find()).as("inline event handler").isFalse();
    }

    /**
     * The page opens on the home screen, where the game is chosen: the two dailies
     * and the practice presets. The old mode switcher, region dropdown and Blind
     * toggle are gone (blind is part of Hard now), and the first-visit popup and the
     * help sheet stay short.
     */
    @Test
    void thePageOpensOnTheHomeScreenWithoutTheOldControls() throws Exception {
        String html = render("jen");

        assertThat(html)
                .contains("data-view=\"home\"")
                .contains("id=\"card-classic\" href=\"#/classic\"")
                .contains("id=\"card-hard\" href=\"#/hard\"")
                .contains("id=\"btn-home\" href=\"#/\"")
                .contains("id=\"home-error\"")
                .contains("id=\"btn-home-retry\"")
                .doesNotContain("btn-blind")
                .doesNotContain("<select")
                .doesNotContain("gl-seg");
        assertThat(words(dialog(html, "dlg-welcome"))).as("words in the first-visit popup").isLessThanOrEqualTo(25);
        assertThat(words(dialog(html, "dlg-help"))).as("words in the help sheet, legend aside").isLessThanOrEqualTo(80);
    }

    @Test
    void thePageNeverCarriesTheDaysAnswer() throws Exception {
        days.saveAndFlush(new GlobleDay(DAY_ONE, "MNG", 1, clock.instant()));
        // Someone else is already playing today, so the answer is settled and live.
        guessDaily(player("colleen"), "FRA");

        String html = render("jen");

        for (String clue : List.of("MNG", "Mongolia", "Ulaanbaatar", "Ulan Bator")) {
            assertThat(html).as("page mentions " + clue).doesNotContain(clue);
        }
    }

    /** The files the page asks for are served to a signed-in player, with types a module loader accepts. */
    @Test
    void thePagesOwnFilesAreServed() throws Exception {
        var jen = player("jen");
        for (String js : List.of("/globle/js/main.js", "/globle/js/api.js", "/globle/js/modes.js",
                "/globle/vendor/d3.min.js")) {
            String type = mvc.perform(get(js).with(jen))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentType();
            assertThat(type).as(js).containsAnyOf("text/javascript", "application/javascript");
        }
        mvc.perform(get("/globle/css/globle.css").with(jen))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/css"));
        mvc.perform(get("/globle/world.json").with(jen))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * The globe's shapes, d3 and the game's scripts are about 1.2 MB. The browser keeps
     * them and revalidates each visit, so an unchanged file costs a 304 rather than a
     * download, and a changed one is never served stale. The page itself is not cached.
     */
    @Test
    void thePagesOwnFilesAreRevalidatedRatherThanDownloadedAgain() throws Exception {
        var jen = player("jen");
        for (String file : List.of("/globle/world.json", "/globle/vendor/d3.min.js", "/globle/js/main.js",
                "/globle/css/globle.css")) {
            MvcResult first = mvc.perform(get(file).with(jen))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control",
                            allOf(containsString("no-cache"), containsString("private"), not(containsString("no-store")))))
                    .andReturn();
            String etag = first.getResponse().getHeader("ETag");
            assertThat(etag).as(file + " ETag").isNotBlank();

            mvc.perform(get(file).with(jen).header("If-None-Match", etag))
                    .andExpect(status().isNotModified());
        }
        mvc.perform(get("/globle").with(jen))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    /** The content of one {@code <dialog>}, by id: from the end of its opening tag to its closing tag. */
    private static String dialog(String html, String id) {
        int tag = html.indexOf("<dialog class=\"gl-sheet");
        int at = html.indexOf("id=\"" + id + "\"", tag);
        assertThat(tag).as("a dialog").isPositive();
        assertThat(at).as("dialog " + id).isPositive();
        int start = html.indexOf('>', at) + 1;
        return html.substring(start, html.indexOf("</dialog>", start));
    }

    /**
     * Words a reader sees: tags, screen-reader-only text and the colour legend's
     * labels left out, entities as spaces, and anything that is not a letter, digit
     * or apostrophe a separator.
     */
    private static int words(String markup) {
        String text = markup
                .replaceAll("(?s)<span class=\"sr-only\">.*?</span>", " ")
                .replaceAll("(?s)<div class=\"gl-legend-labels\">.*?</div>", " ")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("&[a-z]+;", " ")
                .replaceAll("[^\\p{L}\\p{N}']+", " ")
                .trim();
        return text.isEmpty() ? 0 : text.split(" ").length;
    }

    private String render(String username) throws Exception {
        return mvc.perform(get("/globle").with(player(username)))
                .andExpect(status().isOk())
                .andExpect(view().name("globle/index"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn().getResponse().getContentAsString();
    }
}

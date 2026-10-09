package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The API's own security chain: JSON 401s instead of login redirects, CSRF on, and the
 * launcher's session login honoured. Never renders the Globle page itself.
 */
class GlobleSecurityTest extends GlobleTestBase {

    private static final Pattern CSRF_FIELD = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    /** A script asked for JSON; handing it the login page would look like data. */
    @Test
    void signedOutApiRequestsGetA401InJson() throws Exception {
        for (String url : new String[]{"/globle/api/daily", "/globle/api/countries", "/globle/api/leaderboard",
                "/globle/api/unlimited/current?kind=country"}) {
            mvc.perform(MockMvcRequestBuilders.get(url))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json("{\"error\":\"signed-out\"}", true));
        }
    }

    /** Without a session there is no CSRF token either; the honest answer is still "signed out". */
    @Test
    void signedOutPostsGetA401WithOrWithoutAToken() throws Exception {
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/guess").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/guess")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
        assertThat(games.count()).isZero();
    }

    /** The page and its files stay with the main chain: a redirect to sign in, like the launcher. */
    @Test
    void signedOutPageRequestsAreSentToTheLoginPage() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/globle"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(MockMvcRequestBuilders.get("/globle/world.json"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void aSignedInPostWithoutACsrfTokenIsForbidden() throws Exception {
        player("jen");
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/guess").with(user("jen"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"forbidden\"}", true));
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/unlimited/new").with(user("jen"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        assertThat(games.count()).isZero();
    }

    /**
     * The real flow end to end: sign in through the launcher's form, then use that
     * session - and the CSRF token a page under the main chain renders into it - on
     * the API. This is what proves the two chains share the session and the token.
     */
    @Test
    void theLaunchersSessionAndCsrfTokenWorkOnTheApi() throws Exception {
        userService.create("jen", "USER");
        MvcResult login = mvc.perform(formLogin().user("jen").password("first-login"))
                .andExpect(authenticated().withUsername("jen"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mvc.perform(MockMvcRequestBuilders.get("/globle/api/daily").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"PLAYING\"")));

        // The login page is rendered by the main chain and carries the session's token,
        // exactly as the Globle page's <meta name="_csrf"> does.
        String html = mvc.perform(MockMvcRequestBuilders.get("/login").session(session))
                .andReturn().getResponse().getContentAsString();
        Matcher field = CSRF_FIELD.matcher(html);
        assertThat(field.find()).as("login page renders a CSRF token").isTrue();
        String token = field.group(1);

        String guess = farFrom(answerToday());
        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/guess").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", guess))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"guessCount\":1")));

        mvc.perform(MockMvcRequestBuilders.post("/globle/api/daily/give-up").session(session)
                        .header("X-CSRF-TOKEN", "not-the-token")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
        assertThat(games.findAll().get(0).isPlaying()).isTrue();
    }

    /** A session can outlive its account. It must not keep playing as nobody. */
    @Test
    void aSessionForADeletedAccountIsSignedOut() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/daily").with(user("ghost")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
    }

    @Test
    void apiResponsesKeepTheDefaultSecurityHeaders() throws Exception {
        var jen = player("jen");
        mvc.perform(MockMvcRequestBuilders.get("/globle/api/daily").with(jen))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    /** The launcher's own chain is untouched by the new one in front of it. */
    @Test
    void theLauncherStillBehavesAsBefore() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(MockMvcRequestBuilders.get("/").with(user("Jen").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        mvc.perform(MockMvcRequestBuilders.get("/users").with(user("Jen").roles("USER")))
                .andExpect(status().isForbidden());
    }
}

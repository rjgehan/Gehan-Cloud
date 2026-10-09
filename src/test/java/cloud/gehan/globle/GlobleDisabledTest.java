package cloud.gehan.globle;

import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The owner's requirement: Globle can be switched off - or ripped out - with no effect
 * on the rest of the app. With globle.enabled=false not one Globle bean, route or
 * filter chain may exist, and the launcher, login and user console must behave exactly
 * as they did before the game was added.
 */
@SpringBootTest(properties = "globle.enabled=false")
@AutoConfigureMockMvc
class GlobleDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserService userService;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @BeforeEach
    void reset() {
        users.deleteAll();
    }

    @Test
    void noGlobleBeanExists() {
        List<String> globle = new ArrayList<>();
        for (String name : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(name);
            if (type != null && type.getName().startsWith("cloud.gehan.globle")) {
                globle.add(name + " (" + type.getName() + ")");
            }
        }
        assertThat(globle).as("Globle beans present while disabled").isEmpty();
    }

    @Test
    void onlyTheAppsOwnSecurityChainIsInstalled() {
        assertThat(context.getBeansOfType(SecurityFilterChain.class)).containsOnlyKeys("filterChain");
        assertThat(springSecurityFilterChain.getFilterChains()).hasSize(1);
    }

    @Test
    void theGamesRoutesAreGone() throws Exception {
        mvc.perform(get("/globle/api/daily").with(user("Jen").roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/globle/api/countries").with(user("Jen").roles("USER")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/globle/api/daily/guess").with(user("Jen").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"FRA\"}"))
                .andExpect(status().is4xxClientError());
    }

    /**
     * No controller answers /globle any more. What is left is Spring's static resource
     * handler finding the static/globle directory, which it treats exactly as it treats
     * /css: the same handler and the same status. (Run from a packaged jar that is a
     * 404; from exploded classes, as here, Spring lists a directory's contents for
     * either.) The page's files go only when the directory is deleted - see "Removing
     * Globle" in docs/globle.md.
     */
    @Test
    void thePageIsJustAStaticDirectoryLikeAnyOther() throws Exception {
        int css = mvc.perform(get("/css").with(user("Jen").roles("USER")))
                .andReturn().getResponse().getStatus();
        mvc.perform(get("/globle").with(user("Jen").roles("USER")))
                .andExpect(handler().handlerType(ResourceHttpRequestHandler.class))
                .andExpect(status().is(css));
    }

    /** Before Globle, every unknown path sent an anonymous visitor to sign in. It still does. */
    @Test
    void signedOutVisitorsAreSentToLoginAsBefore() throws Exception {
        mvc.perform(get("/globle/api/daily"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
        mvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void theLauncherLoginAndUserConsoleWorkAsBefore() throws Exception {
        mvc.perform(get("/").with(user("Jen").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"apps\"")))
                .andExpect(content().string(containsString("photos.gehan.cloud")));
        mvc.perform(get("/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
        mvc.perform(get("/login"))
                .andExpect(status().isOk());

        userService.create("Colleen", UserService.ROLE_USER);
        mvc.perform(formLogin().user("Colleen").password("firstChoice"))
                .andExpect(authenticated().withUsername("Colleen"))
                .andExpect(redirectedUrl("/"));
    }
}

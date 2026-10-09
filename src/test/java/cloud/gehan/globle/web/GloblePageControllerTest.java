package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The page's model and mapping, checked without rendering the template: the template
 * belongs to the frontend, and is exercised by its own test.
 */
class GloblePageControllerTest extends GlobleTestBase {

    @Autowired
    private GloblePageController controller;

    @Autowired
    private RequestMappingHandlerMapping mappings;

    @Test
    void thePageModelCarriesTodaysPuzzleAndTheViewer() {
        clock.setDay(day(12));
        ExtendedModelMap model = new ExtendedModelMap();
        var jen = UsernamePasswordAuthenticationToken.authenticated("jen", null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));

        String view = controller.page(model, jen);

        assertThat(view).isEqualTo("globle/index");
        assertThat(model.get("puzzleNumber")).isEqualTo(12);
        assertThat(model.get("puzzleDate")).isEqualTo("2026-10-20");
        assertThat(model.get("username")).isEqualTo("jen");
    }

    @Test
    void bothSpellingsOfThePathReachThePage() throws Exception {
        for (String path : new String[]{"/globle", "/globle/"}) {
            HandlerExecutionChain chain = mappings.getHandler(new MockHttpServletRequest("GET", path));
            assertThat(chain).as(path).isNotNull();
            HandlerMethod handler = (HandlerMethod) chain.getHandler();
            assertThat(handler.getBeanType()).isEqualTo(GloblePageController.class);
        }
    }
}

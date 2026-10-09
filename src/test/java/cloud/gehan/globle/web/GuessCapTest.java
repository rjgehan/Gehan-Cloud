package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The defensive ceiling on guesses. Duplicates are refused, so with the real data a
 * game runs out of countries long before 400; a tiny cap is the only way to reach it.
 */
@TestPropertySource(properties = "globle.max-guesses=2")
class GuessCapTest extends GlobleTestBase {

    @Test
    void guessesStopAtTheCapButTheGameCanStillBeGivenUp() throws Exception {
        var jen = player("jen");
        String answer = answerToday();
        List<String> wrong = wrongGuesses(answer, 3);
        guessDaily(jen, wrong.get(0));
        guessDaily(jen, wrong.get(1));

        post("/globle/api/daily/guess", Map.of("code", wrong.get(2)), jen)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad-request"));
        post("/globle/api/daily/guess", Map.of("code", answer), jen)
                .andExpect(status().isBadRequest());

        assertThat(giveUpDaily(jen).get("status").asText()).isEqualTo("GAVE_UP");
    }
}

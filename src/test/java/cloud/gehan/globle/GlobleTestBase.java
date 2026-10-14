package cloud.gehan.globle;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Variant;
import cloud.gehan.globle.repository.GlobleDayRepository;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.repository.GlobleHardDayRepository;
import cloud.gehan.globle.repository.GlobleNameRequestRepository;
import cloud.gehan.globle.service.DailyPuzzles;
import cloud.gehan.repository.UserRepository;
import cloud.gehan.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared setup for Globle's integration tests. Every subclass gets the same Spring
 * context (same configuration, so the test framework caches one), with time on a
 * {@link MovableClock} and empty Globle tables and users before each test.
 *
 * <p>The game is pinned on, as GlobleDisabledTest pins it off, so that an exported
 * {@code GLOBLE_ENABLED=false} cannot switch it off underneath these tests.
 */
@SpringBootTest(properties = "globle.enabled=true")
@AutoConfigureMockMvc
@Import(GlobleTestBase.TestClock.class)
public abstract class GlobleTestBase {

    /** Puzzle #1. */
    protected static final LocalDate DAY_ONE = LocalDate.of(2026, 10, 9);

    @TestConfiguration
    static class TestClock {
        @Bean
        MovableClock globleTimeSource() {
            return new MovableClock();
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected MovableClock clock;

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository users;

    @Autowired
    protected GlobleGameRepository games;

    @Autowired
    protected GlobleDayRepository days;

    @Autowired
    protected GlobleHardDayRepository hardDays;

    @Autowired
    protected GlobleNameRequestRepository nameRequests;

    @Autowired
    protected DailyPuzzles puzzles;

    @Autowired
    protected CountryCatalog catalog;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void resetGloble() {
        games.deleteAll();
        days.deleteAll();
        hardDays.deleteAll();
        nameRequests.deleteAll();
        users.deleteAll();
        clock.setDay(DAY_ONE);
    }

    /** Creates an account and returns a signed-in session for it. */
    protected RequestPostProcessor player(String username) {
        userService.create(username, UserService.ROLE_USER);
        return user(username).roles(UserService.ROLE_USER);
    }

    /** Creates an admin account and returns a signed-in session for it, with the ADMIN role as /users wants. */
    protected RequestPostProcessor admin(String username) {
        userService.create(username, UserService.ROLE_ADMIN);
        return user(username).roles(UserService.ROLE_ADMIN);
    }

    protected LocalDate day(int puzzleNumber) {
        return DAY_ONE.plusDays(puzzleNumber - 1L);
    }

    // ------------------------------------------------------------------ requests

    protected JsonNode getOk(String url, RequestPostProcessor who) throws Exception {
        return read(mvc.perform(get(url).with(who)).andExpect(status().isOk()).andReturn());
    }

    protected ResultActions post(String url, Object body, RequestPostProcessor who) throws Exception {
        var request = MockMvcRequestBuilders.post(url)
                .with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON);
        if (body != null) {
            request = request.content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    protected JsonNode postOk(String url, Object body, RequestPostProcessor who) throws Exception {
        return read(post(url, body, who).andExpect(status().isOk()).andReturn());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode guessDaily(RequestPostProcessor who, String code) throws Exception {
        return postOk("/globle/api/daily/guess", Map.of("code", code), who);
    }

    protected JsonNode guessDaily(RequestPostProcessor who, String code, Variant variant) throws Exception {
        return postOk("/globle/api/daily/guess", Map.of("code", code, "variant", variant.api()), who);
    }

    protected JsonNode giveUpDaily(RequestPostProcessor who) throws Exception {
        return postOk("/globle/api/daily/give-up", null, who);
    }

    protected JsonNode giveUpDaily(RequestPostProcessor who, Variant variant) throws Exception {
        return postOk("/globle/api/daily/give-up", Map.of("variant", variant.api()), who);
    }

    protected JsonNode daily(RequestPostProcessor who, Variant variant) throws Exception {
        return getOk("/globle/api/daily?variant=" + variant.api(), who);
    }

    protected JsonNode newGame(RequestPostProcessor who, String kind, String region) throws Exception {
        return postOk("/globle/api/unlimited/new", Map.of("kind", kind, "region", region), who);
    }

    protected JsonNode newGame(RequestPostProcessor who, String kind, String region, Variant variant)
            throws Exception {
        return postOk("/globle/api/unlimited/new",
                Map.of("kind", kind, "region", region, "variant", variant.api()), who);
    }

    protected JsonNode home(RequestPostProcessor who) throws Exception {
        return getOk("/globle/api/home", who);
    }

    protected JsonNode guessUnlimited(RequestPostProcessor who, long id, String code) throws Exception {
        return postOk("/globle/api/unlimited/" + id + "/guess", Map.of("code", code), who);
    }

    protected JsonNode board(RequestPostProcessor who) throws Exception {
        return getOk("/globle/api/leaderboard", who);
    }

    protected JsonNode board(RequestPostProcessor who, Variant variant) throws Exception {
        return getOk("/globle/api/leaderboard?variant=" + variant.api(), who);
    }

    /**
     * Makes every stored game look as it did before Hard existed: no variant. That is
     * how the rows already in production read after the column is added.
     */
    protected void forgetVariants() {
        jdbc.update("update globle_game set variant = null");
    }

    // ------------------------------------------------------------------ answers

    protected String answerToday() {
        return puzzles.answerFor(clock.instant().atZone(MovableClock.PUZZLE_ZONE).toLocalDate());
    }

    protected String answerToday(Variant variant) {
        return puzzles.answerFor(variant, clock.instant().atZone(MovableClock.PUZZLE_ZONE).toLocalDate());
    }

    /** The stored answer of an unlimited game, which the API never shows mid-game. */
    protected String answerOf(long gameId) {
        return games.findById(gameId).orElseThrow().getAnswerCode();
    }

    /** {@code n} distinct places that are not {@code answer}, in catalogue order. */
    protected List<String> wrongGuesses(String answer, int n) {
        List<String> codes = new ArrayList<>();
        for (Country country : catalog.all()) {
            if (codes.size() == n) {
                break;
            }
            if (!country.code().equals(answer)) {
                codes.add(country.code());
            }
        }
        return codes;
    }

    /** A place that is not the answer and does not border it, so its distance is real. */
    protected String farFrom(String answer) {
        return catalog.all().stream()
                .map(Country::code)
                .filter(code -> !code.equals(answer) && catalog.km(code, answer) > 0)
                .findFirst()
                .orElseThrow();
    }

    /** Wins today's daily in exactly {@code guesses} guesses. */
    protected JsonNode winDailyIn(RequestPostProcessor who, int guesses) throws Exception {
        String answer = answerToday();
        for (String wrong : wrongGuesses(answer, guesses - 1)) {
            guessDaily(who, wrong);
        }
        return guessDaily(who, answer);
    }

    /** Wins today's daily of one variant in exactly {@code guesses} guesses. */
    protected JsonNode winDailyIn(RequestPostProcessor who, int guesses, Variant variant) throws Exception {
        String answer = answerToday(variant);
        for (String wrong : wrongGuesses(answer, guesses - 1)) {
            guessDaily(who, wrong, variant);
        }
        return guessDaily(who, answer, variant);
    }

    /** The player's database id, for building {@code Player}s and rows by hand. */
    protected long idOf(String username) {
        return users.findByUsername(username).orElseThrow().getId();
    }

    // ------------------------------------------------------------------ roll call

    protected JsonNode startRollCall(RequestPostProcessor who, String region, String autofill) throws Exception {
        return postOk("/globle/api/rollcall/new", Map.of("region", region, "autofill", autofill), who);
    }

    protected JsonNode nameIn(RequestPostProcessor who, long runId, String code) throws Exception {
        return postOk("/globle/api/rollcall/" + runId + "/name", Map.of("code", code), who);
    }

    protected JsonNode finishRollCall(RequestPostProcessor who, long runId) throws Exception {
        return postOk("/globle/api/rollcall/" + runId + "/finish", null, who);
    }

    protected JsonNode rollCallOptions(RequestPostProcessor who, String region) throws Exception {
        return getOk("/globle/api/rollcall/options?region=" + region, who);
    }

    /** The countries a roll call run in this region asks for, in catalogue order, from the data itself. */
    protected List<Country> rollCallPool(Region region) {
        return catalog.all().stream()
                .filter(Country::answer)
                .filter(c -> region.includes(c.region(), c.smallIsland()))
                .toList();
    }

    /** The codes of {@link #rollCallPool} of difficulty {@code tier} or easier. */
    protected List<String> rollCallTiers(Region region, int tier) {
        return rollCallPool(region).stream().filter(c -> c.difficulty() <= tier).map(Country::code).toList();
    }
}

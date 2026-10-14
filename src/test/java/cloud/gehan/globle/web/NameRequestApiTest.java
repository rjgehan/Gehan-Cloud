package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.GlobleNameRequest;
import cloud.gehan.globle.model.Lang;
import cloud.gehan.globle.model.NameRequestSource;
import cloud.gehan.globle.model.NameRequestStatus;
import cloud.gehan.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Request a name change": any player sends one; only the admin lists and resolves
 * them, and anyone else is told those routes are not there; a request the data
 * already answers closes by itself when the admin looks.
 */
class NameRequestApiTest extends GlobleTestBase {

    private static final String SEND = "/globle/api/name-requests";
    private static final List<String> FIELDS = List.of("id", "code", "suggestion", "lang", "source", "username",
            "createdAt", "status", "resolvedAt", "resolvedBy");

    // ------------------------------------------------------------------ sending

    @Test
    void aPlayerSendsARequestAndGetsItBackStored() throws Exception {
        var jen = player("jen");
        JsonNode sent = send(jen, "GBR", "Great Britain and NI", "en", "missed", 201);

        assertThat(fields(sent)).containsExactlyElementsOf(FIELDS);
        assertThat(sent.get("id").asLong()).isPositive();
        assertThat(sent.get("code").asText()).isEqualTo("GBR");
        assertThat(sent.get("suggestion").asText()).isEqualTo("Great Britain and NI");
        assertThat(sent.get("lang").asText()).isEqualTo("en");
        assertThat(sent.get("source").asText()).isEqualTo("missed");
        assertThat(sent.get("username").asText()).isEqualTo("jen");
        assertThat(sent.get("createdAt").asText()).isEqualTo("2026-10-09T16:00:00Z");
        assertThat(sent.get("status").asText()).isEqualTo("OPEN");
        assertThat(sent.get("resolvedAt").isNull()).isTrue();
        assertThat(sent.get("resolvedBy").isNull()).isTrue();

        GlobleNameRequest row = nameRequests.findById(sent.get("id").asLong()).orElseThrow();
        assertThat(row.getUserId()).isEqualTo(idOf("jen"));
        assertThat(row.getUsername()).isEqualTo("jen");
        assertThat(row.getCode()).isEqualTo("GBR");
        assertThat(row.getLang()).isEqualTo(Lang.EN);
        assertThat(row.getSource()).isEqualTo(NameRequestSource.MISSED);
        assertThat(row.getStatus()).isEqualTo(NameRequestStatus.OPEN);
        assertThat(row.getCreatedAt()).isEqualTo(clock.instant());
        // Enums are stored by name, as text.
        assertThat(jdbc.queryForObject("select code || '/' || lang || '/' || source || '/' || status"
                + " from globle_name_request where id = ?", String.class, row.getId())).isEqualTo("GBR/EN/MISSED/OPEN");
        assertThat(jdbc.queryForObject("select count(*) from globle_name_request"
                + " where resolved_at is null and resolved_by is null", Integer.class)).isEqualTo(1);
        // Sending touches no game.
        assertThat(games.count()).isZero();
    }

    @Test
    void everySourceAndLanguageAndAnyPlaceTerritoriesIncludedMaySend() throws Exception {
        var jen = player("jen");
        String[] sources = {"named", "missed", "study", "other"};
        String[] codes = {"KGZ", "NRU", "GRL", "ATA"};
        for (int i = 0; i < sources.length; i++) {
            JsonNode sent = send(jen, codes[i], "Name " + i, i % 2 == 0 ? "es" : "en", sources[i], 201);
            assertThat(sent.get("source").asText()).isEqualTo(sources[i]);
            assertThat(sent.get("code").asText()).isEqualTo(codes[i]);
            assertThat(sent.get("lang").asText()).isEqualTo(i % 2 == 0 ? "es" : "en");
        }
        // Case and surrounding spaces are forgiven in the code, language and source.
        JsonNode loose = send(jen, " fra ", "Frankreich", " ES ", "Study", 201);
        assertThat(loose.get("code").asText()).isEqualTo("FRA");
        assertThat(loose.get("lang").asText()).isEqualTo("es");
        assertThat(loose.get("source").asText()).isEqualTo("study");
        // So may the admin, like any player.
        assertThat(send(admin("boss"), "DEU", "Deutschland", "en", "other", 201).get("username").asText())
                .isEqualTo("boss");
        assertThat(nameRequests.count()).isEqualTo(6);
    }

    @Test
    void theSuggestionIsCleanedBeforeItIsStored() throws Exception {
        var jen = player("jen");
        JsonNode sent = send(jen, "CIV", "  Cote\td'Ivoire \n (Ivory   Coast)  ", "en", "named", 201);
        assertThat(sent.get("suggestion").asText()).isEqualTo("Cote d'Ivoire (Ivory Coast)");
        // Composed and decomposed accents are stored the same way (NFC).
        JsonNode accents = send(jen, "STP", "São Tomé", "es", "named", 201);
        assertThat(accents.get("suggestion").asText()).isEqualTo("São Tomé");
    }

    @Test
    void eightyCharactersIsTheLimitCountedAsCharactersNotUnits() throws Exception {
        var jen = player("jen");
        send(jen, "FRA", "a".repeat(80), "en", "other", 201);
        refused(jen, Map.of("code", "FRA", "suggestion", "b".repeat(81), "lang", "en", "source", "other"),
                "bad-request");
        // 80 letters outside the Basic Multilingual Plane are 160 UTF-16 units, and fit.
        String gothic = "𐌰".repeat(80);
        JsonNode wide = send(jen, "FRA", gothic, "en", "other", 201);
        assertThat(wide.get("suggestion").asText()).isEqualTo(gothic);
        assertThat(nameRequests.findById(wide.get("id").asLong()).orElseThrow().getSuggestion()).isEqualTo(gothic);
        refused(jen, Map.of("code", "FRA", "suggestion", gothic + "𐌰", "lang", "en", "source", "other"),
                "bad-request");
        // Spaces around the text do not count toward the limit.
        send(jen, "FRA", "  " + "c".repeat(80) + "  ", "en", "other", 201);
    }

    @Test
    void everyMalformedRequestIsABadRequestAndStoresNothing() throws Exception {
        var jen = player("jen");
        List<Map<String, Object>> bad = new ArrayList<>();
        bad.add(body("FRA", "", "en", "other"));
        bad.add(body("FRA", "   \n\t ", "en", "other"));
        bad.add(body("FRA", "!!! ...", "en", "other"));
        bad.add(body("FRA", null, "en", "other"));
        bad.add(body("XXX", "France", "en", "other"));
        bad.add(body("", "France", "en", "other"));
        bad.add(body(null, "France", "en", "other"));
        bad.add(body("FRA", "France", "fr", "other"));
        bad.add(body("FRA", "France", "es-MX", "other"));
        bad.add(body("FRA", "France", null, "other"));
        bad.add(body("FRA", "France", "en", "elsewhere"));
        bad.add(body("FRA", "France", "en", null));
        for (Map<String, Object> request : bad) {
            refused(jen, request, "bad-request");
        }
        for (String raw : new String[]{"{not json", "[]", "\"France\""}) {
            postRaw(SEND, raw, jen).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        }
        mvc.perform(MockMvcRequestBuilders.post(SEND).with(jen).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        assertThat(nameRequests.count()).isZero();
    }

    // ------------------------------------------------------------------ the same request twice

    @Test
    void sendingTheSameOpenRequestAgainReturnsItInsteadOfAnother() throws Exception {
        var jen = player("jen");
        JsonNode first = send(jen, "GBR", "Great Britain and NI", "en", "missed", 201);
        long id = first.get("id").asLong();
        clock.advance(Duration.ofMinutes(5));

        // The same as the matcher reads it: case, accents, punctuation, spaces, articles.
        for (String same : new String[]{"Great Britain and NI", "great britain and ni", "GREAT-BRITAIN & NI",
                "  Great   Britain and N.I. ", "the Great Britain and NI", "GreatBritainandNI"}) {
            JsonNode again = send(jen, "GBR", same, "es", "study", 200);
            assertThat(again).as(same).isEqualTo(first);
        }
        assertThat(nameRequests.count()).isEqualTo(1);

        // Another place, another suggestion or another player is another request.
        assertThat(send(jen, "IRL", "Great Britain and NI", "en", "missed", 201).get("id").asLong()).isNotEqualTo(id);
        assertThat(send(jen, "GBR", "Great Britain & Northern Ireland", "en", "missed", 201).get("id").asLong())
                .isNotEqualTo(id);
        assertThat(send(player("colleen"), "GBR", "Great Britain and NI", "en", "missed", 201).get("id").asLong())
                .isNotEqualTo(id);
        assertThat(nameRequests.count()).isEqualTo(4);
    }

    @Test
    void textInAnotherScriptIsComparedAsTypedIgnoringCase() throws Exception {
        var jen = player("jen");
        long id = send(jen, "JPN", "日本", "en", "named", 201).get("id").asLong();
        assertThat(send(jen, "JPN", " 日本 ", "en", "named", 200).get("id").asLong()).isEqualTo(id);
        assertThat(send(jen, "JPN", "日本国", "en", "named", 201).get("id").asLong()).isNotEqualTo(id);
        long greek = send(jen, "GRC", "Ελλάδα", "en", "named", 201).get("id").asLong();
        assertThat(send(jen, "GRC", "ΕΛΛΆΔΑ", "en", "named", 200).get("id").asLong()).isEqualTo(greek);
    }

    @Test
    void aClosedRequestCanBeSentAgainAsANewOne() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long id = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();
        resolve(boss, id, "dismissed");
        long again = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();
        assertThat(again).isNotEqualTo(id);
        assertThat(send(jen, "NRU", "naoero", "en", "missed", 200).get("id").asLong()).isEqualTo(again);
    }

    // ------------------------------------------------------------------ too many

    @Test
    void aPlayerMayHaveThirtyOpenAtOnce() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var boss = admin("boss");
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ids.add(send(jen, "NRU", "Name number " + i, "en", "other", 201).get("id").asLong());
        }
        refused(jen, body("NRU", "Name number 30", "en", "other"), "too-many");
        refused(jen, body("FRA", "Anything", "es", "named"), "too-many");
        assertThat(nameRequests.count()).isEqualTo(30);

        // Sending one of the thirty again is no new request, so it is still answered.
        assertThat(send(jen, "NRU", "name number 7", "en", "other", 200).get("id").asLong()).isEqualTo(ids.get(7));
        // The limit is per player.
        send(colleen, "NRU", "Name number 30", "en", "other", 201);

        // Once the admin closes one, there is room for one more.
        resolve(boss, ids.get(0), "done");
        send(jen, "NRU", "Name number 30", "en", "other", 201);
        refused(jen, body("NRU", "Name number 31", "en", "other"), "too-many");
        // Opening it again puts the player at 31 open: allowed, but nothing more until some close.
        resolve(boss, ids.get(0), "open");
        refused(jen, body("NRU", "Name number 31", "en", "other"), "too-many");
    }

    // ------------------------------------------------------------------ the admin's list

    @Test
    void theAdminListsOpenRequestsNewestFirstOrAllOfThem() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var boss = admin("boss");
        long a = send(jen, "GBR", "Great Britain and NI", "en", "missed", 201).get("id").asLong();
        clock.advance(Duration.ofSeconds(30));
        long b = send(colleen, "KGZ", "Kirgizstan", "es", "named", 201).get("id").asLong();
        clock.advance(Duration.ofSeconds(30));
        long c = send(jen, "NRU", "Naoero", "en", "study", 201).get("id").asLong();
        resolve(boss, b, "dismissed");

        JsonNode open = list(boss, "/globle/api/name-requests");
        assertThat(ids(open)).containsExactly(c, a);
        assertThat(fields(open.get(0))).containsExactlyElementsOf(FIELDS);
        assertThat(open.get(0).get("createdAt").asText()).isEqualTo("2026-10-09T16:01:00Z");
        assertThat(open.get(1).get("username").asText()).isEqualTo("jen");
        assertThat(list(boss, "/globle/api/name-requests?status=open")).isEqualTo(open);
        assertThat(read(mvc.perform(get("/globle/api/name-requests").param("status", " OPEN ").with(boss))
                .andExpect(status().isOk()).andReturn())).isEqualTo(open);

        JsonNode all = list(boss, "/globle/api/name-requests?status=all");
        assertThat(ids(all)).containsExactly(c, b, a);
        JsonNode dismissed = all.get(1);
        assertThat(dismissed.get("status").asText()).isEqualTo("DISMISSED");
        assertThat(dismissed.get("resolvedBy").asText()).isEqualTo("boss");
        assertThat(dismissed.get("username").asText()).isEqualTo("colleen");

        mvc.perform(get("/globle/api/name-requests?status=closed").with(boss))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
    }

    @Test
    void requestsSentAtTheSameMomentListNewestIdFirst() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long first = send(jen, "FRA", "One", "en", "other", 201).get("id").asLong();
        long second = send(jen, "FRA", "Two", "en", "other", 201).get("id").asLong();
        assertThat(ids(list(boss, "/globle/api/name-requests"))).containsExactly(second, first);
    }

    @Test
    void aDeletedPlayersRequestsStayListedUnderTheirName() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        send(jen, "NRU", "Naoero", "en", "missed", 201);
        users.delete(users.findByUsername("jen").orElseThrow());
        JsonNode open = list(boss, "/globle/api/name-requests");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("username").asText()).isEqualTo("jen");
    }

    // ------------------------------------------------------------------ resolving

    @Test
    void theAdminMarksRequestsDoneDismissedOrOpenAgain() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long id = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();
        clock.advance(Duration.ofHours(2));

        JsonNode done = resolve(boss, id, "done");
        assertThat(fields(done)).containsExactlyElementsOf(FIELDS);
        assertThat(done.get("status").asText()).isEqualTo("DONE");
        assertThat(done.get("resolvedAt").asText()).isEqualTo("2026-10-09T18:00:00Z");
        assertThat(done.get("resolvedBy").asText()).isEqualTo("boss");
        assertThat(done.get("suggestion").asText()).isEqualTo("Naoero");
        assertThat(done.get("createdAt").asText()).isEqualTo("2026-10-09T16:00:00Z");

        // Marking it again with the same status keeps the first time and name.
        clock.advance(Duration.ofMinutes(1));
        assertThat(resolve(admin("chief"), id, " DONE ")).isEqualTo(done);

        JsonNode reopened = resolve(boss, id, "open");
        assertThat(reopened.get("status").asText()).isEqualTo("OPEN");
        assertThat(reopened.get("resolvedAt").isNull()).isTrue();
        assertThat(reopened.get("resolvedBy").isNull()).isTrue();

        JsonNode dismissed = resolve(boss, id, "dismissed");
        assertThat(dismissed.get("status").asText()).isEqualTo("DISMISSED");
        assertThat(dismissed.get("resolvedAt").asText()).isEqualTo("2026-10-09T18:01:00Z");
        GlobleNameRequest row = nameRequests.findById(id).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(NameRequestStatus.DISMISSED);
        assertThat(row.getResolvedBy()).isEqualTo("boss");
    }

    @Test
    void theAdminsBadResolvesAreRefusedAndChangeNothing() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long id = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();
        String url = "/globle/api/name-requests/" + id + "/resolve";

        for (String body : new String[]{"{\"status\":\"closed\"}", "{\"status\":\"\"}", "{\"status\":3}", "{}",
                "{not json", "[]", "null", ""}) {
            postRaw(url, body, boss).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        }
        postRaw("/globle/api/name-requests/abc/resolve", "{\"status\":\"done\"}", boss)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"bad-request\"}", true));
        postRaw("/globle/api/name-requests/" + (id + 100) + "/resolve", "{\"status\":\"done\"}", boss)
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        assertThat(nameRequests.findById(id).orElseThrow().getStatus()).isEqualTo(NameRequestStatus.OPEN);
    }

    // ------------------------------------------------------------------ nobody else

    /** A player is told the admin's routes are not there, whatever they send, and nothing changes. */
    @Test
    void aPlayerGetsNotFoundFromTheAdminsRoutes() throws Exception {
        var jen = player("jen");
        long id = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();

        for (String url : new String[]{"/globle/api/name-requests", "/globle/api/name-requests?status=all",
                "/globle/api/name-requests?status=nonsense"}) {
            mvc.perform(get(url).with(jen))
                    .andExpect(status().isNotFound())
                    .andExpect(content().json("{\"error\":\"not-found\"}", true));
        }
        String[][] resolves = {
                {"/globle/api/name-requests/" + id + "/resolve", "{\"status\":\"done\"}"},
                {"/globle/api/name-requests/" + id + "/resolve", "{\"status\":\"nonsense\"}"},
                {"/globle/api/name-requests/" + id + "/resolve", "{not json"},
                {"/globle/api/name-requests/" + id + "/resolve", ""},
                {"/globle/api/name-requests/" + (id + 100) + "/resolve", "{\"status\":\"done\"}"},
                {"/globle/api/name-requests/abc/resolve", "{\"status\":\"done\"}"},
        };
        for (String[] resolve : resolves) {
            postRaw(resolve[0], resolve[1], jen)
                    .andExpect(status().isNotFound())
                    .andExpect(content().json("{\"error\":\"not-found\"}", true));
        }
        assertThat(nameRequests.findById(id).orElseThrow().getStatus()).isEqualTo(NameRequestStatus.OPEN);
    }

    @Test
    void signedOutRequestsGetA401AndPostsNeedTheCsrfToken() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long id = send(jen, "NRU", "Naoero", "en", "missed", 201).get("id").asLong();

        mvc.perform(get("/globle/api/name-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
        String[][] posts = {
                {SEND, "{\"code\":\"FRA\",\"suggestion\":\"Francia\",\"lang\":\"es\",\"source\":\"named\"}"},
                {"/globle/api/name-requests/" + id + "/resolve", "{\"status\":\"done\"}"},
        };
        for (String[] request : posts) {
            mvc.perform(MockMvcRequestBuilders.post(request[0])
                            .contentType(MediaType.APPLICATION_JSON).content(request[1]))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"signed-out\"}", true));
            for (RequestPostProcessor who : List.of(jen, boss)) {
                mvc.perform(MockMvcRequestBuilders.post(request[0]).with(who)
                                .contentType(MediaType.APPLICATION_JSON).content(request[1]))
                        .andExpect(status().isForbidden())
                        .andExpect(content().json("{\"error\":\"forbidden\"}", true));
            }
        }
        assertThat(nameRequests.count()).isEqualTo(1);
        assertThat(nameRequests.findById(id).orElseThrow().getStatus()).isEqualTo(NameRequestStatus.OPEN);
    }

    /**
     * Who is the admin is read from a real sign-in, as /users reads it: an ADMIN account
     * signed in through the login form is the admin here, a USER account is not.
     */
    @Test
    void aRealAdminSignInIsTheAdminAndAPlayersIsNot() throws Exception {
        userService.create("boss", UserService.ROLE_ADMIN);
        userService.create("jen", UserService.ROLE_USER);
        MockHttpSession boss = signIn("boss");
        MockHttpSession jen = signIn("jen");

        mvc.perform(get("/globle/api/name-requests").session(boss)).andExpect(status().isOk());
        mvc.perform(get("/users").session(boss)).andExpect(status().isOk());
        assertThat(read(mvc.perform(get("/globle/api/home").session(boss)).andReturn()).get("admin").asBoolean())
                .isTrue();

        mvc.perform(get("/globle/api/name-requests").session(jen))
                .andExpect(status().isNotFound())
                .andExpect(content().json("{\"error\":\"not-found\"}", true));
        mvc.perform(get("/users").session(jen)).andExpect(status().isForbidden());
        JsonNode home = read(mvc.perform(get("/globle/api/home").session(jen)).andReturn());
        assertThat(home.get("admin").asBoolean()).isFalse();
        assertThat(home.get("nameRequests").isNull()).isTrue();
    }

    /** A session whose account has gone is signed out, admin role or not. */
    @Test
    void aDeletedAccountsSessionIsSignedOut() throws Exception {
        var boss = admin("boss");
        users.delete(users.findByUsername("boss").orElseThrow());
        mvc.perform(get("/globle/api/name-requests").with(boss))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"signed-out\"}", true));
    }

    // ------------------------------------------------------------------ closing by itself

    /**
     * A request whose suggestion the data already names (any name or alias, in either
     * language, as the page matches) is closed as DONE by "data" when the admin looks,
     * so after a data change adds the name nothing needs doing by hand.
     */
    @Test
    void aRequestTheDataAlreadyAnswersClosesWhenTheAdminLooks() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long britain = send(jen, "GBR", "great-britain", "en", "missed", 201).get("id").asLong();
        long kirguistan = send(jen, "KGZ", "  KIRGUISTAN ", "en", "named", 201).get("id").asLong();
        long salvador = send(jen, "SLV", "Salvador", "es", "study", 201).get("id").asLong();
        long usa = send(jen, "USA", "los Estados Unidos", "es", "missed", 201).get("id").asLong();
        long open = send(jen, "GBR", "Great Britain and NI", "en", "missed", 201).get("id").asLong();
        long elsewhere = send(jen, "COD", "Congo", "en", "missed", 201).get("id").asLong();
        long dismissed = send(jen, "FRA", "France", "en", "missed", 201).get("id").asLong();
        resolve(boss, dismissed, "dismissed");
        // Sending does not close anything, and nor does a player's home screen.
        home(jen);
        assertThat(nameRequests.findById(britain).orElseThrow().getStatus()).isEqualTo(NameRequestStatus.OPEN);

        clock.advance(Duration.ofMinutes(10));
        JsonNode list = list(boss, "/globle/api/name-requests");
        // "Congo" names the Republic of the Congo, not DR Congo, so that one stays open.
        assertThat(ids(list)).containsExactly(elsewhere, open);

        JsonNode all = list(boss, "/globle/api/name-requests?status=all");
        Map<Long, JsonNode> byId = new HashMap<>();
        all.forEach(request -> byId.put(request.get("id").asLong(), request));
        for (long closed : new long[]{britain, kirguistan, salvador, usa}) {
            JsonNode request = byId.get(closed);
            assertThat(request.get("status").asText()).as("%d", closed).isEqualTo("DONE");
            assertThat(request.get("resolvedBy").asText()).isEqualTo(GlobleNameRequest.BY_DATA).isEqualTo("data");
            assertThat(request.get("resolvedAt").asText()).isEqualTo("2026-10-09T16:10:00Z");
        }
        // A request the admin dismissed stays dismissed, even though the data names it.
        assertThat(byId.get(dismissed).get("status").asText()).isEqualTo("DISMISSED");
        assertThat(byId.get(dismissed).get("resolvedBy").asText()).isEqualTo("boss");
    }

    @Test
    void theAdminsHomeCountsWhatIsReallyOpen() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        JsonNode empty = home(boss);
        assertThat(empty.get("admin").asBoolean()).isTrue();
        assertThat(empty.get("nameRequests").isInt()).isTrue();
        assertThat(empty.get("nameRequests").asInt()).isZero();

        send(jen, "NRU", "Naoero", "en", "missed", 201);
        send(jen, "KGZ", "Kirghizstan", "en", "missed", 201);
        long answered = send(jen, "DEU", "deutschland", "en", "missed", 201).get("id").asLong();
        long dismissed = send(jen, "FRA", "Frankreich", "en", "missed", 201).get("id").asLong();
        resolve(boss, dismissed, "dismissed");

        assertThat(home(boss).get("nameRequests").asInt()).isEqualTo(2);
        // Counting closed the one the data answers.
        GlobleNameRequest closed = nameRequests.findById(answered).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(NameRequestStatus.DONE);
        assertThat(closed.getResolvedBy()).isEqualTo("data");
    }

    @Test
    void aPlayersHomeSaysNotAdminAndCountsNothing() throws Exception {
        var jen = player("jen");
        long answered = send(jen, "DEU", "deutschland", "en", "missed", 201).get("id").asLong();
        JsonNode home = home(jen);
        assertThat(home.get("admin").asBoolean()).isFalse();
        assertThat(home.has("nameRequests")).isTrue();
        assertThat(home.get("nameRequests").isNull()).isTrue();
        assertThat(nameRequests.findById(answered).orElseThrow().getStatus()).isEqualTo(NameRequestStatus.OPEN);
        assertThat(home.toString()).doesNotContain("deutschland");
    }

    /**
     * The data has names that Name them all still refuses for a country: a part of it
     * ("England" for the United Kingdom), "America" alone, a former name ("Zaire"). A
     * player refused one of those in the game and asking for it is asking for a rule
     * change, so the data having the name settles nothing: the request stays open for
     * the admin, whichever language or spelling it came in.
     */
    @Test
    void aNameThatNameThemAllRefusesNeverClosesByItself() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        List<Long> refused = new ArrayList<>();
        refused.add(send(jen, "GBR", "England", "en", "missed", 201).get("id").asLong());
        refused.add(send(jen, "GBR", "  INGLATERRA ", "es", "named", 201).get("id").asLong());
        refused.add(send(jen, "GBR", "Northern-Ireland", "en", "study", 201).get("id").asLong());
        refused.add(send(jen, "USA", "América", "es", "missed", 201).get("id").asLong());
        refused.add(send(jen, "COD", "Zaire", "en", "missed", 201).get("id").asLong());
        refused.add(send(jen, "PSE", "West Bank", "en", "missed", 201).get("id").asLong());
        refused.add(send(jen, "IRN", "persia", "en", "study", 201).get("id").asLong());
        refused.add(send(jen, "TWN", "Formosa", "en", "missed", 201).get("id").asLong());
        refused.add(send(jen, "KGZ", "Kirghizia", "en", "study", 201).get("id").asLong());
        refused.add(send(jen, "TUV", "Islas Ellice", "es", "missed", 201).get("id").asLong());
        long britain = send(jen, "GBR", "Great Britain", "en", "missed", 201).get("id").asLong();

        assertThat(ids(list(boss, "/globle/api/name-requests"))).containsExactlyInAnyOrderElementsOf(refused);
        assertThat(home(boss).get("nameRequests").asInt()).isEqualTo(refused.size());
        for (long id : refused) {
            assertThat(nameRequests.findById(id).orElseThrow().getStatus()).as("%d", id)
                    .isEqualTo(NameRequestStatus.OPEN);
        }
        // A name the game does count for the country still closes by itself.
        assertThat(nameRequests.findById(britain).orElseThrow().getResolvedBy()).isEqualTo("data");
    }

    /** A request reopened although the data names it closes again at the admin's next look. */
    @Test
    void reopeningARequestTheDataAnswersClosesItAgainOnTheNextLook() throws Exception {
        var jen = player("jen");
        var boss = admin("boss");
        long id = send(jen, "DEU", "Deutschland", "en", "missed", 201).get("id").asLong();
        assertThat(resolve(boss, id, "open").get("status").asText()).isEqualTo("OPEN");
        assertThat(list(boss, "/globle/api/name-requests")).isEmpty();
        assertThat(nameRequests.findById(id).orElseThrow().getResolvedBy()).isEqualTo("data");
    }

    // ------------------------------------------------------------------ helpers

    /** Signs in through the login form (the first sign-in sets the password) and returns the session. */
    private MockHttpSession signIn(String username) throws Exception {
        MvcResult login = mvc.perform(formLogin().user(username).password("first-login"))
                .andExpect(authenticated().withUsername(username))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private JsonNode send(RequestPostProcessor who, String code, String suggestion, String lang, String source,
                          int expected) throws Exception {
        return read(post(SEND, body(code, suggestion, lang, source), who)
                .andExpect(status().is(expected)).andReturn());
    }

    private static Map<String, Object> body(String code, String suggestion, String lang, String source) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("suggestion", suggestion);
        body.put("lang", lang);
        body.put("source", source);
        return body;
    }

    private void refused(RequestPostProcessor who, Map<String, Object> request, String code) throws Exception {
        long before = nameRequests.count();
        post(SEND, request, who)
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"" + code + "\"}", true));
        assertThat(nameRequests.count()).as("%s", request).isEqualTo(before);
    }

    private JsonNode resolve(RequestPostProcessor who, long id, String status) throws Exception {
        return read(postRaw("/globle/api/name-requests/" + id + "/resolve", "{\"status\":\"" + status + "\"}", who)
                .andExpect(status().isOk()).andReturn());
    }

    private JsonNode list(RequestPostProcessor who, String url) throws Exception {
        JsonNode list = getOk(url, who);
        assertThat(list.isArray()).isTrue();
        return list;
    }

    /** A POST whose body is sent exactly as given. */
    private ResultActions postRaw(String url, String body, RequestPostProcessor who) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(url).with(who).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static List<Long> ids(JsonNode list) {
        return StreamSupport.stream(list.spliterator(), false).map(n -> n.get("id").asLong()).toList();
    }

    private static List<String> fields(JsonNode node) {
        List<String> fields = new ArrayList<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}

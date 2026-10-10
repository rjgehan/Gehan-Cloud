package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.model.Variant;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** Classic and Hard are separate dailies with separate boards; practice has three boards. */
class LeaderboardVariantTest extends GlobleTestBase {

    @Test
    void classicAndHardDailiesHaveSeparateBoards() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        var ryan = player("ryan");
        winDailyIn(jen, 2);
        winDailyIn(colleen, 3, Variant.HARD);
        giveUpDaily(ryan, Variant.HARD);

        JsonNode classic = board(jen, Variant.CLASSIC);
        assertThat(classic.get("variant").asText()).isEqualTo("classic");
        assertThat(names(classic.get("today").get("entries"))).containsExactly("jen");
        assertThat(classic.get("today").get("viewerFinished").asBoolean()).isTrue();
        assertThat(names(classic.get("week"))).containsExactly("jen");
        assertThat(names(classic.get("allTime"))).containsExactly("jen");
        assertThat(classic.get("me").get("wins").asInt()).isEqualTo(1);

        JsonNode hard = board(jen, Variant.HARD);
        assertThat(hard.get("variant").asText()).isEqualTo("hard");
        assertThat(hard.get("today").get("puzzle").asInt()).isEqualTo(1);
        assertThat(names(hard.get("today").get("entries"))).containsExactly("colleen", "ryan");
        assertThat(hard.get("today").get("entries").get(0).get("rank").asInt()).isEqualTo(1);
        assertThat(hard.get("today").get("entries").get(1).get("status").asText()).isEqualTo("GAVE_UP");
        assertThat(hard.get("today").get("viewerFinished").asBoolean()).isFalse();
        assertThat(names(hard.get("allTime"))).containsExactly("colleen", "ryan");
        assertThat(hard.get("me").get("played").asInt()).isZero();

        assertThat(board(colleen, Variant.HARD).get("me").get("wins").asInt()).isEqualTo(1);
        assertThat(board(colleen, Variant.CLASSIC).get("me").get("played").asInt()).isZero();
    }

    @Test
    void aBoardWithoutAVariantIsClassic() throws Exception {
        var jen = player("jen");
        winDailyIn(jen, 2);
        winDailyIn(jen, 5, Variant.HARD);
        assertThat(board(jen)).isEqualTo(board(jen, Variant.CLASSIC));
        assertThat(board(jen).get("me").get("distribution").get("2").asInt()).isEqualTo(1);
        assertThat(board(jen).get("me").get("distribution").get("5").asInt()).isZero();
    }

    @Test
    void streaksAndDistributionAreKeptPerVariant() throws Exception {
        var jen = player("jen");
        int[] hardCounts = {4, 8, 15};
        for (int puzzle = 1; puzzle <= 3; puzzle++) {
            clock.setDay(day(puzzle));
            winDailyIn(jen, hardCounts[puzzle - 1], Variant.HARD);
            if (puzzle == 2) {
                giveUpDaily(jen);
            } else {
                winDailyIn(jen, 2);
            }
        }

        JsonNode hard = board(jen, Variant.HARD).get("me");
        assertThat(hard.get("played").asInt()).isEqualTo(3);
        assertThat(hard.get("wins").asInt()).isEqualTo(3);
        assertThat(hard.get("currentStreak").asInt()).isEqualTo(3);
        assertThat(hard.get("maxStreak").asInt()).isEqualTo(3);
        assertThat(hard.get("distribution").get("4").asInt()).isEqualTo(1);
        assertThat(hard.get("distribution").get("7-9").asInt()).isEqualTo(1);
        assertThat(hard.get("distribution").get("15+").asInt()).isEqualTo(1);
        assertThat(hard.get("avgGuesses").asDouble()).isEqualTo(9.0);

        JsonNode classic = board(jen, Variant.CLASSIC).get("me");
        assertThat(classic.get("played").asInt()).isEqualTo(3);
        assertThat(classic.get("wins").asInt()).isEqualTo(2);
        assertThat(classic.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(classic.get("distribution").get("2").asInt()).isEqualTo(2);
        assertThat(classic.get("distribution").get("15+").asInt()).isZero();
    }

    /**
     * Countries is every classic country game, whatever region or preset, Mainland
     * included; Capitals is capitals; Hard is Hard practice. The same whichever daily
     * the request asked about.
     */
    @Test
    void practiceHasCountriesCapitalsAndHardBoards() throws Exception {
        var jen = player("jen");
        var colleen = player("colleen");
        winUnlimitedIn(jen, "country", "world", Variant.CLASSIC, 3);
        winUnlimitedIn(jen, "country", "europe", Variant.CLASSIC, 4);
        winUnlimitedIn(jen, "country", "mainland", Variant.CLASSIC, 5);
        winUnlimitedIn(jen, "capital", "world", Variant.CLASSIC, 2);
        winUnlimitedIn(jen, "country", "world", Variant.HARD, 2);
        winUnlimitedIn(colleen, "country", "world", Variant.HARD, 6);
        newGame(jen, "country", "world", Variant.HARD);   // in progress: not counted

        JsonNode unlimited = board(jen).get("unlimited");
        List<String> boards = new ArrayList<>();
        unlimited.fieldNames().forEachRemaining(boards::add);
        assertThat(boards).containsExactly("country", "capital", "hard");

        JsonNode country = only(unlimited.get("country"));
        assertThat(country.get("played").asInt()).isEqualTo(3);
        assertThat(country.get("avgGuesses").asDouble()).isEqualTo(4.0);
        assertThat(country.get("best").asInt()).isEqualTo(3);
        assertThat(only(unlimited.get("capital")).get("played").asInt()).isEqualTo(1);

        JsonNode hard = unlimited.get("hard");
        assertThat(names(hard)).containsExactly("jen", "colleen");
        assertThat(hard.get(0).get("played").asInt()).isEqualTo(1);
        assertThat(hard.get(0).get("best").asInt()).isEqualTo(2);
        assertThat(hard.get(0).get("you").asBoolean()).isTrue();

        assertThat(board(jen, Variant.HARD).get("unlimited")).isEqualTo(unlimited);
        // Practice never feeds either daily board.
        assertThat(board(jen, Variant.HARD).get("allTime")).isEmpty();
        assertThat(board(jen, Variant.CLASSIC).get("allTime")).isEmpty();
    }

    // ------------------------------------------------------------------ helpers

    private void winUnlimitedIn(RequestPostProcessor who, String kind, String region, Variant variant, int guesses)
            throws Exception {
        long id = newGame(who, kind, region, variant).get("id").asLong();
        String answer = answerOf(id);
        List<String> wrong = catalog.all().stream()
                .filter(c -> !c.code().equals(answer) && c.hasCapital())
                .map(c -> c.code())
                .limit(guesses - 1L)
                .toList();
        for (String code : wrong) {
            guessUnlimited(who, id, code);
        }
        assertThat(guessUnlimited(who, id, answer).get("status").asText()).isEqualTo("WON");
    }

    private static List<String> names(JsonNode rows) {
        return StreamSupport.stream(rows.spliterator(), false).map(r -> r.get("username").asText()).toList();
    }

    private static JsonNode only(JsonNode rows) {
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }
}

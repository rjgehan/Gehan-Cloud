package cloud.gehan.globle.service;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.GlobleDay;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class DailyPuzzlesTest extends GlobleTestBase {

    @Autowired
    private GlobleClock globleClock;

    @Test
    void theAnswerIsFixedForTheWholeDay() {
        clock.set(Instant.parse("2026-10-09T04:30:00Z"));   // 00:30 in New York
        String early = answerToday();
        clock.set(Instant.parse("2026-10-10T03:30:00Z"));   // 23:30 the same New York day
        String late = answerToday();

        assertThat(late).isEqualTo(early);
        assertThat(days.count()).isEqualTo(1);
        GlobleDay stored = days.findById("2026-10-09").orElseThrow();
        assertThat(stored.getCountryCode()).isEqualTo(early);
        assertThat(stored.getPuzzleNumber()).isEqualTo(1);
    }

    @Test
    void theAnswerIsAlwaysOneThatCanBeTheMystery() {
        String answer = puzzles.answerFor(DAY_ONE);
        assertThat(catalog.require(answer).answer()).isTrue();
    }

    @Test
    void puzzleNumbersCountDaysFromTheEpoch() {
        assertThat(puzzles.puzzleNumber(LocalDate.of(2026, 10, 9))).isEqualTo(1);
        assertThat(puzzles.puzzleNumber(LocalDate.of(2026, 10, 10))).isEqualTo(2);
        assertThat(puzzles.puzzleNumber(LocalDate.of(2027, 10, 9))).isEqualTo(366);
    }

    /**
     * Every finished game reveals its answer, so the set of countries a day can draw
     * from must stay large, or a player keeping a list could narrow it down. A 365-day
     * window used to leave one candidate on day 197 and fifteen on every day after. The
     * window is capped at a third of the answers, so even when configured that long the
     * draw is always among well over a hundred, and still never repeats within the cap.
     */
    @Test
    void theDayIsAlwaysDrawnFromALargePool() {
        GlobleProperties longWindow = new GlobleProperties();
        longWindow.setAnswerRepeatWindowDays(365);
        List<Integer> bounds = new ArrayList<>();
        Random recording = new Random(7) {
            @Override
            public int nextInt(int bound) {
                bounds.add(bound);
                return super.nextInt(bound);
            }
        };
        DailyPuzzles daily = new DailyPuzzles(days, catalog, longWindow, globleClock, recording);
        int window = catalog.answerable(Region.WORLD, Kind.COUNTRY).size() / 3;

        List<String> answers = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            answers.add(daily.answerFor(DAY_ONE.plusDays(i)));
        }

        assertThat(bounds).hasSize(400).allSatisfy(bound -> assertThat(bound).isGreaterThanOrEqualTo(100));
        for (int i = 0; i < answers.size(); i++) {
            for (int j = i + 1; j < answers.size() && j - i <= window; j++) {
                assertThat(answers.get(j)).as("days %d and %d", i, j).isNotEqualTo(answers.get(i));
            }
        }
    }

    /** With the default window no answer comes back within 60 days either. */
    @Test
    void theDefaultWindowKeepsTwoMonthsBetweenRepeats() {
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            answers.add(puzzles.answerFor(DAY_ONE.plusDays(i)));
        }
        for (int i = 0; i < answers.size(); i++) {
            for (int j = i + 1; j < answers.size() && j - i <= 60; j++) {
                assertThat(answers.get(j)).as("days %d and %d", i, j).isNotEqualTo(answers.get(i));
            }
        }
    }

    @Test
    void neighbouringDatesFilledOutOfOrderStillDoNotRepeat() {
        String later = puzzles.answerFor(DAY_ONE.plusDays(1));
        String earlier = puzzles.answerFor(DAY_ONE);
        assertThat(earlier).isNotEqualTo(later);
    }

    /** Everyone guessing at the first moment of a day must still share one answer. */
    @Test
    void simultaneousFirstRequestsAgree() throws Exception {
        LocalDate date = DAY_ONE.plusDays(3);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> puzzles.answerFor(date));
            }
            Set<String> seen = new HashSet<>();
            for (Future<String> f : pool.invokeAll(calls)) {
                seen.add(f.get());
            }
            assertThat(seen).hasSize(1);
            assertThat(days.findById(date.toString())).isPresent();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theDrawIsNotTheSameEveryTime() {
        // Not a statistical test, just a guard against something deterministic sneaking
        // in: 20 fresh draws for the same date would all agree with odds of ~1e-45.
        Set<String> draws = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            draws.add(puzzles.draw(DAY_ONE.plusDays(500)));
        }
        assertThat(draws.size()).isGreaterThan(1);
        assertThat(draws).allMatch(code -> catalog.find(code).map(Country::answer).orElse(false));
    }

    @Test
    void theClockMovesAcrossMidnightInThePuzzleZone() throws Exception {
        var jen = player("jen");
        clock.set(Instant.parse("2026-10-10T03:59:00Z"));   // 23:59 EDT on the 9th
        assertThat(getOk("/globle/api/daily", jen).get("date").asText()).isEqualTo("2026-10-09");
        clock.advance(Duration.ofMinutes(1));                // midnight in New York
        assertThat(getOk("/globle/api/daily", jen).get("date").asText()).isEqualTo("2026-10-10");
        assertThat(getOk("/globle/api/daily", jen).get("puzzle").asInt()).isEqualTo(2);
    }
}

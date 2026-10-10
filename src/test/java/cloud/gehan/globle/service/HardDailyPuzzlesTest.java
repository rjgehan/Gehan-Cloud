package cloud.gehan.globle.service;

import cloud.gehan.globle.GlobleTestBase;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.GlobleHardDay;
import cloud.gehan.globle.model.Variant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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

/** The Hard daily's answer: drawn once, stored on its own, and never the classic one. */
class HardDailyPuzzlesTest extends GlobleTestBase {

    @Autowired
    private GlobleClock globleClock;

    @Autowired
    private GlobleProperties properties;

    @Autowired
    private AnswerPools pools;

    @Test
    void theHardAnswerIsFixedForTheWholeDayAndStoredInItsOwnTable() {
        clock.set(Instant.parse("2026-10-09T04:30:00Z"));   // 00:30 in New York
        String early = answerToday(Variant.HARD);
        clock.set(Instant.parse("2026-10-10T03:30:00Z"));   // 23:30 the same New York day
        String late = answerToday(Variant.HARD);

        assertThat(late).isEqualTo(early);
        GlobleHardDay stored = hardDays.findById("2026-10-09").orElseThrow();
        assertThat(stored.getCountryCode()).isEqualTo(early);
        assertThat(stored.getPuzzleNumber()).isEqualTo(1);
        assertThat(hardDays.count()).isEqualTo(1);
        // The classic answer was settled first, so the two could be kept apart.
        assertThat(days.findById("2026-10-09")).isPresent();
        assertThat(days.findById("2026-10-09").orElseThrow().getCountryCode()).isNotEqualTo(early);
    }

    @Test
    void aClassicAnswerAlreadyDrawnIsTheOneHardAvoids() {
        String classic = puzzles.answerFor(DAY_ONE);
        String hard = puzzles.answerFor(Variant.HARD, DAY_ONE);
        assertThat(hard).isNotEqualTo(classic);
        assertThat(puzzles.answerFor(DAY_ONE)).isEqualTo(classic);
        assertThat(days.count()).isEqualTo(1);
    }

    @Test
    void hardIsNeverTheClassicAnswerAndNeverATierOneCountry() {
        for (int i = 0; i < 200; i++) {
            LocalDate date = DAY_ONE.plusDays(i);
            String hard = puzzles.answerFor(Variant.HARD, date);
            assertThat(hard).as("day %d", i).isNotEqualTo(puzzles.answerFor(date));
            assertThat(catalog.require(hard).answer()).isTrue();
            assertThat(catalog.require(hard).difficulty()).as("day %d drew %s", i, hard).isGreaterThan(1);
        }
    }

    /** Hard keeps its own window of min(60, 167 / 3) = 55 days. */
    @Test
    void hardHasItsOwnNoRepeatWindow() {
        int window = Math.min(properties.getAnswerRepeatWindowDays(), pools.daily(Variant.HARD).size() / 3);
        assertThat(window).isEqualTo(55);
        List<String> answers = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            answers.add(puzzles.answerFor(Variant.HARD, DAY_ONE.plusDays(i)));
        }
        for (int i = 0; i < answers.size(); i++) {
            for (int j = i + 1; j < answers.size() && j - i <= window; j++) {
                assertThat(answers.get(j)).as("days %d and %d", i, j).isNotEqualTo(answers.get(i));
            }
        }
    }

    /**
     * With a "random" that always picks the first allowed country the draws are
     * predictable, which shows exactly what each draw leaves out. Day 1: classic takes
     * the first answer, Afghanistan; Hard must skip it and takes Albania. Day 2: classic
     * has used Afghanistan, so takes Albania; Hard has used Albania and must skip
     * classic's Albania, and takes Afghanistan. So Hard avoids the day's classic answer,
     * but classic's other days are no business of Hard's: the windows are separate.
     */
    @Test
    void eachDrawLeavesOutItsOwnRecentAnswersAndTheDaysClassicAnswer() {
        Random first = new Random() {
            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
        DailyPuzzles rigged = new DailyPuzzles(days, hardDays, pools, properties, globleClock, first);

        assertThat(rigged.answerFor(DAY_ONE)).isEqualTo("AFG");
        assertThat(rigged.answerFor(Variant.HARD, DAY_ONE)).isEqualTo("ALB");
        assertThat(rigged.answerFor(day(2))).isEqualTo("ALB");
        assertThat(rigged.answerFor(Variant.HARD, day(2))).isEqualTo("AFG");
    }

    @Test
    void aHardDrawNeverReturnsTheClassicAnswerItIsGiven() {
        // Tuvalu is tier 5 and drawn about 1% of the time: 2,000 draws would hit it ~20 times.
        for (int i = 0; i < 2_000; i++) {
            assertThat(puzzles.drawHard(DAY_ONE.plusDays(900), "TUV")).isNotEqualTo("TUV");
        }
    }

    /** Everyone starting Hard at the first moment of a day must still share one answer. */
    @Test
    void simultaneousFirstHardRequestsAgree() throws Exception {
        LocalDate date = DAY_ONE.plusDays(3);
        ExecutorService threads = Executors.newFixedThreadPool(8);
        try {
            List<Callable<String>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                Variant variant = i % 2 == 0 ? Variant.HARD : Variant.CLASSIC;
                calls.add(() -> variant + ":" + puzzles.answerFor(variant, date));
            }
            Set<String> seen = new HashSet<>();
            for (Future<String> f : threads.invokeAll(calls)) {
                seen.add(f.get());
            }
            assertThat(seen).hasSize(2);
            assertThat(hardDays.findById(date.toString())).isPresent();
            assertThat(days.findById(date.toString())).isPresent();
            assertThat(hardDays.findById(date.toString()).orElseThrow().getCountryCode())
                    .isNotEqualTo(days.findById(date.toString()).orElseThrow().getCountryCode());
        } finally {
            threads.shutdownNow();
        }
    }
}

package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Variant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Which countries each game can draw, against the shipped data. The statistical
 * checks use a fixed seed, so they are exact and repeatable rather than flaky; the
 * tolerances are what any honest seed would meet.
 */
class AnswerPoolsTest {

    private static CountryCatalog catalog;
    private static AnswerPools pools;

    @BeforeAll
    static void load() {
        catalog = new CountryCatalog();
        pools = new AnswerPools(catalog, new GlobleProperties());
    }

    @Test
    void classicDrawsFromEveryAnswerAndHardLeavesOutTheFamousOnes() {
        assertThat(pools.daily(Variant.CLASSIC).size()).isEqualTo(197);
        assertThat(pools.daily(Variant.CLASSIC).countries()).extracting(Country::code).contains("USA", "FRA");

        AnswerPools.Pool hard = pools.daily(Variant.HARD);
        long tierOne = catalog.answerable(Region.WORLD, Kind.COUNTRY).stream()
                .filter(c -> c.difficulty() == 1).count();
        assertThat(hard.size()).isEqualTo(197 - tierOne).isEqualTo(167);
        assertThat(hard.countries()).allSatisfy(c -> assertThat(c.difficulty()).isGreaterThan(1));
        assertThat(hard.countries()).extracting(Country::code).doesNotContain("USA", "FRA", "CHN", "BRA", "JPN");
        assertThat(hard.weight("TUV")).isEqualTo(8);
        assertThat(hard.weight("USA")).isZero();
    }

    /** Every region's Hard pool, Mainland included, is that region without tier 1. */
    @Test
    void noHardPoolInAnyRegionHasATierOneCountry() {
        for (Region region : Region.values()) {
            AnswerPools.Pool hard = pools.pool(Kind.COUNTRY, region, Variant.HARD);
            assertThat(hard.size()).as(region.api()).isPositive();
            assertThat(hard.countries()).as(region.api())
                    .allSatisfy(c -> {
                        assertThat(c.difficulty()).isGreaterThan(1);
                        assertThat(region.includes(c.region(), c.smallIsland())).isTrue();
                    });
        }
    }

    /**
     * The default weights, 0/1/3/6/8 by tier, over 40/45/45/37 countries in tiers 2-5,
     * give tier 5 about 40% of Hard draws and tier 2 about 5%. 30,000 draws land within
     * a percentage point of that, and never on tier 1.
     */
    @Test
    void hardDrawsAreSkewedTowardObscureCountries() {
        AnswerPools.Pool hard = pools.daily(Variant.HARD);
        Map<Integer, Double> expected = new HashMap<>();
        int total = hard.countries().stream().mapToInt(c -> hard.weight(c.code())).sum();
        for (Country c : hard.countries()) {
            expected.merge(c.difficulty(), (double) hard.weight(c.code()) / total, Double::sum);
        }

        int draws = 30_000;
        Random random = new Random(20261009);
        Map<Integer, Integer> byTier = new HashMap<>();
        double hardMean = 0;
        for (int i = 0; i < draws; i++) {
            Country drawn = hard.draw(random, c -> true).orElseThrow();
            byTier.merge(drawn.difficulty(), 1, Integer::sum);
            hardMean += drawn.difficulty();
        }
        hardMean /= draws;

        assertThat(byTier).doesNotContainKey(1);
        for (int tier = 2; tier <= 5; tier++) {
            double share = byTier.getOrDefault(tier, 0) / (double) draws;
            assertThat(share).as("tier %d share", tier).isCloseTo(expected.get(tier), within(0.01));
        }
        assertThat(byTier.get(5)).isGreaterThan(byTier.get(4)).isGreaterThan(byTier.get(3))
                .isGreaterThan(byTier.get(2));
        assertThat(expected.get(4) + expected.get(5)).isGreaterThan(0.7);

        double classicMean = pools.daily(Variant.CLASSIC).countries().stream()
                .mapToInt(Country::difficulty).average().orElseThrow();
        assertThat(hardMean).isGreaterThan(classicMean + 0.8);
    }

    /** Classic stays even: every answer turns up, none much more than another. */
    @Test
    void classicDrawsAreEven() {
        AnswerPools.Pool classic = pools.daily(Variant.CLASSIC);
        Random random = new Random(7);
        Map<String, Integer> seen = new HashMap<>();
        int perCountry = 200;
        for (int i = 0; i < classic.size() * perCountry; i++) {
            seen.merge(classic.draw(random, c -> true).orElseThrow().code(), 1, Integer::sum);
        }
        assertThat(seen).hasSize(197);
        // Binomial with mean 200 and sd about 14: five sd either side.
        assertThat(seen.values()).allSatisfy(n -> assertThat(n).isBetween(130, 270));
    }

    @Test
    void mainlandNeverDrawsASmallIslandNation() {
        for (Variant variant : Variant.values()) {
            for (Kind kind : Kind.values()) {
                AnswerPools.Pool mainland = pools.pool(kind, Region.MAINLAND, variant);
                assertThat(mainland.countries()).as("%s %s", variant, kind).noneMatch(Country::smallIsland);
            }
        }
        AnswerPools.Pool mainland = pools.pool(Kind.COUNTRY, Region.MAINLAND, Variant.CLASSIC);
        assertThat(mainland.size()).isEqualTo(166);
        assertThat(mainland.countries()).extracting(Country::code)
                .contains("JPN", "GBR", "NZL", "AUS", "AND", "VAT")
                .doesNotContain("TON", "FJI", "JAM", "MLT", "SGP");

        Random random = new Random(11);
        for (int i = 0; i < 5_000; i++) {
            assertThat(mainland.draw(random, c -> true).orElseThrow().smallIsland()).isFalse();
        }
    }

    @Test
    void aDrawOnlyPicksWhatTheFilterAllows() {
        AnswerPools.Pool hard = pools.daily(Variant.HARD);
        Random random = new Random(3);
        for (int i = 0; i < 2_000; i++) {
            assertThat(hard.draw(random, c -> !c.code().equals("TUV")).orElseThrow().code()).isNotEqualTo("TUV");
        }
        assertThat(hard.draw(random, c -> c.code().equals("TUV"))).map(Country::code).contains("TUV");
        assertThat(hard.draw(random, c -> false)).isEmpty();
        // A filter that lets nothing with weight through is empty too: USA is not in Hard.
        assertThat(hard.draw(random, c -> c.code().equals("USA"))).isEmpty();
    }

    @Test
    void theWeightsAreConfigurable() {
        GlobleProperties easyOnly = new GlobleProperties();
        easyOnly.setHardWeights(Map.of(1, 1, 2, 0, 3, 0, 4, 0, 5, 0));
        AnswerPools custom = new AnswerPools(catalog, easyOnly);
        List<Country> hard = custom.daily(Variant.HARD).countries();
        assertThat(hard).hasSize(30).allSatisfy(c -> assertThat(c.difficulty()).isEqualTo(1));
        // Classic is untouched by Hard's weights.
        assertThat(custom.daily(Variant.CLASSIC).size()).isEqualTo(197);
    }

    /** Oceania has no tier-2 country, so weighting only tier 2 would leave it nothing. */
    @Test
    void aRegionTheWeightsWouldEmptyFallsBackToEveryAnswerThere() {
        GlobleProperties tierTwoOnly = new GlobleProperties();
        tierTwoOnly.setHardWeights(Map.of(2, 1));
        AnswerPools custom = new AnswerPools(catalog, tierTwoOnly);

        assertThat(custom.pool(Kind.COUNTRY, Region.OCEANIA, Variant.HARD).countries())
                .containsExactlyElementsOf(catalog.answerable(Region.OCEANIA, Kind.COUNTRY));
        assertThat(custom.daily(Variant.HARD).countries())
                .isNotEmpty().allSatisfy(c -> assertThat(c.difficulty()).isEqualTo(2));
    }
}

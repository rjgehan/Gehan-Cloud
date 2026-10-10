package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.config.GlobleProperties;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.model.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Which countries a game can draw as its mystery, and how likely each one is.
 *
 * <p><b>Classic</b> draws evenly from the catalogue's answers for the kind and region.
 *
 * <p><b>Hard</b> draws from the same answers weighted by difficulty tier, using
 * {@code globle.hard-weights} (by default tier 1 never, then 1, 3, 6 and 8). With the
 * shipped data that leaves 167 of the 197 answers in the world pool, and puts about
 * three in four of a single draw on tier 4 or 5 (about seven in ten Hard dailies, after
 * the no-repeat window). A region filter applies first, so a Hard game in Europe is
 * Europe's hardest. If the weights leave a region with nothing to draw, that region's
 * Hard pool falls back to its classic pool rather than a game that cannot start.
 *
 * <p>Built once at startup; the pools are immutable.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnswerPools {

    private static final Logger log = LoggerFactory.getLogger(AnswerPools.class);

    private final Map<Variant, Map<Kind, Map<Region, Pool>>> pools = new EnumMap<>(Variant.class);

    public AnswerPools(CountryCatalog catalog, GlobleProperties properties) {
        for (Variant variant : Variant.values()) {
            Map<Kind, Map<Region, Pool>> byKind = new EnumMap<>(Kind.class);
            for (Kind kind : Kind.values()) {
                Map<Region, Pool> byRegion = new EnumMap<>(Region.class);
                for (Region region : Region.values()) {
                    List<Country> answers = catalog.answerable(region, kind);
                    Pool pool = variant == Variant.HARD ? Pool.weighted(answers, properties) : Pool.even(answers);
                    if (pool.size() == 0) {
                        log.warn("globle.hard-weights leave no Hard {} answer in {}; using every answer there",
                                kind.api(), region.api());
                        pool = Pool.even(answers);
                    }
                    byRegion.put(region, pool);
                }
                byKind.put(kind, Collections.unmodifiableMap(byRegion));
            }
            pools.put(variant, Collections.unmodifiableMap(byKind));
        }
        log.info("Globle Hard pool: {} of {} answers", daily(Variant.HARD).size(), daily(Variant.CLASSIC).size());
    }

    public Pool pool(Kind kind, Region region, Variant variant) {
        return pools.get(variant).get(kind).get(region);
    }

    /** A daily's pool: any country in the world, weighted if Hard. */
    public Pool daily(Variant variant) {
        return pool(Kind.COUNTRY, Region.WORLD, variant);
    }

    /** Countries with a positive weight each, in catalogue order. */
    public static final class Pool {

        private final List<Country> countries;
        private final int[] weights;

        private Pool(List<Country> countries, int[] weights) {
            this.countries = List.copyOf(countries);
            this.weights = weights;
        }

        static Pool even(List<Country> answers) {
            int[] weights = new int[answers.size()];
            Arrays.fill(weights, 1);
            return new Pool(answers, weights);
        }

        static Pool weighted(List<Country> answers, GlobleProperties properties) {
            List<Country> members = new ArrayList<>();
            List<Integer> weights = new ArrayList<>();
            for (Country country : answers) {
                int weight = properties.hardWeight(country.difficulty());
                if (weight > 0) {
                    members.add(country);
                    weights.add(weight);
                }
            }
            return new Pool(members, weights.stream().mapToInt(Integer::intValue).toArray());
        }

        public List<Country> countries() {
            return countries;
        }

        public int size() {
            return countries.size();
        }

        /** The country's weight in this pool; 0 if it is not in it. */
        public int weight(String code) {
            for (int i = 0; i < countries.size(); i++) {
                if (countries.get(i).code().equals(code)) {
                    return weights[i];
                }
            }
            return 0;
        }

        /**
         * One country, chosen by weight among those {@code allowed} lets through; empty
         * when it lets none through. One call to {@code random.nextInt}, with the
         * allowed weights' total as its bound: for an even pool that is a uniform pick
         * among the allowed countries, exactly as before Hard existed.
         */
        public Optional<Country> draw(Random random, Predicate<Country> allowed) {
            int total = 0;
            for (int i = 0; i < countries.size(); i++) {
                if (allowed.test(countries.get(i))) {
                    total += weights[i];
                }
            }
            if (total == 0) {
                return Optional.empty();
            }
            int ticket = random.nextInt(total);
            for (int i = 0; i < countries.size(); i++) {
                if (allowed.test(countries.get(i))) {
                    ticket -= weights[i];
                    if (ticket < 0) {
                        return Optional.of(countries.get(i));
                    }
                }
            }
            throw new IllegalStateException("weights changed during a draw");
        }
    }
}

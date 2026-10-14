package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which countries a roll call run ("Name them all") asks for, per region.
 *
 * <p>A run's pool is the catalogue's answer countries in its region, by the same
 * {@link Region#includes} rule a practice game draws by: 197 in the world, a
 * continent's own, and for Mainland the world without the small island nations.
 * Territories are never in a pool; Greenland is not a country to name. The pools are
 * built once at startup, in catalogue order, and never change.
 *
 * <p>The difficulty tiers the easy autofills read stay here, on the server: a client
 * only ever learns which codes a run filled in.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RollCallPools {

    /** The continents a run's progress is broken down by, in the order the client shows them. */
    public static final List<String> CONTINENTS = List.of("africa", "americas", "asia", "europe", "oceania");

    /** "easy" fills in tier 1, the household names. */
    static final int EASY_TIER = Country.EASIEST;
    /** "known" fills in tiers 1 and 2: well known as well. */
    static final int KNOWN_TIER = Country.EASIEST + 1;

    private final Map<Region, Pool> pools = new EnumMap<>(Region.class);

    public RollCallPools(CountryCatalog catalog) {
        for (Region region : Region.values()) {
            pools.put(region, new Pool(catalog.answerable(region, Kind.COUNTRY)));
        }
    }

    public Pool pool(Region region) {
        return pools.get(region);
    }

    /** One region's countries to name, in catalogue order. */
    public static final class Pool {

        private final List<Country> countries;
        private final Set<String> codes;

        private Pool(List<Country> countries) {
            this.countries = List.copyOf(countries);
            Set<String> codes = new LinkedHashSet<>();
            countries.forEach(country -> codes.add(country.code()));
            this.codes = Collections.unmodifiableSet(codes);
        }

        public int size() {
            return countries.size();
        }

        public boolean contains(String code) {
            return codes.contains(code);
        }

        /** Every code, in catalogue order. */
        public Set<String> codes() {
            return codes;
        }

        public List<Country> countries() {
            return countries;
        }

        /** Whether {@code named} holds every country of the pool: nothing would be left to name. */
        public boolean coveredBy(Collection<String> named) {
            return new HashSet<>(named).containsAll(codes);
        }

        /** The codes of difficulty {@code tier} or easier, in catalogue order. */
        public List<String> upToTier(int tier) {
            return countries.stream()
                    .filter(country -> country.difficulty() <= tier)
                    .map(Country::code)
                    .toList();
        }
    }
}

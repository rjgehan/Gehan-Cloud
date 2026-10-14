package cloud.gehan.globle.service;

import cloud.gehan.globle.catalog.Country;
import cloud.gehan.globle.catalog.CountryCatalog;
import cloud.gehan.globle.catalog.GreatCircle;
import cloud.gehan.globle.model.GlobleGame;
import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Player;
import cloud.gehan.globle.model.Region;
import cloud.gehan.globle.repository.GlobleGameRepository;
import cloud.gehan.globle.view.RollCallStudy;
import cloud.gehan.model.User;
import cloud.gehan.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * "Study what you missed": the countries a player's latest finished "Name them all"
 * run in a region missed, with what places each one (its continent, its label point,
 * its nearest answer countries, the nearest one the run did name) and how often it is
 * named (by everyone's runs that count there, and by the player's own).
 *
 * <p><b>Order.</b> Most worth learning first: the countries most players name come
 * first (highest named rate), since missing one of those is the gap most worth
 * closing. Where there are too few runs for a rate (fewer than {@value #MIN_SAMPLE}),
 * well-known before familiar before obscure. Ties by code.
 *
 * <p><b>What counts as named.</b> A run's named countries, filled-in ones included, as
 * on its end card: the end card's "missed" is the region less those, and that is what
 * is studied here. The sample is every run in the region that counts (finished, with
 * at least one country typed; see GlobleGame#counts), by players who still exist, the
 * leaderboard's rule. The run studied is the player's newest finished one, whether it
 * counts or not, since its end card offers the study either way.
 *
 * <p>Read-only: nothing is stored, and nothing about a run in progress is used.
 */
@Service
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RollCallStudyService {

    /** Below this many runs in the sample, a named rate says too little and is null. */
    static final int MIN_SAMPLE = 3;
    /**
     * At least this many nearest answer countries each missed one lists; every bordering
     * one is listed, even when there are more.
     */
    static final int NEARBY = 4;

    static final String BY_NAMED_RATE = "named-rate";
    static final String BY_FAMILIARITY = "familiarity";

    /** Highest named rate first (none last), then well-known before obscure, then by code. */
    private static final Comparator<RollCallStudy.Missed> MOST_WORTH_LEARNING =
            Comparator.comparing(RollCallStudy.Missed::namedRate, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparingInt(missed -> familiarityRank(missed.familiarity()))
                    .thenComparing(RollCallStudy.Missed::code);

    private final GlobleGameRepository games;
    private final CountryCatalog catalog;
    private final RollCallPools pools;
    private final UserRepository users;

    public RollCallStudyService(GlobleGameRepository games, CountryCatalog catalog, RollCallPools pools,
                                UserRepository users) {
        this.games = games;
        this.catalog = catalog;
        this.pools = pools;
        this.users = users;
    }

    /** The study of the player's newest finished run in the region, or empty when there is none. */
    public Optional<RollCallStudy> study(Player player, Region region) {
        return games.findRollCallsFinished(player, region).stream()
                .findFirst()
                .map(run -> study(player, region, run));
    }

    private RollCallStudy study(Player player, Region region, GlobleGame run) {
        RollCallPools.Pool pool = pools.pool(region);
        Set<String> named = new HashSet<>(run.guessCodes());
        List<Country> namedCountries = run.guessCodes().stream()
                .map(catalog::find)
                .flatMap(Optional::stream)
                .toList();

        List<Set<String>> sample = everybodysCounting(region);
        List<Set<String>> yours = games.findRollCallsCounting(player, region).stream()
                .map(game -> (Set<String>) new HashSet<>(game.guessCodes()))
                .toList();

        List<RollCallStudy.Missed> missed = pool.countries().stream()
                .filter(country -> !named.contains(country.code()))
                .map(country -> missed(country, namedCountries, sample, yours))
                .sorted(MOST_WORTH_LEARNING)
                .toList();

        return new RollCallStudy(
                run.getId(),
                region.api(),
                run.getFinishedAt() == null ? null : run.getFinishedAt().toString(),
                run.getGuessCount(),
                pool.size(),
                missed.size(),
                missed,
                sample.size() >= MIN_SAMPLE ? BY_NAMED_RATE : BY_FAMILIARITY);
    }

    private RollCallStudy.Missed missed(Country country, List<Country> namedCountries, List<Set<String>> sample,
                                        List<Set<String>> yours) {
        String code = country.code();
        long namedBy = sample.stream().filter(run -> run.contains(code)).count();
        Double rate = sample.size() < MIN_SAMPLE ? null : Math.round(10_000.0 * namedBy / sample.size()) / 10_000.0;
        int yourMisses = (int) yours.stream().filter(run -> !run.contains(code)).count();
        // Every bordering answer country (0 km comes first), then the nearest others up to NEARBY.
        List<RollCallStudy.Near> ranked = nearest(country, catalog.answerable(Region.WORLD, Kind.COUNTRY)).toList();
        int borders = (int) ranked.stream().filter(near -> near.km() == 0).count();
        List<RollCallStudy.Near> nearby = List.copyOf(ranked.subList(0, Math.min(ranked.size(), Math.max(NEARBY, borders))));
        return new RollCallStudy.Missed(
                code,
                country.region(),
                country.lat(),
                country.lng(),
                nearby,
                nearest(country, namedCountries).findFirst().orElse(null),
                rate,
                sample.size(),
                yours.size(),
                yourMisses,
                familiarity(country));
    }

    /**
     * Other places nearest {@code country} first, by border-to-border km, then by how
     * far apart their label points are (so of several neighbours, the one whose middle
     * is closest), then by code.
     */
    private Stream<RollCallStudy.Near> nearest(Country country, List<Country> others) {
        record Candidate(Country other, int km, double labelKm) {
        }
        return others.stream()
                .filter(other -> !other.code().equals(country.code()))
                .map(other -> new Candidate(other, catalog.km(country.code(), other.code()),
                        GreatCircle.km(country.lat(), country.lng(), other.lat(), other.lng())))
                .sorted(Comparator.comparingInt(Candidate::km)
                        .thenComparingDouble(Candidate::labelKm)
                        .thenComparing(candidate -> candidate.other().code()))
                .map(candidate -> new RollCallStudy.Near(candidate.other().code(), candidate.km()));
    }

    /** Every run in the region that counts, by a player who still exists, as its set of codes named. */
    private List<Set<String>> everybodysCounting(Region region) {
        Map<Long, String> existing = users.findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return games.findRollCallsCounting(region).stream()
                .filter(run -> Objects.equals(existing.get(run.getUserId()), run.getUsername()))
                .map(run -> (Set<String>) new HashSet<>(run.guessCodes()))
                .toList();
    }

    /** Tiers 1-2, 3 and 4-5, coarsely. */
    static String familiarity(Country country) {
        int tier = country.difficulty();
        if (tier <= Country.EASIEST + 1) {
            return "well-known";
        }
        return tier == Country.EASIEST + 2 ? "familiar" : "obscure";
    }

    private static int familiarityRank(String familiarity) {
        return switch (familiarity) {
            case "well-known" -> 0;
            case "familiar" -> 1;
            default -> 2;
        };
    }
}

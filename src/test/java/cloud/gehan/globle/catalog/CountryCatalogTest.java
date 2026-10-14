package cloud.gehan.globle.catalog;

import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** The shipped data, as the server loads it, plus the checks that make it refuse bad data. */
class CountryCatalogTest {

    private static CountryCatalog catalog;

    @BeforeAll
    static void load() {
        catalog = new CountryCatalog();
    }

    @Test
    void loadsEveryPlaceAndEveryAnswer() {
        assertThat(catalog.all()).hasSize(248);
        assertThat(catalog.answerable(Region.WORLD, Kind.COUNTRY)).hasSize(197);

        Set<String> codes = new HashSet<>();
        catalog.all().forEach(c -> assertThat(codes.add(c.code())).as("duplicate %s", c.code()).isTrue());
    }

    /**
     * The file is sorted with accent-insensitive collation, which is what the
     * autocomplete expects; the catalogue keeps that order rather than re-sorting.
     */
    @Test
    void keepsTheFilesNameOrder() {
        assertThat(catalog.all().get(0).name()).isEqualTo("Afghanistan");
        assertThat(catalog.all().get(1).name()).isEqualTo("Åland Islands");
    }

    @Test
    void neighboursAndTheSameCountryAreZeroKilometresApart() {
        assertThat(catalog.km("FRA", "ESP")).isZero();
        assertThat(catalog.km("USA", "CAN")).isZero();
        assertThat(catalog.km("ITA", "VAT")).isZero();
        assertThat(catalog.km("FRA", "FRA")).isZero();
    }

    @Test
    void distancesAreBorderToBorderAndSymmetric() {
        assertThat(catalog.km("GBR", "FRA")).isBetween(25, 45);
        assertThat(catalog.km("FRA", "BRA")).isEqualTo(catalog.km("BRA", "FRA")).isGreaterThan(5000);
        assertThat(catalog.km("JPN", "KOR")).isBetween(40, 70);
    }

    @Test
    void capitalDistancesAreCityToCity() {
        // Paris to Madrid is about 1,053 km as the crow flies.
        assertThat(catalog.capitalKm("FRA", "ESP")).isBetween(1040, 1065);
        assertThat(catalog.capitalKm("ESP", "FRA")).isEqualTo(catalog.capitalKm("FRA", "ESP"));
        assertThat(catalog.capitalKm("FRA", "FRA")).isZero();
        // Neighbours are not zero here: the capitals are still apart.
        assertThat(catalog.capitalKm("USA", "CAN")).isBetween(700, 760);
    }

    @Test
    void placesWithoutACapitalCannotBeMeasuredByOne() {
        assertThat(catalog.require("ATA").hasCapital()).isFalse();
        assertThatThrownBy(() -> catalog.capitalKm("ATA", "FRA")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void answerPoolsRespectRegionAndKind() {
        for (Region region : Region.values()) {
            for (Kind kind : Kind.values()) {
                List<Country> pool = catalog.answerable(region, kind);
                assertThat(pool).isNotEmpty();
                assertThat(pool).allMatch(Country::answer);
                assertThat(pool).allMatch(c -> region.includes(c.region(), c.smallIsland()));
                if (kind == Kind.CAPITAL) {
                    assertThat(pool).allMatch(Country::hasCapital);
                }
            }
        }
        assertThat(catalog.answerable(Region.EUROPE, Kind.COUNTRY)).extracting(Country::code)
                .contains("FRA").doesNotContain("JPN", "GRL");
    }

    @Test
    void everyAnswerHasACapital() {
        assertThat(catalog.answerable(Region.WORLD, Kind.CAPITAL))
                .hasSameSizeAs(catalog.answerable(Region.WORLD, Kind.COUNTRY));
    }

    @Test
    void lookupsAreExact() {
        assertThat(catalog.find("FRA")).map(Country::name).contains("France");
        assertThat(catalog.find("fra")).isEmpty();
        assertThat(catalog.find("XXX")).isEmpty();
        assertThat(catalog.find(null)).isEmpty();
    }

    @Test
    void greatCircleDistances() {
        assertThat(GreatCircle.km(0, 0, 0, 0)).isZero();
        // Half the circumference, and no NaN at the antipode.
        assertThat(GreatCircle.km(0, 0, 0, 180)).isCloseTo(Math.PI * GreatCircle.EARTH_RADIUS_KM, within(0.5));
        assertThat(GreatCircle.km(90, 0, -90, 0)).isCloseTo(Math.PI * GreatCircle.EARTH_RADIUS_KM, within(0.5));
    }

    @Test
    void everyPlaceHasADifficultyAndAnIslandFlag() {
        assertThat(catalog.require("USA").difficulty()).isEqualTo(1);
        assertThat(catalog.require("JPN").difficulty()).isEqualTo(1);
        assertThat(catalog.require("TUV").difficulty()).isEqualTo(5);
        assertThat(catalog.require("SWZ").difficulty()).isEqualTo(5);
        assertThat(catalog.all()).allSatisfy(c -> assertThat(c.difficulty()).isBetween(1, 5));
        // Territories are never the answer; the data marks them 5 and not small islands.
        assertThat(catalog.require("GRL").answer()).isFalse();
        assertThat(catalog.require("GRL").difficulty()).isEqualTo(5);
        assertThat(catalog.all()).filteredOn(c -> !c.answer()).noneMatch(Country::smallIsland);
        for (int tier = 1; tier <= 5; tier++) {
            int t = tier;
            assertThat(catalog.answerable(Region.WORLD, Kind.COUNTRY)).as("tier %d", tier)
                    .anyMatch(c -> c.difficulty() == t);
        }
    }

    /** "Just no obscure island nations, like Japan still counts but not Tonga." */
    @Test
    void mainlandLeavesOutExactlyTheSmallIslandNations() {
        List<Country> mainland = catalog.answerable(Region.MAINLAND, Kind.COUNTRY);
        assertThat(mainland).hasSize(166).noneMatch(Country::smallIsland);
        assertThat(mainland).extracting(Country::code).contains(
                "JPN", "GBR", "IRL", "ISL", "CUB", "NZL", "MDG", "LKA", "PHL", "IDN", "TWN", "HTI", "DOM",
                "PNG", "TLS", "BRN", "AUS", "AND", "VAT", "MCO", "SMR", "LIE", "LUX");
        assertThat(mainland).extracting(Country::code).doesNotContain(
                "TON", "FJI", "JAM", "BHS", "TTO", "BRB", "MLT", "CYP", "BHR", "SGP", "MDV", "MUS", "SYC",
                "COM", "STP", "CPV", "SLB", "VUT", "WSM", "KIR", "TUV", "NRU", "PLW", "MHL", "FSM", "LCA",
                "VCT", "GRD", "ATG", "DMA", "KNA");
        assertThat(catalog.answerable(Region.MAINLAND, Kind.CAPITAL)).hasSameSizeAs(mainland);
    }

    // ------------------------------------------------------------------ Spanish names

    @Test
    void everyPlaceAndEveryCapitalHasASpanishName() {
        assertThat(catalog.all()).allSatisfy(c -> {
            assertThat(c.nameEs()).as("%s nameEs", c.code()).isNotBlank();
            assertThat(c.aliasesEs()).as("%s aliasesEs", c.code()).allSatisfy(a -> assertThat(a).isNotBlank());
            if (c.hasCapital()) {
                assertThat(c.capital().nameEs()).as("%s capital nameEs", c.code()).isNotBlank();
                assertThat(c.capital().aliasesEs()).as("%s capital aliasesEs", c.code())
                        .allSatisfy(a -> assertThat(a).isNotBlank());
            }
        });
    }

    @Test
    void spanishNamesAreTheOnesSpanishSpeakersUse() {
        assertThat(catalog.require("DEU").nameEs()).isEqualTo("Alemania");
        assertThat(catalog.require("USA").nameEs()).isEqualTo("Estados Unidos");
        assertThat(catalog.require("USA").aliasesEs()).contains("EE. UU.", "EEUU");
        assertThat(catalog.require("GBR").nameEs()).isEqualTo("Reino Unido");
        assertThat(catalog.require("NLD").nameEs()).isEqualTo("Países Bajos");
        assertThat(catalog.require("NLD").aliasesEs()).contains("Holanda");
        assertThat(catalog.require("FLK").nameEs()).isEqualTo("Islas Malvinas");
        assertThat(catalog.require("CIV").nameEs()).isEqualTo("Costa de Marfil");
        assertThat(catalog.require("XKX").nameEs()).isEqualTo("Kosovo");
        assertThat(catalog.require("GBR").capital().nameEs()).isEqualTo("Londres");
        assertThat(catalog.require("CHN").capital().nameEs()).isEqualTo("Pekín");
        assertThat(catalog.require("MEX").capital().nameEs()).isEqualTo("Ciudad de México");
        assertThat(catalog.require("NLD").capital().aliasesEs()).contains("La Haya");
        // Where Spanish uses the English name unchanged, it is repeated, never left out.
        assertThat(catalog.require("PER").capital().nameEs()).isEqualTo("Lima");
        // The English names are untouched: the server names places in English.
        assertThat(catalog.require("DEU").name()).isEqualTo("Germany");
        assertThat(catalog.require("GBR").capital().name()).isEqualTo("London");
    }

    /**
     * The page accepts both languages whichever it shows, so no name or alias, English
     * or Spanish, may name two places. Case and accents do not count, as on the page.
     */
    @Test
    void noNameInEitherLanguageNamesTwoPlaces() {
        Map<String, String> owners = new HashMap<>();
        for (Country c : catalog.all()) {
            List<String> names = new ArrayList<>();
            names.add(c.name());
            names.addAll(c.aliases());
            names.add(c.nameEs());
            names.addAll(c.aliasesEs());
            for (String name : names) {
                String key = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                        .toLowerCase(Locale.ROOT).trim();
                String previous = owners.putIfAbsent(key, c.code());
                assertThat(previous == null || previous.equals(c.code()))
                        .as("\"%s\" names both %s and %s", name, previous, c.code()).isTrue();
            }
        }
    }

    /** A countries.json from before Spanish names is refused, not shown half in English. */
    @Test
    void refusesAPlaceOrCapitalWithoutASpanishName() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\",\"CCC\",\"DDD\",\"EEE\"],\"km\":"
                + "[[0,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[1,1,1,1,0]]}";
        String good = countries("AAA", "BBB", "CCC", "DDD", "EEE");
        CountryCatalog loaded = load(good, distances);
        assertThat(loaded.require("AAA").nameEs()).isEqualTo("Lugar 0");
        assertThat(loaded.require("AAA").capital().nameEs()).isEqualTo("Ciudad 0");

        for (String broken : List.of(
                good.replaceFirst("\"nameEs\":\"Lugar 0\",", ""),
                good.replaceFirst("\"nameEs\":\"Lugar 0\"", "\"nameEs\":\" \""),
                good.replaceFirst("\"nameEs\":\"Lugar 0\"", "\"nameEs\":null"),
                good.replaceFirst("\"nameEs\":\"Lugar 0\"", "\"nameEs\":3"),
                good.replaceFirst("\"nameEs\":\"Ciudad 2\",", ""),
                good.replaceFirst("\"nameEs\":\"Ciudad 2\"", "\"nameEs\":\"\""))) {
            assertThatThrownBy(() -> load(broken, distances))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("nameEs")
                    .hasMessageContaining("globle.enabled=false");
        }
    }

    /** Spanish aliases are optional, like the English ones: many places have none. */
    @Test
    void spanishAliasesMayBeLeftOut() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\",\"CCC\",\"DDD\",\"EEE\"],\"km\":"
                + "[[0,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[1,1,1,1,0]]}";
        String withAliases = countries("AAA", "BBB", "CCC", "DDD", "EEE")
                .replaceFirst("\"aliasesEs\":\\[]", "\"aliasesEs\":[\"Otro\"]");
        assertThat(load(withAliases, distances).require("AAA").aliasesEs()).containsExactly("Otro");

        String without = countries("AAA", "BBB", "CCC", "DDD", "EEE").replace(",\"aliasesEs\":[]", "");
        CountryCatalog loaded = load(without, distances);
        assertThat(loaded.require("AAA").aliasesEs()).isEmpty();
        assertThat(loaded.require("AAA").capital().aliasesEs()).isEmpty();
    }

    // ------------------------------------------------------------------ refusing bad data

    /** A countries.json from before Hard has neither field, and is refused rather than guessed at. */
    @Test
    void refusesAPlaceWithoutADifficultyOrIslandFlag() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\",\"CCC\",\"DDD\",\"EEE\"],\"km\":"
                + "[[0,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[1,1,1,1,0]]}";
        String good = countries("AAA", "BBB", "CCC", "DDD", "EEE");
        assertThat(load(good, distances).require("AAA").difficulty()).isEqualTo(3);

        for (String broken : List.of(
                good.replaceFirst("\"difficulty\":3,", ""),
                good.replaceFirst("\"difficulty\":3", "\"difficulty\":6"),
                good.replaceFirst("\"difficulty\":3", "\"difficulty\":0"),
                good.replaceFirst("\"difficulty\":3", "\"difficulty\":2.5"),
                good.replaceFirst("\"difficulty\":3", "\"difficulty\":\"3\""))) {
            assertThatThrownBy(() -> load(broken, distances))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("difficulty");
        }
        for (String broken : List.of(
                good.replaceFirst("\"smallIsland\":false,", ""),
                good.replaceFirst("\"smallIsland\":false", "\"smallIsland\":\"no\""))) {
            assertThatThrownBy(() -> load(broken, distances))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("smallIsland");
        }
    }

    /** Every answer a small island: a Mainland game could never start. */
    @Test
    void refusesDataWithNothingOnTheMainland() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\",\"CCC\",\"DDD\",\"EEE\"],\"km\":"
                + "[[0,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[1,1,1,1,0]]}";
        String islands = countries("AAA", "BBB", "CCC", "DDD", "EEE")
                .replace("\"smallIsland\":false", "\"smallIsland\":true");
        assertThatThrownBy(() -> load(islands, distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("mainland");
    }

    @Test
    void refusesADistanceFileForADifferentSetOfCountries() {
        String distances = "{\"codes\":[\"AAA\",\"ZZZ\"],\"km\":[[0,1],[1,0]]}";
        assertThatThrownBy(() -> load(countries("AAA", "BBB"), distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Globle data is inconsistent")
                .hasMessageContaining("ZZZ")
                .hasMessageContaining("globle.enabled=false");
    }

    @Test
    void refusesAnAsymmetricMatrix() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\"],\"km\":[[0,1],[2,0]]}";
        assertThatThrownBy(() -> load(countries("AAA", "BBB"), distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not symmetric");
    }

    @Test
    void refusesAMatrixOfTheWrongShape() {
        String distances = "{\"codes\":[\"AAA\",\"BBB\"],\"km\":[[0,1]]}";
        assertThatThrownBy(() -> load(countries("AAA", "BBB"), distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("rows");
    }

    @Test
    void refusesADuplicatedCode() {
        String distances = "{\"codes\":[\"AAA\",\"AAA\"],\"km\":[[0,0],[0,0]]}";
        assertThatThrownBy(() -> load(countries("AAA", "AAA"), distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("twice");
    }

    @Test
    void refusesARegionWithNothingToGuess() {
        // Five answers, but the African one moved to Asia: an Africa game could never start.
        String countries = countries("AAA", "BBB", "CCC", "DDD", "EEE")
                .replace("\"region\":\"africa\",\"lat\":1", "\"region\":\"asia\",\"lat\":1");
        String distances = "{\"codes\":[\"AAA\",\"BBB\",\"CCC\",\"DDD\",\"EEE\"],\"km\":"
                + "[[0,1,1,1,1],[1,0,1,1,1],[1,1,0,1,1],[1,1,1,0,1],[1,1,1,1,0]]}";
        assertThatThrownBy(() -> load(countries, distances))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no possible");
    }

    @Test
    void acceptsSmallButConsistentData() {
        String distances = "{\"codes\":[\"EEE\",\"DDD\",\"CCC\",\"BBB\",\"AAA\"],\"km\":"
                + "[[0,4,3,2,1],[4,0,7,6,5],[3,7,0,9,8],[2,6,9,0,10],[1,5,8,10,0]]}";
        CountryCatalog small = load(countries("AAA", "BBB", "CCC", "DDD", "EEE"), distances);
        // Indexed by the distance file's own code list, not by position.
        assertThat(small.km("AAA", "EEE")).isEqualTo(1);
        assertThat(small.km("BBB", "DDD")).isEqualTo(6);
        assertThat(small.km("DDD", "BBB")).isEqualTo(6);
    }

    @Test
    void theShippedFilesAreWhatTheDefaultConstructorReads() {
        assertThat(new ClassPathResource(CountryCatalog.COUNTRIES).exists()).isTrue();
        assertThat(new ClassPathResource(CountryCatalog.DISTANCES).exists()).isTrue();
    }

    /** One answer country per region, in order: africa, americas, asia, europe, oceania. */
    private static String countries(String... codes) {
        String[] regions = {"africa", "americas", "asia", "europe", "oceania"};
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < codes.length; i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append("{\"code\":\"").append(codes[i]).append("\",\"name\":\"Place ").append(i)
                    .append("\",\"nameEs\":\"Lugar ").append(i)
                    .append("\",\"iso2\":null,\"aliases\":[],\"aliasesEs\":[],\"answer\":true,")
                    .append("\"difficulty\":3,\"smallIsland\":false,\"region\":\"")
                    .append(regions[i % regions.length]).append("\",\"lat\":").append(i + 1)
                    .append(",\"lng\":").append(i + 1)
                    .append(",\"capital\":{\"name\":\"City ").append(i)
                    .append("\",\"nameEs\":\"Ciudad ").append(i)
                    .append("\",\"aliases\":[],\"aliasesEs\":[],\"lat\":").append(i + 1)
                    .append(",\"lng\":").append(i + 1)
                    .append("}}");
        }
        return out.append(']').toString();
    }

    private static CountryCatalog load(String countries, String distances) {
        return new CountryCatalog(
                new ByteArrayResource(countries.getBytes(StandardCharsets.UTF_8)),
                new ByteArrayResource(distances.getBytes(StandardCharsets.UTF_8)));
    }
}

package cloud.gehan.globle.catalog;

import cloud.gehan.globle.model.Kind;
import cloud.gehan.globle.model.Region;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The countries and the distances between them, loaded once at startup from the files
 * tools/globle generates, and immutable afterwards.
 *
 * <p>It checks the two files against each other on load and refuses to start on any
 * disagreement. A game played against a matrix that is out of step with the country
 * list would give silently wrong distances, which is worse than not starting; the
 * message says how to regenerate the data, or how to start the app without Globle.
 *
 * <p>Country order is the file's: already sorted by name with accent-insensitive
 * English collation, which is what the autocomplete expects. Re-sorting here with
 * String.compareTo would move Åland, Côte d'Ivoire and friends to the end.
 */
@Component
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CountryCatalog {

    private static final Logger log = LoggerFactory.getLogger(CountryCatalog.class);

    static final String COUNTRIES = "globle/countries.json";
    static final String DISTANCES = "globle/distances.json";

    private static final Set<String> DATA_REGIONS =
            Set.of("africa", "americas", "asia", "europe", "oceania", "antarctica");
    private static final Pattern CODE = Pattern.compile("[A-Z]{3}");

    private final List<Country> countries;
    private final Map<String, Integer> indexByCode;
    /** Border-to-border km, indexed in catalogue order. */
    private final int[][] km;
    private final Map<Kind, Map<Region, List<Country>>> answers;

    public CountryCatalog() {
        this(new ClassPathResource(COUNTRIES), new ClassPathResource(DISTANCES));
    }

    /** For tests that need to feed it broken data. */
    CountryCatalog(Resource countriesJson, Resource distancesJson) {
        ObjectMapper json = new ObjectMapper();
        this.countries = parseCountries(read(json, countriesJson, COUNTRIES));
        this.indexByCode = indexCodes(countries);
        this.km = parseDistances(read(json, distancesJson, DISTANCES), indexByCode);
        this.answers = groupAnswers(countries);
        log.info("Globle catalogue loaded: {} places, {} possible answers, {} of them on the mainland",
                countries.size(), answers.get(Kind.COUNTRY).get(Region.WORLD).size(),
                answers.get(Kind.COUNTRY).get(Region.MAINLAND).size());
    }

    /** Every country and territory, sorted by name. */
    public List<Country> all() {
        return countries;
    }

    /** Looks up an exact, already-normalised code such as "FRA". */
    public Optional<Country> find(String code) {
        Integer i = code == null ? null : indexByCode.get(code);
        return i == null ? Optional.empty() : Optional.of(countries.get(i));
    }

    /** Minimum border-to-border distance in km; 0 for neighbours and for the same country. */
    public int km(String a, String b) {
        return km[index(a)][index(b)];
    }

    /** Capital-to-capital great-circle distance in whole km. */
    public int capitalKm(String a, String b) {
        Country.Capital from = require(a).capital();
        Country.Capital to = require(b).capital();
        if (from == null || to == null) {
            throw new IllegalArgumentException("No capital for " + (from == null ? a : b));
        }
        return (int) Math.round(GreatCircle.km(from.lat(), from.lng(), to.lat(), to.lng()));
    }

    /**
     * The countries a game of this kind may pick as its mystery: answer countries in
     * the region, and for a capital game only those with a capital. Every one equally;
     * Hard's weighting is applied on top of this by AnswerPools.
     */
    public List<Country> answerable(Region region, Kind kind) {
        return answers.get(kind).get(region);
    }

    public Country require(String code) {
        return countries.get(index(code));
    }

    private int index(String code) {
        Integer i = code == null ? null : indexByCode.get(code);
        if (i == null) {
            throw new IllegalArgumentException("Unknown country code " + code);
        }
        return i;
    }

    // ------------------------------------------------------------------ loading

    private static JsonNode read(ObjectMapper json, Resource resource, String name) {
        try (InputStream in = resource.getInputStream()) {
            return json.readTree(in);
        } catch (IOException e) {
            throw inconsistent(name + " could not be read (" + e.getMessage() + ")");
        }
    }

    private static List<Country> parseCountries(JsonNode root) {
        if (root == null || !root.isArray() || root.isEmpty()) {
            throw inconsistent(COUNTRIES + " is not a non-empty JSON array");
        }
        List<Country> parsed = new ArrayList<>();
        for (JsonNode node : root) {
            String code = text(node, "code", parsed.size());
            String where = COUNTRIES + " entry " + code;
            if (!CODE.matcher(code).matches()) {
                throw inconsistent(where + ": code must be three capital letters");
            }
            String region = text(node, "region", parsed.size());
            if (!DATA_REGIONS.contains(region)) {
                throw inconsistent(where + ": unknown region \"" + region + "\"");
            }
            JsonNode capital = node.get("capital");
            Country.Capital cap = capital == null || capital.isNull() ? null : new Country.Capital(
                    text(capital, "name", parsed.size()),
                    spanishName(capital, where + " capital"),
                    strings(capital.get("aliases")),
                    strings(capital.get("aliasesEs")),
                    latitude(capital, where + " capital"),
                    longitude(capital, where + " capital"));
            JsonNode iso2 = node.get("iso2");
            parsed.add(new Country(
                    code,
                    text(node, "name", parsed.size()),
                    spanishName(node, where),
                    iso2 == null || iso2.isNull() ? null : iso2.asText(),
                    strings(node.get("aliases")),
                    strings(node.get("aliasesEs")),
                    node.path("answer").asBoolean(false),
                    region,
                    difficulty(node, where),
                    flag(node, "smallIsland", where),
                    latitude(node, where),
                    longitude(node, where),
                    cap));
        }
        for (Country country : parsed) {
            if (country.answer() && !country.hasCapital()) {
                throw inconsistent(COUNTRIES + " entry " + country.code()
                        + " can be an answer but has no capital, which Capitals mode needs");
            }
        }
        return Collections.unmodifiableList(parsed);
    }

    private static Map<String, Integer> indexCodes(List<Country> countries) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < countries.size(); i++) {
            if (index.put(countries.get(i).code(), i) != null) {
                throw inconsistent(COUNTRIES + " lists " + countries.get(i).code() + " twice");
            }
        }
        return Map.copyOf(index);
    }

    /**
     * Reads the matrix into catalogue order. The file carries its own code list, so it
     * is indexed by that rather than assuming it shares the country file's order.
     */
    private static int[][] parseDistances(JsonNode root, Map<String, Integer> indexByCode) {
        JsonNode codes = root == null ? null : root.get("codes");
        JsonNode rows = root == null ? null : root.get("km");
        if (codes == null || !codes.isArray() || rows == null || !rows.isArray()) {
            throw inconsistent(DISTANCES + " needs a \"codes\" array and a \"km\" matrix");
        }
        int n = indexByCode.size();
        if (codes.size() != n) {
            throw inconsistent(DISTANCES + " has " + codes.size() + " codes but "
                    + COUNTRIES + " has " + n + " countries");
        }
        int[] toCatalog = new int[n];
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String code = codes.get(i).asText();
            Integer at = indexByCode.get(code);
            if (at == null) {
                throw inconsistent(DISTANCES + " has " + code + ", which " + COUNTRIES + " does not");
            }
            if (!seen.add(code)) {
                throw inconsistent(DISTANCES + " lists " + code + " twice");
            }
            toCatalog[i] = at;
        }
        if (rows.size() != n) {
            throw inconsistent(DISTANCES + " matrix has " + rows.size() + " rows, expected " + n);
        }
        int[][] km = new int[n][n];
        for (int i = 0; i < n; i++) {
            JsonNode row = rows.get(i);
            if (row == null || !row.isArray() || row.size() != n) {
                throw inconsistent(DISTANCES + " matrix row " + i + " is not " + n + " numbers long");
            }
            for (int j = 0; j < n; j++) {
                JsonNode cell = row.get(j);
                if (cell == null || !cell.canConvertToInt() || !cell.isIntegralNumber() || cell.asInt() < 0) {
                    throw inconsistent(DISTANCES + " matrix cell [" + i + "][" + j
                            + "] is not a whole number of km");
                }
                km[toCatalog[i]][toCatalog[j]] = cell.asInt();
            }
        }
        for (int i = 0; i < n; i++) {
            if (km[i][i] != 0) {
                throw inconsistent(DISTANCES + " gives a country a non-zero distance to itself");
            }
            for (int j = i + 1; j < n; j++) {
                if (km[i][j] != km[j][i]) {
                    throw inconsistent(DISTANCES + " matrix is not symmetric");
                }
            }
        }
        return km;
    }

    private static Map<Kind, Map<Region, List<Country>>> groupAnswers(List<Country> countries) {
        Map<Kind, Map<Region, List<Country>>> grouped = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            Map<Region, List<Country>> byRegion = new EnumMap<>(Region.class);
            for (Region region : Region.values()) {
                List<Country> pool = countries.stream()
                        .filter(Country::answer)
                        .filter(c -> region.includes(c.region(), c.smallIsland()))
                        .filter(c -> kind != Kind.CAPITAL || c.hasCapital())
                        .toList();
                if (pool.isEmpty()) {
                    throw inconsistent(COUNTRIES + " has no possible " + kind.api()
                            + " answer in " + region.api() + ", so that game could never start");
                }
                byRegion.put(region, pool);
            }
            grouped.put(kind, Collections.unmodifiableMap(byRegion));
        }
        return Collections.unmodifiableMap(grouped);
    }

    private static String text(JsonNode node, String field, int position) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw inconsistent(COUNTRIES + " entry #" + (position + 1) + " has no " + field);
        }
        return value.asText();
    }

    /**
     * Required, so a countries.json from before Hard is refused rather than played with
     * every country quietly given the same difficulty.
     */
    private static int difficulty(JsonNode node, String where) {
        JsonNode value = node.get("difficulty");
        if (value == null || !value.isIntegralNumber()
                || value.asInt() < Country.EASIEST || value.asInt() > Country.HARDEST) {
            throw inconsistent(where + ": difficulty must be a whole number from "
                    + Country.EASIEST + " to " + Country.HARDEST);
        }
        return value.asInt();
    }

    /**
     * Required on every place and every capital, so a countries.json from before
     * Spanish names is refused rather than served to a page that would show Spanish
     * players a blank, or fall back to English for some places and not others.
     * Spanish aliases are optional, like the English ones: many places have none.
     */
    private static String spanishName(JsonNode node, String where) {
        JsonNode value = node.get("nameEs");
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw inconsistent(where + ": nameEs (the Spanish name) is missing or blank");
        }
        return value.asText();
    }

    private static boolean flag(JsonNode node, String field, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw inconsistent(where + ": " + field + " must be true or false");
        }
        return value.asBoolean();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        if (array != null && array.isArray()) {
            array.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    private static double latitude(JsonNode node, String where) {
        return coordinate(node, "lat", 90, where);
    }

    private static double longitude(JsonNode node, String where) {
        return coordinate(node, "lng", 180, where);
    }

    private static double coordinate(JsonNode node, String field, double limit, String where) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber() || Math.abs(value.asDouble()) > limit) {
            throw inconsistent(where + ": " + field + " is missing or out of range");
        }
        return value.asDouble();
    }

    private static IllegalStateException inconsistent(String detail) {
        return new IllegalStateException("Globle data is inconsistent: " + detail
                + ". Regenerate it with tools/globle (npm run build && npm run check),"
                + " or set globle.enabled=false to start the app without the game.");
    }
}

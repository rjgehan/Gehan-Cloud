package cloud.gehan.globle.web;

import cloud.gehan.globle.GlobleTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The country list in both languages: the page shows Spanish names to Spanish
 * players, localising by code, and accepts either language's names when typed.
 */
class CountryListApiTest extends GlobleTestBase {

    private Map<String, JsonNode> countriesByCode() throws Exception {
        Map<String, JsonNode> byCode = new HashMap<>();
        for (JsonNode c : getOk("/globle/api/countries", player("jen"))) {
            byCode.put(c.get("code").asText(), c);
        }
        return byCode;
    }

    @Test
    void everyPlaceAndCapitalCarriesItsSpanishNames() throws Exception {
        Map<String, JsonNode> list = countriesByCode();
        assertThat(list).hasSize(catalog.all().size());
        for (JsonNode c : list.values()) {
            String code = c.get("code").asText();
            assertThat(c.get("nameEs").asText()).as("%s nameEs", code).isNotBlank()
                    .isEqualTo(catalog.require(code).nameEs());
            assertThat(c.get("aliasesEs").isArray()).as("%s aliasesEs", code).isTrue();
            JsonNode capital = c.get("capital");
            if (!capital.isNull()) {
                assertThat(capital.get("nameEs").asText()).as("%s capital nameEs", code).isNotBlank();
                assertThat(capital.get("aliasesEs").isArray()).as("%s capital aliasesEs", code).isTrue();
            }
        }
    }

    @Test
    void theCapitalCarriesBothLanguagesBesideItsCoordinates() throws Exception {
        JsonNode capital = countriesByCode().get("GBR").get("capital");
        List<String> fields = new ArrayList<>();
        capital.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactly("name", "nameEs", "aliases", "aliasesEs", "lat", "lng");
        assertThat(capital.get("name").asText()).isEqualTo("London");
        assertThat(capital.get("nameEs").asText()).isEqualTo("Londres");
    }

    @Test
    void spanishNamesAreTheOnesSpanishSpeakersUse() throws Exception {
        Map<String, JsonNode> list = countriesByCode();
        assertThat(list.get("DEU").get("name").asText()).isEqualTo("Germany");
        assertThat(list.get("DEU").get("nameEs").asText()).isEqualTo("Alemania");
        assertThat(list.get("USA").get("nameEs").asText()).isEqualTo("Estados Unidos");
        assertThat(list.get("FLK").get("nameEs").asText()).isEqualTo("Islas Malvinas");
        assertThat(list.get("CIV").get("nameEs").asText()).isEqualTo("Costa de Marfil");

        JsonNode netherlands = list.get("NLD");
        assertThat(netherlands.get("nameEs").asText()).isEqualTo("Países Bajos");
        List<String> aliasesEs = new ArrayList<>();
        netherlands.get("aliasesEs").forEach(a -> aliasesEs.add(a.asText()));
        assertThat(aliasesEs).contains("Holanda");

        assertThat(list.get("CHN").get("capital").get("nameEs").asText()).isEqualTo("Pekín");
        assertThat(list.get("MEX").get("capital").get("nameEs").asText()).isEqualTo("Ciudad de México");
        // Where Spanish uses the English name unchanged, nameEs repeats it.
        assertThat(list.get("PER").get("capital").get("nameEs").asText()).isEqualTo("Lima");
        // No capital, in either language.
        assertThat(list.get("ATA").get("nameEs").asText()).isEqualTo("Antártida");
        assertThat(list.get("ATA").get("capital").isNull()).isTrue();
    }
}

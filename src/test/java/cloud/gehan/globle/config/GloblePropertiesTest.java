package cloud.gehan.globle.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The game must run with nothing in application.properties, and each knob must bind
 * from the plain text someone would type there.
 */
class GloblePropertiesTest {

    private static GlobleProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("globle", GlobleProperties.class);
    }

    @Test
    void everySettingHasADefault() {
        GlobleProperties defaults = bind(Map.of());
        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.getZone()).isEqualTo(ZoneId.of("America/New_York"));
        assertThat(defaults.getEpoch()).isEqualTo(LocalDate.of(2026, 10, 9));
        assertThat(defaults.getAnswerRepeatWindowDays()).isEqualTo(60);
        assertThat(defaults.getMaxGuesses()).isEqualTo(400);
        assertThat(defaults.getHardWeights()).isEqualTo(Map.of(1, 0, 2, 1, 3, 3, 4, 6, 5, 8));
    }

    /** One tier set in application.properties leaves the others at their defaults. */
    @Test
    void hardWeightsBindPerTier() {
        GlobleProperties properties = bind(Map.of("globle.hard-weights.1", "2", "globle.hard-weights.5", "20"));
        assertThat(properties.getHardWeights()).isEqualTo(Map.of(1, 2, 2, 1, 3, 3, 4, 6, 5, 20));
        assertThat(properties.hardWeight(5)).isEqualTo(20);
        assertThat(properties.hardWeight(9)).isZero();
    }

    @Test
    void hardWeightsThatMakeNoSenseAreRefused() {
        assertThatThrownBy(() -> bind(Map.of("globle.hard-weights.6", "1"))).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of("globle.hard-weights.2", "-1"))).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of(
                "globle.hard-weights.2", "0", "globle.hard-weights.3", "0",
                "globle.hard-weights.4", "0", "globle.hard-weights.5", "0")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void settingsBindFromText() {
        GlobleProperties properties = bind(Map.of(
                "globle.enabled", "false",
                "globle.zone", "Europe/London",
                "globle.epoch", "2027-01-01",
                "globle.answer-repeat-window-days", "30",
                "globle.max-guesses", "50"));
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getZone()).isEqualTo(ZoneId.of("Europe/London"));
        assertThat(properties.getEpoch()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(properties.getAnswerRepeatWindowDays()).isEqualTo(30);
        assertThat(properties.getMaxGuesses()).isEqualTo(50);
    }

    @Test
    void nonsenseIsRefusedAtStartupRatherThanMisbehavingLater() {
        assertThatThrownBy(() -> bind(Map.of("globle.max-guesses", "0"))).isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of("globle.answer-repeat-window-days", "-1")))
                .isInstanceOf(BindException.class);
        assertThatThrownBy(() -> bind(Map.of("globle.zone", "Not/AZone"))).isInstanceOf(BindException.class);
    }
}

package cloud.gehan.globle.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds {@link GlobleProperties} when the game is on.
 *
 * <p>Deliberately defines no beans of its own. In particular there is no {@code Clock}
 * bean: an unqualified one would be picked up by anything else in the app that ever
 * asks for a Clock, which is exactly the kind of reach a removable feature must not
 * have. Globle's time source is {@code GlobleClock}, which only looks for a Clock
 * qualified "globleTimeSource" (a seam for tests) and otherwise uses the system clock.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(GlobleProperties.class)
public class GlobleConfiguration {
}

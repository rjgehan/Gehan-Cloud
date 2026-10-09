package cloud.gehan.globle.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.util.DigestUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets browsers keep the game's static files between visits.
 *
 * <p>Left alone, {@code static/globle} is served with Spring Security's default
 * {@code no-store}, so every visit downloaded the globe's shapes, d3 and the game's
 * scripts again: about 1.2 MB, and the "Loading the world" wait, each time. These files
 * are served instead as {@code no-cache, private} with an ETag of their content. The
 * browser keeps them and asks each visit whether they changed, and the answer is a
 * 304 with no body until a deploy changes a file, so a stale script or world.json is
 * never used. {@code private} keeps a shared cache such as the CDN from storing files
 * that need a session. Security's own cache headers step aside for a response that
 * already has one, so no security setting changes; the page and the API stay
 * {@code no-store}.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "globle", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobleStaticCaching implements WebMvcConfigurer {

    /** Content hashes, keyed by file and its modification time, so a file is read once. */
    private final Map<String, String> etags = new ConcurrentHashMap<>();

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/globle/**")
                .addResourceLocations("classpath:/static/globle/")
                .setCacheControl(CacheControl.noCache().cachePrivate())
                .setEtagGenerator(this::etag);
    }

    private String etag(Resource resource) {
        try {
            String key = resource.getURL() + "@" + resource.lastModified();
            return etags.computeIfAbsent(key, k -> md5(resource));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String md5(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return DigestUtils.md5DigestAsHex(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

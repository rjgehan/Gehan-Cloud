package cloud.gehan.security;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Duration;
import java.util.Date;

/** Mints and verifies the bearer tokens that {@code /api/**} accepts. */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final Duration EXPIRATION = Duration.ofHours(1);
    /** HS256 needs at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private final Key key;

    public JwtUtil(@Value("${app.jwt.secret:}") String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET is not set - generating a random signing key. "
                    + "Every API token is invalidated when this instance restarts.");
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            return;
        }
        byte[] material = secret.getBytes(StandardCharsets.UTF_8);
        if (material.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " characters; got " + material.length);
        }
        this.key = Keys.hmacShaKeyFor(material);
    }

    public String generateToken(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + EXPIRATION.toMillis()))
                .signWith(key)
                .compact();
    }

    /** @return the subject of a valid, unexpired token, or {@code null} if it fails verification. */
    public String validateToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}

package edu.vitap.common;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;

public class AccessTokenService {
    @Value("${AUTH_TOKEN_SECRET:}") private String configuredSecret;
    @Value("${AUTH_TOKEN_TTL_SECONDS:3600}") private long ttlSeconds;

    public String issue(String subject) {
        SecretKey key = key();
        Date now = new Date();
        return Jwts.builder().subject(subject).issuedAt(now)
                .expiration(new Date(now.getTime() + Math.max(60, ttlSeconds) * 1000L))
                .signWith(key).compact();
    }

    public String verifySubject(String token) {
        return Jwts.parser().verifyWith(key()).build().parseSignedClaims(token)
                .getPayload().getSubject();
    }

    private SecretKey key() {
        try {
            byte[] bytes = Base64.getDecoder().decode(configuredSecret == null ? "" : configuredSecret.trim());
            if (bytes.length < 32) throw new IllegalStateException("AUTH_TOKEN_SECRET must be Base64 for at least 32 random bytes");
            return Keys.hmacShaKeyFor(bytes);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("AUTH_TOKEN_SECRET must be valid Base64 for at least 32 random bytes", e);
        }
    }
}

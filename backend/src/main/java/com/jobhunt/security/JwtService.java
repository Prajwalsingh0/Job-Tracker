package com.jobhunt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Issues and validates HS256 JSON Web Tokens. */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    /**
     * HS256 requires a 256-bit key. Any string of at least 32 characters is at least
     * 32 bytes once UTF-8 encoded, so this single check covers both requirements.
     */
    private static final int MIN_SECRET_CHARS = 32;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {

        // Fail fast: never start with a missing or weak signing key. The secret value
        // itself is never logged or included in any error message.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set. Set the JWT_SECRET environment variable to a random string "
                            + "of at least " + MIN_SECRET_CHARS + " characters before starting the application. "
                            + "Generate one with: openssl rand -base64 48");
        }

        if (secret.length() < MIN_SECRET_CHARS) {
            throw new IllegalStateException(
                    "JWT_SECRET is too short (" + secret.length() + " characters). It must be at least "
                            + MIN_SECRET_CHARS + " characters (256 bits) for HS256. "
                            + "Generate one with: openssl rand -base64 48");
        }

        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String email, String name) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setSubject(email)
                .claim("uid", userId)
                .claim("name", name)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}

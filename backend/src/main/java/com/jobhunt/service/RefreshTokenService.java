package com.jobhunt.service;

import com.jobhunt.entity.RefreshToken;
import com.jobhunt.entity.User;
import com.jobhunt.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * <p>Tokens are opaque 256-bit random strings. Only the SHA-256 hash is persisted, and
 * rotation marks the old row revoked rather than deleting it. Presenting an already
 * revoked token revokes the user's whole family of tokens, which limits the damage if a
 * token is stolen and replayed.
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final RefreshTokenRepository repository;
    private final long refreshTtlMs;

    public RefreshTokenService(RefreshTokenRepository repository,
                               @Value("${app.jwt.refresh-expiration-ms}") long refreshTtlMs) {
        this.repository = repository;
        this.refreshTtlMs = refreshTtlMs;
    }

    public long getRefreshTtlMs() {
        return refreshTtlMs;
    }

    @Transactional
    public String issue(User user) {
        String rawToken = generateRawToken();
        repository.save(new RefreshToken(user, sha256Hex(rawToken), Instant.now().plusMillis(refreshTtlMs)));
        return rawToken;
    }

    /** Validates a raw token, revokes it and issues a replacement. */
    @Transactional
    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            // No cookie at all is a client error, not a server fault.
            throw new BadCredentialsException("Missing refresh token");
        }

        RefreshToken stored = repository.findByTokenHash(sha256Hex(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (!stored.isActive(Instant.now())) {
            // Reuse of a revoked or expired token: assume compromise and revoke everything.
            repository.revokeAllForUser(stored.getUser().getId());
            throw new BadCredentialsException("Refresh token is no longer valid");
        }

        stored.setRevoked(true);
        repository.save(stored);

        User user = stored.getUser();
        return new Rotation(user.getId(), issue(user));
    }

    /** Logout: revokes every active token belonging to the holder of this token. */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(sha256Hex(rawToken))
                .ifPresent(stored -> repository.revokeAllForUser(stored.getUser().getId()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record Rotation(Long userId, String refreshToken) {
    }
}

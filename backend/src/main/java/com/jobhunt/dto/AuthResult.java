package com.jobhunt.dto;

/**
 * Internal carrier for an authentication response plus the raw refresh token, which the
 * controller places in an httpOnly cookie and never in the response body.
 */
public record AuthResult(AuthResponse response, String refreshToken, long refreshTtlMs) {
}

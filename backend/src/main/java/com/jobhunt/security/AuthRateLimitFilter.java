package com.jobhunt.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhunt.dto.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fixed-window rate limiter for the credential endpoints.
 *
 * <p>Deliberately in-memory and dependency-free: it protects a single instance against
 * brute force and credential stuffing. A multi-instance deployment should move the counter
 * to Redis or a gateway. The key is the client IP plus the path, so hammering /login does
 * not lock a user out of /refresh.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    /** Endpoints worth protecting. Everything else is authenticated already. */
    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh");

    /** Guards against unbounded memory if a flood of unique IPs arrives. */
    private static final int MAX_TRACKED_KEYS = 20_000;

    private final ObjectMapper objectMapper;
    private final int maxAttempts;
    private final long windowMs;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(ObjectMapper objectMapper,
                               @Value("${app.security.login-rate-limit.max-attempts:10}") int maxAttempts,
                               @Value("${app.security.login-rate-limit.window-seconds:900}") long windowSeconds) {
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts;
        this.windowMs = windowSeconds * 1000L;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        pruneIfNeeded();

        String key = clientIp(request) + '|' + path;
        long now = System.currentTimeMillis();

        Window window = windows.compute(key, (ignored, existing) ->
                (existing == null || now - existing.startMs >= windowMs) ? new Window(now) : existing);

        int attempts = window.count.incrementAndGet();
        if (attempts > maxAttempts) {
            long retryAfterSeconds = Math.max(1, (window.startMs + windowMs - now) / 1000);
            log.warn("Rate limit exceeded for {} on {}", clientIp(request), path);
            writeTooManyRequests(response, request, retryAfterSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void pruneIfNeeded() {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(entry -> now - entry.getValue().startMs >= windowMs);
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.clear();
        }
    }

    /** Honours the first X-Forwarded-For hop when running behind a reverse proxy. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response,
                                      HttpServletRequest request,
                                      long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Too many attempts. Please try again in " + retryAfterSeconds + " seconds.",
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static final class Window {
        private final long startMs;
        private final AtomicInteger count = new AtomicInteger();

        private Window(long startMs) {
            this.startMs = startMs;
        }
    }
}

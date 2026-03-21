package com.zone.tasksphere.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NFR-05 / SEC-02: HTTP-level Rate Limiting using Bucket4j (token bucket algorithm).
 *
 * Rules:
 *   - /api/v1/auth/login  → 20 req/min per IP  (brute-force protection)
 *   - /api/v1/auth/signup → 10 req/min per IP  (prevent mass registration)
 *   - Any authenticated endpoint → 100 req/min per userId (extracted from JWT claim "userId")
 *
 * On limit exceeded: HTTP 429 + Retry-After header.
 */
@Component
@Slf4j
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Buckets keyed by "login:<ip>", "signup:<ip>", "user:<userId>" */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // ── Capacity constants ───────────────────────────────────────────────────
    private static final int LOGIN_CAPACITY  = 20;
    private static final int SIGNUP_CAPACITY = 10;
    private static final int USER_CAPACITY   = 100;

    // ── Path prefixes ────────────────────────────────────────────────────────
    private static final String LOGIN_PATH  = "/api/v1/auth/login";
    private static final String SIGNUP_PATH = "/api/v1/auth/signup";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        String path = request.getRequestURI();
        String ip   = resolveClientIp(request);

        // Rule 1: login endpoint → 20 req/min per IP
        if (path.startsWith(LOGIN_PATH)) {
            Bucket bucket = resolveBucket("login:" + ip, LOGIN_CAPACITY, Duration.ofMinutes(1));
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded on login for IP={}", ip);
                writeRateLimitResponse(response,
                        "Quá nhiều yêu cầu đăng nhập. Vui lòng thử lại sau 60 giây.");
                return false;
            }
        }

        // Rule 2: signup endpoint → 10 req/min per IP
        if (path.startsWith(SIGNUP_PATH)) {
            Bucket bucket = resolveBucket("signup:" + ip, SIGNUP_CAPACITY, Duration.ofMinutes(1));
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded on signup for IP={}", ip);
                writeRateLimitResponse(response,
                        "Quá nhiều yêu cầu đăng ký. Vui lòng thử lại sau 60 giây.");
                return false;
            }
        }

        // Rule 3: authenticated endpoints → 100 req/min per userId
        String userId = extractUserIdFromAuthHeader(request);
        if (userId != null) {
            Bucket bucket = resolveBucket("user:" + userId, USER_CAPACITY, Duration.ofMinutes(1));
            if (!bucket.tryConsume(1)) {
                log.warn("Rate limit exceeded for userId={}", userId);
                writeRateLimitResponse(response,
                        "Vượt quá giới hạn 100 yêu cầu/phút. Vui lòng thử lại sau.");
                return false;
            }
        }

        return true;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * Returns or creates a greedy token-bucket with the given capacity refilled every period.
     */
    private Bucket resolveBucket(String key, int capacity, Duration period) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder()
                        .addLimit(Bandwidth.classic(capacity,
                                Refill.intervally(capacity, period)))
                        .build()
        );
    }

    /**
     * Extracts the "userId" claim from the Authorization: Bearer <jwt> header WITHOUT
     * full validation — we only need the subject for bucket keying. If parsing fails, returns null.
     */
    private String extractUserIdFromAuthHeader(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        try {
            String token = auth.substring(7);
            // JWT payload is base64url-encoded between the two dots
            String[] parts = token.split("\\.");
            if (parts.length < 2) return null;
            String payloadJson = new String(
                    java.util.Base64.getUrlDecoder().decode(parts[1]));
            // Quick JSON field extraction — avoids heavy Jackson parse for every request
            int idx = payloadJson.indexOf("\"userId\"");
            if (idx < 0) return null;
            int start = payloadJson.indexOf('"', idx + 8) + 1;
            int end   = payloadJson.indexOf('"', start);
            if (start <= 0 || end <= start) return null;
            return payloadJson.substring(start, end);
        } catch (Exception e) {
            return null; // malformed token — let JWTFilter handle the real rejection
        }
    }

    /**
     * Resolves real client IP honoring X-Forwarded-For (for reverse proxy deployments).
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeRateLimitResponse(HttpServletResponse response, String message)
            throws IOException {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Retry-After", "60");
        response.getWriter().write(
                "{\"statusCode\":429,\"message\":\"" + message + "\",\"data\":null}");
    }
}

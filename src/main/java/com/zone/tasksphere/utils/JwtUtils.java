package com.zone.tasksphere.utils;

import com.zone.tasksphere.exception.CustomAuthenticationException;
import com.zone.tasksphere.security.CustomUserDetail;
import com.zone.tasksphere.security.CustomUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class JwtUtils {

    @Value("${security.jwt.secret-key}")
    private String secret;

    @Value("${security.jwt.issuer}")
    private String issuer;

    @Value("${security.jwt.expiry-time-in-seconds}")
    private Long accessExpiration;

    @Value("${security.jwt.refreshable-duration}")
    private Long refreshExpiration;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    private static final String TOKEN_PREFIX = "revoked_token:";
    private static final String BEARER_PREFIX = "Bearer ";

    public String generateAccessToken(UserDetails userDetails) {
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetails;

        Map<String, Object> claims = new HashMap<>();
        // FIX: NFR-04 - Không chứa sensitive data (email đã loại bỏ, chỉ giữ userId + role)
        claims.put("userId", customUserDetail.getUserDetail().getId().toString());
        claims.put("role",   customUserDetail.getUserDetail().getSystemRole() != null
                ? customUserDetail.getUserDetail().getSystemRole().name() : null);
        claims.put("status", customUserDetail.getUserDetail().getStatus() != null
                ? customUserDetail.getUserDetail().getStatus().name() : null);
        claims.put("jti",    UUID.randomUUID().toString());
        return buildToken(customUserDetail.getUsername(), claims, accessExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", UUID.randomUUID().toString()); // FIX: Bug2 - jti để revoke theo ID thay vì full token
        return buildToken(userDetails.getUsername(), claims, refreshExpiration);
    }

    public Authentication setAuthentication(String token) {
        Claims payload = parseClaimsFromToken(token);
        String username = payload.getSubject();
        CustomUserDetail customUserDetail = (CustomUserDetail) userDetailsService.loadUserByUsername(username);
        return new UsernamePasswordAuthenticationToken(customUserDetail, "", customUserDetail.getAuthorities());
    }

    public boolean validateToken(String token) {
        try {
            parseClaimsFromToken(token);
            return !isTokenExpired(token) && !isTokenRevoked(token);
        } catch (JwtException e) {
            return false;
        } catch (Exception e) {
            // FIX: Redis không khả dụng → chỉ validate expiry từ JWT payload (không cần Redis)
            // Trước đây return false → kick user khi Redis restart. Giờ fallback về JWT expiry.
            log.warn("Redis không khả dụng, bỏ qua kiểm tra revocation: {}", e.getMessage());
            return !isTokenExpired(token);
        }
    }

    public String extractUsername(String token) {
        return parseClaimsFromToken(token).getSubject();
    }

    /** FIX: Bug2 - trích xuất jti để dùng làm Redis key thay vì full token string */
    public String extractJti(String token) {
        Object jti = parseClaimsFromToken(token).get("jti");
        return jti != null ? jti.toString() : null;
    }

    public boolean isTokenRevoked(String token) {
        try {
            // FIX: Bug2 - dùng jti (~36 chars) thay vì full JWT (~300+ chars) làm Redis key
            String jti = extractJti(token);
            if (jti == null) return false; // token cũ không có jti, không thể kiểm tra
            return Boolean.TRUE.equals(redisTemplate.hasKey(TOKEN_PREFIX + jti));
        } catch (Exception e) {
            // FIX: Bug1 - không để Redis exception bubble lên JwtUtils.validateToken
            log.warn("Không thể kiểm tra revocation từ Redis: {}", e.getMessage());
            return false;
        }
    }

    public void revokeToken(String token) {
        if (token == null) {
            throw new CustomAuthenticationException("Token không hợp lệ");
        }
        try {
            Claims claims = parseClaimsFromToken(token);
            // FIX: Bug2 - xử lý token đã expired (không cần revoke, bỏ qua gracefully)
            if (claims.getExpiration().before(new Date())) {
                log.debug("Token đã hết hạn, bỏ qua revoke");
                return;
            }
            // FIX: Bug2 - dùng jti làm Redis key thay vì full token string
            String jti = (String) claims.get("jti");
            if (jti == null) {
                log.warn("Token không có jti, bỏ qua revoke vào Redis");
                return;
            }
            long remainingTime = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remainingTime > 0) {
                redisTemplate.opsForValue().set(TOKEN_PREFIX + jti, "revoked",
                        remainingTime, TimeUnit.MILLISECONDS);
            }
        } catch (JwtException e) {
            throw new CustomAuthenticationException("Token không hợp lệ");
        }
    }

    private String buildToken(String subject, Map<String, Object> claims, long expiration) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + (expiration * 1000)))
                .signWith(getSecretKey())
                .compact();
    }

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseClaimsFromToken(token).getExpiration().before(new Date());
        } catch (JwtException e) {
            return true;
        }
    }

    public String extractBearerToken(String bearerToken) {
        if (StringUtils.hasText(bearerToken) && bearerToken.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return bearerToken.substring(7);
        }
        return null;
    }
}

package com.zone.tasksphere.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaService {

    @Value("${app.turnstile.enabled:true}")
    private boolean enabled;

    @Value("${app.turnstile.secret-key:}")
    private String secretKey;

    @Value("${app.turnstile.url:https://challenges.cloudflare.com/turnstile/v0/siteverify}")
    private String verifyUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean verifyCaptcha(String token, HttpServletRequest servletRequest) {
        if (!enabled) {
            return true;
        }

        if (secretKey == null || secretKey.isBlank()) {
            log.warn("Turnstile verification is enabled but no secret key is configured.");
            return false;
        }

        if (token == null || token.isBlank()) {
            log.warn("Turnstile token is missing.");
            return false;
        }

        // --- LẤY IP CLIENT CHUẨN SAU PROXY ---
        String clientIp = getClientIp(servletRequest);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("secret", secretKey.trim());
        body.add("response", token);
        body.add("remoteip", clientIp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request =
                new HttpEntity<>(body, headers);

        TurnstileResponse response;
        try {
            response = restTemplate.postForObject(
                    verifyUrl,
                    request,
                    TurnstileResponse.class
            );
        } catch (Exception e) {
            log.error("Turnstile verification request failed: {}", e.getMessage(), e);
            return false;
        }

        if (response == null) return false;

        if (!response.success) {
            log.warn("Turnstile verification failed with error codes: {}", response.errorCodes);
        }

        return response.success;
    }

    // 🔥 Lấy IP thật khi deploy sau nginx / load balancer
    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0];
        }
        return request.getRemoteAddr();
    }

    @Data
    private static class TurnstileResponse {
        private boolean success;

        @JsonProperty("error-codes")
        private List<String> errorCodes;
    }
}

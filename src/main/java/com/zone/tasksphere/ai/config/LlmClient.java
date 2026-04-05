package com.zone.tasksphere.ai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Thin wrapper around the Google Gemini generateContent API.
 * Model: gemini-2.0-flash-lite (v1beta — required for systemInstruction support)
 * Retries: 3 total attempts; on 429 the retryDelay from the response body is honoured,
 * otherwise falls back to 20 s → 40 s exponential back-off.
 *
 * Required config:  ai.gemini.api-key=${GEMINI_API_KEY}
 */
@Slf4j
@Component
public class LlmClient {

    private static final String GEMINI_URL        = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-lite:generateContent";
    private static final float  TEMPERATURE       = 0.7f;
    private static final int    MAX_OUTPUT_TOKENS = 4096;
    private static final MediaType JSON           = MediaType.get("application/json; charset=utf-8");
    /** Fallback delays used when the API does not supply a retryDelay (non-429 errors). */
    private static final long[] FALLBACK_DELAYS_MS = {20_000L, 40_000L};

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public LlmClient(
            @Value("${ai.gemini.api-key}") String apiKey,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public String call(String systemPrompt, String userMessage) {
        String body = buildBody(systemPrompt, userMessage);
        RateLimitException lastRateLimit = null;
        IOException lastErr = null;

        for (int attempt = 0; attempt <= FALLBACK_DELAYS_MS.length; attempt++) {
            if (attempt > 0) {
                long delayMs = (lastRateLimit != null)
                        ? lastRateLimit.retryAfterMs
                        : FALLBACK_DELAYS_MS[attempt - 1];
                log.warn("[LLM] attempt {}/{} failed, retrying in {} ms",
                        attempt, FALLBACK_DELAYS_MS.length + 1, delayMs);
                sleep(delayMs);
                lastRateLimit = null;
            }
            try {
                return execute(body);
            } catch (RateLimitException e) {
                lastRateLimit = e;
                lastErr = e;
                log.warn("[LLM] attempt {} rate-limited (429), retryAfter={} ms", attempt + 1, e.retryAfterMs);
            } catch (IOException e) {
                lastErr = e;
                log.error("[LLM] attempt {} error: {}", attempt + 1, e.getMessage());
            }
        }
        throw new LlmException("LLM failed after " + (FALLBACK_DELAYS_MS.length + 1) + " attempts", lastErr);
    }

    private String execute(String body) throws IOException {
        HttpUrl url = HttpUrl.parse(GEMINI_URL).newBuilder()
                .addQueryParameter("key", apiKey)
                .build();

        Request req = new Request.Builder()
                .url(url)
                .header("content-type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response res = http.newCall(req).execute()) {
            String raw = res.body() != null ? res.body().string() : "";
            if (res.code() == 429) {
                long retryAfterMs = parseRetryAfterMs(raw);
                throw new RateLimitException(retryAfterMs, raw);
            }
            if (!res.isSuccessful()) {
                throw new IOException("Gemini API HTTP " + res.code() + ": " + raw);
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode text = root.path("candidates").path(0)
                                .path("content").path("parts").path(0)
                                .path("text");
            if (!text.isMissingNode() && !text.asText().isBlank()) {
                return text.asText();
            }
            throw new IOException("Unexpected Gemini response: " + raw);
        }
    }

    /**
     * Parses retryDelay from the Gemini 429 response body.
     * Format: {@code "details": [..., {"@type": "...RetryInfo", "retryDelay": "21s"}]}
     * Falls back to 30 s if the field is absent or unparseable.
     */
    private long parseRetryAfterMs(String raw) {
        try {
            JsonNode details = objectMapper.readTree(raw).path("error").path("details");
            if (details.isArray()) {
                for (JsonNode detail : details) {
                    JsonNode retryDelay = detail.path("retryDelay");
                    if (!retryDelay.isMissingNode()) {
                        String val = retryDelay.asText().trim(); // e.g. "21s" or "21.5s"
                        val = val.replaceAll("[^0-9.]", "");
                        double seconds = Double.parseDouble(val);
                        // Add a 2-second buffer on top of what the API requests
                        return (long)(seconds * 1000) + 2_000L;
                    }
                }
            }
        } catch (Exception ignored) { }
        return 30_000L; // safe default
    }

    private String buildBody(String system, String user) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // systemInstruction
            ObjectNode sysInstruction = objectMapper.createObjectNode();
            ArrayNode  sysParts       = objectMapper.createArrayNode();
            ObjectNode sysPart        = objectMapper.createObjectNode();
            sysPart.put("text", system);
            sysParts.add(sysPart);
            sysInstruction.set("parts", sysParts);
            root.set("systemInstruction", sysInstruction);

            // contents
            ArrayNode  contents = objectMapper.createArrayNode();
            ObjectNode content  = objectMapper.createObjectNode();
            content.put("role", "user");
            ArrayNode  parts    = objectMapper.createArrayNode();
            ObjectNode part     = objectMapper.createObjectNode();
            part.put("text", user);
            parts.add(part);
            content.set("parts", parts);
            contents.add(content);
            root.set("contents", contents);

            // generationConfig
            ObjectNode genConfig = objectMapper.createObjectNode();
            genConfig.put("temperature", TEMPERATURE);
            genConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);
            root.set("generationConfig", genConfig);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to serialize LLM request", e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new LlmException("Interrupted", e); }
    }

    private static class RateLimitException extends IOException {
        final long retryAfterMs;
        RateLimitException(long retryAfterMs, String body) {
            super("Gemini API HTTP 429: " + body);
            this.retryAfterMs = retryAfterMs;
        }
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String msg, Throwable cause) { super(msg, cause); }
    }
}

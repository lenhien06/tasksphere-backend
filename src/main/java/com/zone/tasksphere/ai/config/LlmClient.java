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
 * Multi-provider LLM client with Gemini as primary and Groq as automatic fallback.
 *
 * Primary:
 *   - Gemini generateContent API (gemini-2.5-flash)
 * Fallback:
 *   - Groq OpenAI-compatible chat completions API
 *
 * Required config:
 *   ai.gemini.api-key=${GEMINI_API_KEY:}
 *   ai.groq.api-key=${GROQ_API_KEY:}
 *   ai.groq.model=${GROQ_MODEL:llama-3.1-70b-versatile}
 */
@Slf4j
@Component
public class LlmClient {

    private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GEMINI_MODEL_NAME = "gemini-2.5-flash";
    private static final float TEMPERATURE = 0.7f;
    private static final int MAX_OUTPUT_TOKENS = 8192;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final long[] GEMINI_RETRY_DELAYS_MS = {};
    private static final long[] GROQ_RETRY_DELAYS_MS = {};

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String geminiApiKey;
    private final String groqApiKey;
    private final String groqModel;

    public LlmClient(
            @Value("${ai.gemini.api-key:}") String geminiApiKey,
            @Value("${ai.groq.api-key:}") String groqApiKey,
            @Value("${ai.groq.model:llama-3.1-70b-versatile}") String groqModel,
            ObjectMapper objectMapper) {
        this.geminiApiKey = geminiApiKey;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
        this.objectMapper = objectMapper;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public boolean isAvailable() {
        return hasText(geminiApiKey) || hasText(groqApiKey);
    }

    public String call(String systemPrompt, String userMessage) {
        if (!isAvailable()) {
            throw new LlmException("No AI provider is configured", null);
        }

        IOException lastErr = null;

        if (hasText(geminiApiKey)) {
            try {
                return callGemini(systemPrompt, userMessage);
            } catch (IOException e) {
                lastErr = e;
                if (hasText(groqApiKey)) {
                    log.warn("[LLM] Gemini unavailable, switching to Groq fallback: {}", e.getMessage());
                } else {
                    log.error("[LLM] Gemini failed and no fallback provider is configured: {}", e.getMessage());
                }
            }
        }

        if (hasText(groqApiKey)) {
            try {
                return callGroq(systemPrompt, userMessage);
            } catch (IOException e) {
                lastErr = e;
                log.error("[LLM] Groq fallback failed: {}", e.getMessage());
            }
        }

        throw new LlmException("All AI providers failed", lastErr);
    }

    private String callGemini(String systemPrompt, String userMessage) throws IOException {
        String body = buildGeminiBody(systemPrompt, userMessage);
        RetryableProviderException lastRetryable = null;
        IOException lastErr = null;

        for (int attempt = 0; attempt <= GEMINI_RETRY_DELAYS_MS.length; attempt++) {
            if (attempt > 0) {
                long delayMs = (lastRetryable != null)
                        ? lastRetryable.retryAfterMs
                        : GEMINI_RETRY_DELAYS_MS[attempt - 1];
                log.warn("[LLM][Gemini] attempt {}/{} failed, retrying in {} ms",
                        attempt, GEMINI_RETRY_DELAYS_MS.length + 1, delayMs);
                sleep(delayMs);
                lastRetryable = null;
            }
            try {
                return executeGemini(body);
            } catch (RetryableProviderException e) {
                lastRetryable = e;
                lastErr = e;
                log.warn("[LLM][Gemini] attempt {} retryable error (HTTP {}), retryAfter={} ms",
                        attempt + 1, e.statusCode, e.retryAfterMs);
            } catch (IOException e) {
                lastErr = e;
                log.error("[LLM][Gemini] attempt {} error: {}", attempt + 1, e.getMessage());
            }
        }

        throw new IOException("Gemini failed after " + (GEMINI_RETRY_DELAYS_MS.length + 1) + " attempts", lastErr);
    }

    private String executeGemini(String body) throws IOException {
        HttpUrl url = HttpUrl.parse(GEMINI_URL).newBuilder()
                .addQueryParameter("key", geminiApiKey)
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
                throw new RetryableProviderException("Gemini", res.code(), retryAfterMs, raw);
            }
            if (res.code() == 503) {
                throw new RetryableProviderException("Gemini", res.code(), 5_000L, raw);
            }
            if (!res.isSuccessful()) {
                throw new IOException("Gemini API HTTP " + res.code() + ": " + raw);
            }
            JsonNode root         = objectMapper.readTree(raw);
            JsonNode candidate    = root.path("candidates").path(0);
            String   finishReason = candidate.path("finishReason").asText("");
            if ("MAX_TOKENS".equals(finishReason)) {
                throw new IOException("Gemini response truncated (MAX_TOKENS). Consider reducing output size.");
            }
            JsonNode text = candidate.path("content").path("parts").path(0).path("text");
            if (!text.isMissingNode() && !text.asText().isBlank()) {
                return stripMarkdownFences(text.asText());
            }
            throw new IOException("Unexpected Gemini response: " + raw);
        }
    }

    private String callGroq(String systemPrompt, String userMessage) throws IOException {
        String body = buildGroqBody(systemPrompt, userMessage);
        RetryableProviderException lastRetryable = null;
        IOException lastErr = null;

        for (int attempt = 0; attempt <= GROQ_RETRY_DELAYS_MS.length; attempt++) {
            if (attempt > 0) {
                long delayMs = (lastRetryable != null)
                        ? lastRetryable.retryAfterMs
                        : GROQ_RETRY_DELAYS_MS[attempt - 1];
                log.warn("[LLM][Groq] attempt {}/{} failed, retrying in {} ms",
                        attempt, GROQ_RETRY_DELAYS_MS.length + 1, delayMs);
                sleep(delayMs);
                lastRetryable = null;
            }
            try {
                return executeGroq(body);
            } catch (RetryableProviderException e) {
                lastRetryable = e;
                lastErr = e;
                log.warn("[LLM][Groq] attempt {} retryable error (HTTP {}), retryAfter={} ms",
                        attempt + 1, e.statusCode, e.retryAfterMs);
            } catch (IOException e) {
                lastErr = e;
                log.error("[LLM][Groq] attempt {} error: {}", attempt + 1, e.getMessage());
            }
        }

        throw new IOException("Groq failed after " + (GROQ_RETRY_DELAYS_MS.length + 1) + " attempts", lastErr);
    }

    private String executeGroq(String body) throws IOException {
        Request req = new Request.Builder()
                .url(GROQ_URL)
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + groqApiKey)
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response res = http.newCall(req).execute()) {
            String raw = res.body() != null ? res.body().string() : "";
            if (res.code() == 429) {
                throw new RetryableProviderException("Groq", res.code(), 5_000L, raw);
            }
            if (res.code() == 503) {
                throw new RetryableProviderException("Groq", res.code(), 5_000L, raw);
            }
            if (!res.isSuccessful()) {
                throw new IOException("Groq API HTTP " + res.code() + ": " + raw);
            }

            JsonNode root = objectMapper.readTree(raw);
            JsonNode message = root.path("choices").path(0).path("message").path("content");
            if (!message.isMissingNode() && !message.asText().isBlank()) {
                return stripMarkdownFences(message.asText());
            }
            throw new IOException("Unexpected Groq response: " + raw);
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

    private String buildGeminiBody(String system, String user) {
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
            // Disable extended thinking to reduce latency
            ObjectNode thinkingConfig = objectMapper.createObjectNode();
            thinkingConfig.put("thinkingBudget", 0);
            genConfig.set("thinkingConfig", thinkingConfig);
            root.set("generationConfig", genConfig);

            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to serialize LLM request", e);
        }
    }

    private String buildGroqBody(String system, String user) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", groqModel);
            root.put("temperature", TEMPERATURE);
            root.put("max_tokens", MAX_OUTPUT_TOKENS);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", system);
            messages.add(systemMessage);

            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", user);
            messages.add(userMessage);

            root.set("messages", messages);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to serialize Groq request", e);
        }
    }

    /** Strips ```json ... ``` or ``` ... ``` fences the model sometimes adds despite instructions. */
    private static String stripMarkdownFences(String text) {
        String trimmed = text.strip();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
            }
        }
        return trimmed;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new LlmException("Interrupted", e); }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static class RetryableProviderException extends IOException {
        final int statusCode;
        final long retryAfterMs;
        RetryableProviderException(String provider, int statusCode, long retryAfterMs, String body) {
            super(provider + " API HTTP " + statusCode + ": " + body);
            this.statusCode = statusCode;
            this.retryAfterMs = retryAfterMs;
        }
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String msg, Throwable cause) { super(msg, cause); }
    }
}

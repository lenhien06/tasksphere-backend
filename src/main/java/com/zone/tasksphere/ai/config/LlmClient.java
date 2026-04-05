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
 * Thin wrapper around the Anthropic Messages API.
 * Model: claude-sonnet-4-20250514
 * Retries: 3 total attempts with 2 s → 4 s back-off.
 *
 * Required config:  ai.anthropic.api-key=${ANTHROPIC_API_KEY}
 */
@Slf4j
@Component
public class LlmClient {

    private static final String ANTHROPIC_URL     = "https://api.anthropic.com/v1/messages";
    private static final String MODEL             = "claude-sonnet-4-20250514";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int    MAX_TOKENS        = 4096;
    private static final MediaType JSON           = MediaType.get("application/json; charset=utf-8");
    private static final long[]  RETRY_DELAYS_MS  = {2_000L, 4_000L};

    private final OkHttpClient http;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public LlmClient(
            @Value("${ai.anthropic.api-key}") String apiKey,
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
        IOException lastErr = null;

        for (int attempt = 0; attempt <= RETRY_DELAYS_MS.length; attempt++) {
            if (attempt > 0) {
                log.warn("[LLM] attempt {}/{} failed, retrying in {} ms",
                        attempt, RETRY_DELAYS_MS.length + 1, RETRY_DELAYS_MS[attempt - 1]);
                sleep(RETRY_DELAYS_MS[attempt - 1]);
            }
            try {
                return execute(body);
            } catch (IOException e) {
                lastErr = e;
                log.error("[LLM] attempt {} error: {}", attempt + 1, e.getMessage());
            }
        }
        throw new LlmException("LLM failed after " + (RETRY_DELAYS_MS.length + 1) + " attempts", lastErr);
    }

    private String execute(String body) throws IOException {
        Request req = new Request.Builder()
                .url(ANTHROPIC_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("content-type", "application/json")
                .post(RequestBody.create(body, JSON))
                .build();

        try (Response res = http.newCall(req).execute()) {
            String raw = res.body() != null ? res.body().string() : "";
            if (!res.isSuccessful()) {
                throw new IOException("Anthropic API HTTP " + res.code() + ": " + raw);
            }
            JsonNode root    = objectMapper.readTree(raw);
            JsonNode content = root.path("content");
            if (content.isArray() && !content.isEmpty()) {
                String text = content.get(0).path("text").asText();
                if (!text.isBlank()) return text;
            }
            throw new IOException("Unexpected Anthropic response: " + raw);
        }
    }

    private String buildBody(String system, String user) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", MODEL);
            root.put("max_tokens", MAX_TOKENS);
            root.put("system", system);
            ArrayNode msgs = objectMapper.createArrayNode();
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", "user");
            msg.put("content", user);
            msgs.add(msg);
            root.set("messages", msgs);
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new LlmException("Failed to serialize LLM request", e);
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new LlmException("Interrupted", e); }
    }

    public static class LlmException extends RuntimeException {
        public LlmException(String msg, Throwable cause) { super(msg, cause); }
    }
}

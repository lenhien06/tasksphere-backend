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
 * Model: gemini-2.0-flash
 * Retries: 3 total attempts with 2 s → 4 s back-off.
 *
 * Required config:  ai.gemini.api-key=${GEMINI_API_KEY}
 */
@Slf4j
@Component
public class LlmClient {

    private static final String GEMINI_URL      = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
    private static final float  TEMPERATURE     = 0.7f;
    private static final int    MAX_OUTPUT_TOKENS = 4096;
    private static final MediaType JSON          = MediaType.get("application/json; charset=utf-8");
    private static final long[]  RETRY_DELAYS_MS = {2_000L, 4_000L};

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
            ArrayNode  contents  = objectMapper.createArrayNode();
            ObjectNode content   = objectMapper.createObjectNode();
            content.put("role", "user");
            ArrayNode  parts     = objectMapper.createArrayNode();
            ObjectNode part      = objectMapper.createObjectNode();
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

    public static class LlmException extends RuntimeException {
        public LlmException(String msg, Throwable cause) { super(msg, cause); }
    }
}

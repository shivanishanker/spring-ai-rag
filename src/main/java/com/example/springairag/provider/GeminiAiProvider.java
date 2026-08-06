package com.example.springairag.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service("geminiAiProvider")
@ConditionalOnProperty(name = "ai.provider", havingValue = "gemini")
public class GeminiAiProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(GeminiAiProvider.class);
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.gemini.api-key:}")
    private String apiKey;

    @Value("${ai.gemini.chat-model:gemini-2.5-flash}")
    private String chatModel;

    @Value("${ai.gemini.embedding-model:gemini-embedding-001}")
    private String embeddingModel;

    public GeminiAiProvider(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateAnswer(String systemPrompt, String contextDocs, String userQuery) {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text",
                        systemPrompt + "\n\n--- REFERENCE KNOWLEDGE BASE CONTEXT ---\n" + contextDocs))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userQuery)))));
        try {
            log.info("Sending request to Gemini chat model {}", chatModel);
            String response = post(chatModel + ":generateContent", body);
            JsonNode text = objectMapper.readTree(response)
                    .path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (text.isTextual()) {
                return text.asText();
            }
            throw new IllegalStateException("Gemini response does not contain candidate text");
        } catch (Exception exception) {
            log.error("Failed to generate an answer with Gemini", exception);
            throw new AiProviderException("Failed to generate an answer with Gemini", exception);
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
        requireApiKey();
        Map<String, Object> body = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text))));
        try {
            String response = post(embeddingModel + ":embedContent", body);
            JsonNode values = objectMapper.readTree(response).path("embedding").path("values");
            if (!values.isArray() || values.isEmpty()) {
                throw new IllegalStateException("Gemini response does not contain embedding values");
            }
            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }
            return embedding;
        } catch (Exception exception) {
            log.error("Failed to generate an embedding with Gemini", exception);
            throw new AiProviderException("Failed to generate an embedding with Gemini", exception);
        }
    }

    @Override
    public String getProviderName() {
        return "gemini";
    }

    private String post(String operation, Map<String, Object> body) {
        return restClient.post()
                .uri(API_BASE_URL + operation + "?key={apiKey}", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new AiProviderException("GEMINI_API_KEY is not configured");
        }
    }
}

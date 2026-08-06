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

@Service("ollamaAiProvider")
@ConditionalOnProperty(name = "ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAiProvider implements AiProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaAiProvider.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.ollama.chat-model:mistral}")
    private String chatModel;

    @Value("${ai.ollama.embedding-model:nomic-embed-text}")
    private String embeddingModel;

    public OllamaAiProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${ai.ollama.base-url:http://localhost:11434}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.objectMapper = objectMapper;
    }

    @Override
    public String generateAnswer(String systemPrompt, String contextDocs, String userQuery) {
        Map<String, Object> body = Map.of(
                "model", chatModel,
                "stream", false,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt
                                + "\n\n--- REFERENCE KNOWLEDGE BASE CONTEXT ---\n" + contextDocs),
                        Map.of("role", "user", "content", userQuery)));
        try {
            log.info("Sending request to Ollama chat model {}", chatModel);
            String response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode content = objectMapper.readTree(response).path("message").path("content");
            if (content.isTextual()) {
                return content.asText();
            }
            throw new IllegalStateException("Ollama response does not contain message content");
        } catch (Exception exception) {
            log.error("Failed to generate an answer with Ollama", exception);
            throw new AiProviderException("Failed to generate an answer with Ollama", exception);
        }
    }

    @Override
    public float[] generateEmbedding(String text) {
        Map<String, Object> body = Map.of("model", embeddingModel, "input", text);
        try {
            String response = restClient.post()
                    .uri("/api/embed")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode values = null;

            // Support multiple possible response shapes from Ollama:
            // 1) { "embeddings": [ [ ... ] ] }
            // 2) { "embedding": [ ... ] }
            // 3) [ ... ] (top-level array)
            // 4) { "embeddings": [ { "embedding": [ ... ] } ] }
            if (root.has("embeddings")) {
                JsonNode embeddingsNode = root.path("embeddings");
                if (embeddingsNode.isArray() && embeddingsNode.size() > 0) {
                    JsonNode first = embeddingsNode.get(0);
                    if (first.isArray()) {
                        values = first;
                    } else if (first.has("embedding")) {
                        values = first.path("embedding");
                    }
                }
            }

            if (values == null && root.has("embedding")) {
                values = root.path("embedding");
            }

            if (values == null && root.isArray()) {
                values = root;
            }

            if (values == null || !values.isArray() || values.isEmpty()) {
                log.error("Unexpected Ollama embed response: {}", response);
                throw new IllegalStateException("Ollama response does not contain embedding values");
            }

            float[] embedding = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                embedding[i] = (float) values.get(i).asDouble();
            }
            return embedding;
        } catch (Exception exception) {
            log.error("Failed to generate an embedding with Ollama", exception);
            throw new AiProviderException("Failed to generate an embedding with Ollama", exception);
        }
    }

    @Override
    public String getProviderName() {
        return "ollama";
    }
}

package com.example.springairag.provider;

public interface AiProvider {
    String generateAnswer(String systemPrompt, String contextDocs, String userQuery);

    float[] generateEmbedding(String text);

    String getProviderName();
}

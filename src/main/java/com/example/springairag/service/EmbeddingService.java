package com.example.springairag.service;

import com.example.springairag.provider.AiProvider;
import org.springframework.stereotype.Service;

@Service
public class EmbeddingService {

    private final AiProvider aiProvider;

    public EmbeddingService(AiProvider aiProvider) {
        this.aiProvider = aiProvider;
    }

    public float[] generateEmbedding(String text) {
        return aiProvider.generateEmbedding(text);
    }
}

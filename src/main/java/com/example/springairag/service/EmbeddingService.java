package com.example.springairag.service;

import org.springframework.ai.embedding.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

   public float[] generateEmbedding(String text) {

    EmbeddingResponse response =
            embeddingModel.embedForResponse(List.of(text));

    List<Double> list = response.getResults().get(0).getOutput();

    float[] vector = new float[list.size()];

    for (int i = 0; i < list.size(); i++) {
        vector[i] = list.get(i).floatValue();
    }

    return vector;
    }
}
package com.rag.openai.client.ollama;

import java.util.List;
import java.util.Objects;

/**
 * Represents a response from Ollama for embedding generation.
 * 
 * @param embedding The vector embedding
 */
public record OllamaEmbeddingResponse(
    List<Float> embedding
) {
    public OllamaEmbeddingResponse {
        Objects.requireNonNull(embedding, "Embedding must not be null");
        if (embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding must not be empty");
        }
    }
}

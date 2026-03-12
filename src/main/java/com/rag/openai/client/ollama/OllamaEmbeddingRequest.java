package com.rag.openai.client.ollama;

import java.util.Objects;

/**
 * Represents a request to Ollama for embedding generation.
 * 
 * @param model The embedding model to use
 * @param prompt The text to generate an embedding for
 */
public record OllamaEmbeddingRequest(
    String model,
    String prompt
) {
    public OllamaEmbeddingRequest {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(prompt, "Prompt must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("Model must not be blank");
        }
    }
}

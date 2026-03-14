package com.rag.openai.client.ollama;

import java.util.Objects;

/**
 * Represents an OpenAI-compatible request to Ollama for embedding generation.
 *
 * @param input The text to generate an embedding for
 * @param model The embedding model to use
 * @param encoding_format The format of the embedding output (e.g., "float")
 */
public record OllamaEmbeddingRequest(
    String input,
    String model,
    String encoding_format
) {
    public OllamaEmbeddingRequest {
        Objects.requireNonNull(input, "Input must not be null");
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(encoding_format, "Encoding format must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("Model must not be blank");
        }
    }
}

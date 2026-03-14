package com.rag.openai.client.ollama;

import java.util.List;
import java.util.Objects;

/**
 * Represents an OpenAI-compatible response from Ollama for embedding generation.
 *
 * @param object The object type (e.g., "list")
 * @param data List of embedding data entries
 * @param model The model used for embedding generation
 * @param usage Token usage information
 */
public record OllamaEmbeddingResponse(
    String object,
    List<EmbeddingData> data,
    String model,
    Usage usage
) {
    public OllamaEmbeddingResponse {
        Objects.requireNonNull(data, "Embedding data must not be null");
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Embedding data must not be empty");
        }
    }

    public record EmbeddingData(
        String object,
        int index,
        List<Float> embedding
    ) {}

    public record Usage(
        int prompt_tokens,
        int total_tokens
    ) {}
}

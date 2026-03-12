package com.rag.openai.client.ollama;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a request to Ollama for text generation.
 * 
 * @param model The model to use for generation
 * @param prompt The prompt to generate from
 * @param stream Whether to stream the response
 * @param options Optional generation parameters
 */
public record OllamaGenerateRequest(
    String model,
    String prompt,
    boolean stream,
    Optional<OllamaOptions> options
) {
    public OllamaGenerateRequest {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(prompt, "Prompt must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("Model must not be blank");
        }
    }
}

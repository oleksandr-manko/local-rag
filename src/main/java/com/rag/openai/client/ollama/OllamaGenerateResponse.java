package com.rag.openai.client.ollama;

import java.util.Objects;

/**
 * Represents a response from Ollama for text generation.
 * 
 * @param model The model used for generation
 * @param response The generated text
 * @param done Whether generation is complete
 */
public record OllamaGenerateResponse(
    String model,
    String response,
    boolean done
) {
    public OllamaGenerateResponse {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(response, "Response must not be null");
    }
}

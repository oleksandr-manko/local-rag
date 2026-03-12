package com.rag.openai.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Optional;

/**
 * Represents optional parameters for Ollama generation requests.
 * 
 * @param temperature Sampling temperature (0-2)
 * @param numPredict Maximum number of tokens to generate
 */
public record OllamaOptions(
    Optional<Double> temperature,
    @JsonProperty("num_predict") Optional<Integer> numPredict
) {
    public OllamaOptions {
        temperature.ifPresent(t -> {
            if (t < 0.0 || t > 2.0) {
                throw new IllegalArgumentException("Temperature must be between 0 and 2");
            }
        });
        numPredict.ifPresent(n -> {
            if (n <= 0) {
                throw new IllegalArgumentException("Num predict must be positive");
            }
        });
    }
}

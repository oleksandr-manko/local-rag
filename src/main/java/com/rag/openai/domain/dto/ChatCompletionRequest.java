package com.rag.openai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents an OpenAI chat completion request.
 * 
 * @param model The model to use for completion
 * @param messages The list of messages in the conversation
 * @param stream Whether to stream the response
 * @param temperature Sampling temperature (0-2)
 * @param maxTokens Maximum number of tokens to generate
 */
public record ChatCompletionRequest(
    String model,
    List<Message> messages,
    boolean stream,
    Optional<Double> temperature,
    @JsonProperty("max_tokens") Optional<Integer> maxTokens
) {
    public ChatCompletionRequest {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(messages, "Messages must not be null");
        if (model.isBlank()) {
            throw new IllegalArgumentException("Model must not be blank");
        }
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Messages list must not be empty");
        }
        temperature.ifPresent(t -> {
            if (t < 0.0 || t > 2.0) {
                throw new IllegalArgumentException("Temperature must be between 0 and 2");
            }
        });
        maxTokens.ifPresent(m -> {
            if (m <= 0) {
                throw new IllegalArgumentException("Max tokens must be positive");
            }
        });
    }
}

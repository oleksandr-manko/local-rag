package com.rag.openai.domain.dto;

import java.util.List;
import java.util.Objects;

/**
 * Represents an OpenAI chat completion response (non-streaming).
 * 
 * @param id Unique identifier for the completion
 * @param object The object type (always "chat.completion")
 * @param created Unix timestamp of when the completion was created
 * @param model The model used for completion
 * @param choices List of completion choices
 * @param usage Token usage information
 */
public record ChatCompletionResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    public ChatCompletionResponse {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(object, "Object must not be null");
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(choices, "Choices must not be null");
        Objects.requireNonNull(usage, "Usage must not be null");
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("Choices list must not be empty");
        }
    }
}

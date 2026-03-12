package com.rag.openai.domain.dto;

import java.util.List;
import java.util.Objects;

/**
 * Represents a chunk in a streaming OpenAI chat completion response.
 * 
 * @param id Unique identifier for the completion
 * @param object The object type (always "chat.completion.chunk")
 * @param created Unix timestamp of when the completion was created
 * @param model The model used for completion
 * @param choices List of chunk choices with deltas
 */
public record ChatCompletionChunk(
    String id,
    String object,
    long created,
    String model,
    List<ChunkChoice> choices
) {
    public ChatCompletionChunk {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(object, "Object must not be null");
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(choices, "Choices must not be null");
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("Choices list must not be empty");
        }
    }
}

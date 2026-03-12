package com.rag.openai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents a choice in a streaming chat completion chunk.
 * 
 * @param index The index of this choice
 * @param delta The incremental update for this chunk
 * @param finishReason The reason the model stopped (null until final chunk)
 */
public record ChunkChoice(
    int index,
    Delta delta,
    @JsonProperty("finish_reason") String finishReason
) {
    public ChunkChoice {
        Objects.requireNonNull(delta, "Delta must not be null");
        if (index < 0) {
            throw new IllegalArgumentException("Index must be non-negative");
        }
    }
}

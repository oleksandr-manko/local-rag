package com.rag.openai.domain.model;

import java.util.Objects;

/**
 * Represents a text chunk with a similarity score from vector search.
 * 
 * @param chunk The text chunk
 * @param score The similarity score (higher is more similar)
 */
public record ScoredChunk(
    TextChunk chunk,
    float score
) {
    public ScoredChunk {
        Objects.requireNonNull(chunk, "Chunk must not be null");
        if (Float.isNaN(score)) {
            throw new IllegalArgumentException("Score must not be NaN");
        }
    }
}

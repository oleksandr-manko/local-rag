package com.rag.openai.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents a text chunk with its vector embedding.
 * 
 * @param id Unique identifier for this embedding
 * @param embedding The vector embedding
 * @param chunk The text chunk this embedding represents
 */
public record EmbeddingRecord(
    String id,
    List<Float> embedding,
    TextChunk chunk
) {
    public EmbeddingRecord {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(embedding, "Embedding must not be null");
        Objects.requireNonNull(chunk, "Chunk must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ID must not be blank");
        }
        if (embedding.isEmpty()) {
            throw new IllegalArgumentException("Embedding must not be empty");
        }
    }
}

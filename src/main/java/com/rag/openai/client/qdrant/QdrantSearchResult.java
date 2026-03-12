package com.rag.openai.client.qdrant;

import java.util.Map;
import java.util.Objects;

/**
 * Represents a search result from Qdrant.
 * 
 * @param id The ID of the matching point
 * @param score The similarity score
 * @param payload The metadata associated with this point
 */
public record QdrantSearchResult(
    String id,
    float score,
    Map<String, Object> payload
) {
    public QdrantSearchResult {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(payload, "Payload must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ID must not be blank");
        }
        if (Float.isNaN(score)) {
            throw new IllegalArgumentException("Score must not be NaN");
        }
    }
}

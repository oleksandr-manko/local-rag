package com.rag.openai.client.qdrant;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents a search request to Qdrant.
 * 
 * @param vector The query vector to search for
 * @param limit Maximum number of results to return
 * @param filter Optional filter conditions for the search
 */
public record QdrantSearchRequest(
    List<Float> vector,
    int limit,
    Optional<Map<String, Object>> filter
) {
    public QdrantSearchRequest {
        Objects.requireNonNull(vector, "Vector must not be null");
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("Vector must not be empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("Limit must be positive");
        }
    }
}

package com.rag.openai.client.qdrant;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a point (vector with payload) in Qdrant.
 * 
 * @param id Unique identifier for this point
 * @param vector The vector embedding
 * @param payload Metadata associated with this vector
 */
public record QdrantPoint(
    String id,
    List<Float> vector,
    Map<String, Object> payload
) {
    public QdrantPoint {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(vector, "Vector must not be null");
        Objects.requireNonNull(payload, "Payload must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("ID must not be blank");
        }
        if (vector.isEmpty()) {
            throw new IllegalArgumentException("Vector must not be empty");
        }
    }
}

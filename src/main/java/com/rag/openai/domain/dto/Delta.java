package com.rag.openai.domain.dto;

import java.util.Optional;

/**
 * Represents a delta (incremental update) in a streaming response.
 * 
 * @param role Optional role (only present in first chunk)
 * @param content Optional content (the incremental text)
 */
public record Delta(
    Optional<String> role,
    Optional<String> content
) {
    public Delta {
        // Optional fields don't require validation
    }
}

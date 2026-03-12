package com.rag.openai.domain.dto;

import java.util.Objects;

/**
 * Represents an OpenAI API error response.
 * 
 * @param error The error details
 */
public record OpenAIError(
    ErrorDetail error
) {
    public OpenAIError {
        Objects.requireNonNull(error, "Error detail must not be null");
    }
}

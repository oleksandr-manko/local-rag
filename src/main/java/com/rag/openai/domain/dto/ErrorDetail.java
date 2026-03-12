package com.rag.openai.domain.dto;

import java.util.Objects;

/**
 * Represents detailed error information in an OpenAI error response.
 * 
 * @param message Human-readable error message
 * @param type The type of error
 * @param code Optional error code
 */
public record ErrorDetail(
    String message,
    String type,
    String code
) {
    public ErrorDetail {
        Objects.requireNonNull(message, "Error message must not be null");
        Objects.requireNonNull(type, "Error type must not be null");
    }
}

package com.rag.openai.domain.dto;

import java.util.List;
import java.util.Objects;

/**
 * Represents the response from the models list endpoint.
 * 
 * @param object The object type (always "list")
 * @param data List of available models
 */
public record ModelsResponse(
    String object,
    List<ModelInfo> data
) {
    public ModelsResponse {
        Objects.requireNonNull(object, "Object must not be null");
        Objects.requireNonNull(data, "Data must not be null");
    }
}

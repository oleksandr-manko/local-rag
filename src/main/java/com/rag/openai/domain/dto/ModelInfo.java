package com.rag.openai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * Represents information about a model in the models list.
 * 
 * @param id The model identifier
 * @param object The object type (always "model")
 * @param created Unix timestamp of when the model was created
 * @param ownedBy The organization that owns the model
 */
public record ModelInfo(
    String id,
    String object,
    long created,
    @JsonProperty("owned_by") String ownedBy
) {
    public ModelInfo {
        Objects.requireNonNull(id, "ID must not be null");
        Objects.requireNonNull(object, "Object must not be null");
        Objects.requireNonNull(ownedBy, "Owned by must not be null");
    }
}

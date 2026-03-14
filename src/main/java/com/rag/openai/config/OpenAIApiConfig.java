package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "openai.api")
public record OpenAIApiConfig(
    String modelName,
    long creationDate,
    String ownedBy
) {
    public OpenAIApiConfig {
        Objects.requireNonNull(modelName, "OpenAI API model name must not be null");
        Objects.requireNonNull(ownedBy, "OpenAI API owned by must not be null");
    }
}

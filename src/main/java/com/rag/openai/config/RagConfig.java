package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Objects;

@ConfigurationProperties(prefix = "rag")
public record RagConfig(
    int topKResults,
    double similarityThreshold,
    String contextSeparator,
    String promptTemplate
) {
    public RagConfig {
        if (topKResults <= 0) {
            throw new IllegalArgumentException("Top K results must be positive");
        }
        if (similarityThreshold < 0.0 || similarityThreshold > 1.0) {
            throw new IllegalArgumentException("Similarity threshold must be between 0 and 1");
        }
        Objects.requireNonNull(contextSeparator, "Context separator must not be null");
        Objects.requireNonNull(promptTemplate, "Prompt template must not be null");
    }
}

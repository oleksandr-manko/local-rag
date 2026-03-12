package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "ollama")
public record OllamaConfig(
    String host,
    int port,
    String modelName,
    String embeddingModelName,
    String visionModelName,
    Duration connectionTimeout,
    Duration readTimeout
) {
    public OllamaConfig {
        Objects.requireNonNull(host, "Ollama host must not be null");
        Objects.requireNonNull(modelName, "Ollama model name must not be null");
        Objects.requireNonNull(embeddingModelName, "Ollama embedding model name must not be null");
        Objects.requireNonNull(visionModelName, "Ollama vision model name must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }
}

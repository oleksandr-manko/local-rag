package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "qdrant")
public record QdrantConfig(
    String host,
    int port,
    String collectionName,
    Duration connectionTimeout
) {
    public QdrantConfig {
        Objects.requireNonNull(host, "Qdrant host must not be null");
        Objects.requireNonNull(collectionName, "Collection name must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
    }
}

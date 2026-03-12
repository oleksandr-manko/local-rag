package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "processing")
public record ProcessingConfig(
    String schedule,
    int chunkSize,
    int chunkOverlap,
    int batchSize,
    int maxConcurrentFiles,
    Duration jobTimeout
) {
    public ProcessingConfig {
        Objects.requireNonNull(schedule, "Schedule must not be null");
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("Invalid chunk overlap");
        }
    }
}

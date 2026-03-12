package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "redis")
public record RedisConfig(
    String host,
    int port,
    Duration connectionTimeout,
    int database
) {
    public RedisConfig {
        Objects.requireNonNull(host, "Redis host must not be null");
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (database < 0) {
            throw new IllegalArgumentException("Database index must be non-negative");
        }
    }
}

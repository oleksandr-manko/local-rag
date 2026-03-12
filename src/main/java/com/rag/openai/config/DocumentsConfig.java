package com.rag.openai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@ConfigurationProperties(prefix = "documents")
public record DocumentsConfig(
    Path inputFolder,
    List<String> supportedExtensions
) {
    public DocumentsConfig {
        Objects.requireNonNull(inputFolder, "Input folder must not be null");
        Objects.requireNonNull(supportedExtensions, "Supported extensions must not be null");
    }
}

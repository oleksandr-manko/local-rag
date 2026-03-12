package com.rag.openai.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a stored file hash for change detection.
 * 
 * @param filePath The path to the file
 * @param hash The SHA-256 hash of the file contents
 * @param lastProcessed Unix timestamp of when the file was last processed
 */
public record FileHashRecord(
    Path filePath,
    String hash,
    long lastProcessed
) {
    public FileHashRecord {
        Objects.requireNonNull(filePath, "File path must not be null");
        Objects.requireNonNull(hash, "Hash must not be null");
        if (hash.isBlank()) {
            throw new IllegalArgumentException("Hash must not be blank");
        }
        if (lastProcessed < 0) {
            throw new IllegalArgumentException("Last processed timestamp must be non-negative");
        }
    }
}

package com.rag.openai.domain.model;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents metadata about a processed document.
 * 
 * @param filename The name of the file
 * @param filePath The full path to the file
 * @param lastModified Unix timestamp of last modification
 * @param fileType The type of file (pdf, jpg, png, etc.)
 */
public record DocumentMetadata(
    String filename,
    Path filePath,
    long lastModified,
    String fileType
) {
    public DocumentMetadata {
        Objects.requireNonNull(filename, "Filename must not be null");
        Objects.requireNonNull(filePath, "File path must not be null");
        Objects.requireNonNull(fileType, "File type must not be null");
        if (filename.isBlank()) {
            throw new IllegalArgumentException("Filename must not be blank");
        }
        if (fileType.isBlank()) {
            throw new IllegalArgumentException("File type must not be blank");
        }
    }
}

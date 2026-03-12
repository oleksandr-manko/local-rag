package com.rag.openai.domain.model;

import java.util.Objects;

/**
 * Represents a chunk of text extracted from a document.
 * 
 * @param text The text content of the chunk
 * @param metadata Metadata about the source document
 * @param chunkIndex The index of this chunk within the document
 * @param startPosition The starting character position in the original text
 * @param endPosition The ending character position in the original text
 */
public record TextChunk(
    String text,
    DocumentMetadata metadata,
    int chunkIndex,
    int startPosition,
    int endPosition
) {
    public TextChunk {
        Objects.requireNonNull(text, "Text must not be null");
        Objects.requireNonNull(metadata, "Metadata must not be null");
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("Chunk index must be non-negative");
        }
        if (startPosition < 0) {
            throw new IllegalArgumentException("Start position must be non-negative");
        }
        if (endPosition < startPosition) {
            throw new IllegalArgumentException("End position must be >= start position");
        }
    }
}

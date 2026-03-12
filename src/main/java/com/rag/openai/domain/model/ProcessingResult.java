package com.rag.openai.domain.model;

import java.util.List;
import java.util.Objects;

/**
 * Represents the result of a document processing job.
 * 
 * @param documentsProcessed Number of documents successfully processed
 * @param documentsSkipped Number of documents skipped (unchanged)
 * @param chunksCreated Number of text chunks created
 * @param embeddingsStored Number of embeddings stored in vector database
 * @param processingTimeMs Total processing time in milliseconds
 * @param errors List of error messages encountered during processing
 */
public record ProcessingResult(
    int documentsProcessed,
    int documentsSkipped,
    int chunksCreated,
    int embeddingsStored,
    long processingTimeMs,
    List<String> errors
) {
    public ProcessingResult {
        Objects.requireNonNull(errors, "Errors list must not be null");
        if (documentsProcessed < 0) {
            throw new IllegalArgumentException("Documents processed must be non-negative");
        }
        if (documentsSkipped < 0) {
            throw new IllegalArgumentException("Documents skipped must be non-negative");
        }
        if (chunksCreated < 0) {
            throw new IllegalArgumentException("Chunks created must be non-negative");
        }
        if (embeddingsStored < 0) {
            throw new IllegalArgumentException("Embeddings stored must be non-negative");
        }
        if (processingTimeMs < 0) {
            throw new IllegalArgumentException("Processing time must be non-negative");
        }
    }
}

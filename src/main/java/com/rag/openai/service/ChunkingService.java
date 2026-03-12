package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;

import java.util.List;

/**
 * Service for splitting text into fixed-size chunks with overlap.
 * Implements functional programming principles with pure functions.
 */
public interface ChunkingService {
    
    /**
     * Splits text into fixed-size chunks with configurable overlap.
     * This is a pure function with no side effects.
     * 
     * @param text The text to chunk
     * @param metadata Metadata about the source document
     * @param chunkSize The maximum size of each chunk in characters
     * @param overlapSize The number of characters to overlap between consecutive chunks
     * @return List of text chunks with metadata
     */
    List<TextChunk> chunkText(
        String text,
        DocumentMetadata metadata,
        int chunkSize,
        int overlapSize
    );
}

package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Implementation of ChunkingService that splits text into fixed-size chunks
 * while preserving word boundaries and applying configurable overlap.
 * Uses functional programming with Stream API for chunk generation.
 */
@Service
public class ChunkingServiceImpl implements ChunkingService {
    
    @Override
    public List<TextChunk> chunkText(
        String text,
        DocumentMetadata metadata,
        int chunkSize,
        int overlapSize
    ) {
        Objects.requireNonNull(text, "Text must not be null");
        Objects.requireNonNull(metadata, "Metadata must not be null");
        
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive");
        }
        if (overlapSize < 0) {
            throw new IllegalArgumentException("Overlap size must be non-negative");
        }
        if (overlapSize >= chunkSize) {
            throw new IllegalArgumentException("Overlap size must be less than chunk size");
        }
        
        // Handle edge case: empty text
        if (text.isEmpty()) {
            return List.of();
        }
        
        // Handle edge case: text shorter than chunk size
        if (text.length() <= chunkSize) {
            return List.of(new TextChunk(text, metadata, 0, 0, text.length()));
        }
        
        // Calculate step size (how far to advance for each chunk)
        int stepSize = chunkSize - overlapSize;
        
        // Generate chunks using Stream API for functional chunk generation
        return IntStream.iterate(0, startPos -> startPos < text.length(), startPos -> startPos + stepSize)
            .mapToObj(startPos -> createChunkAtPosition(text, metadata, startPos, chunkSize, stepSize))
            .toList();
    }
    
    /**
     * Creates a single chunk at the specified position.
     * Pure function that preserves word boundaries.
     * 
     * @param text The full text
     * @param metadata Document metadata
     * @param startPos Starting position for this chunk
     * @param chunkSize Maximum chunk size
     * @param stepSize Step size for calculating chunk index
     * @return A TextChunk with word boundaries preserved
     */
    private TextChunk createChunkAtPosition(
        String text,
        DocumentMetadata metadata,
        int startPos,
        int chunkSize,
        int stepSize
    ) {
        int endPos = Math.min(startPos + chunkSize, text.length());
        
        // If this is not the last chunk, try to preserve word boundaries
        if (endPos < text.length()) {
            endPos = findWordBoundary(text, endPos);
        }
        
        String chunkText = text.substring(startPos, endPos);
        int chunkIndex = startPos / stepSize;
        
        return new TextChunk(chunkText, metadata, chunkIndex, startPos, endPos);
    }
    
    /**
     * Finds the nearest word boundary before the given position.
     * Looks backward from the position to find whitespace or punctuation.
     * Pure function with no side effects.
     * 
     * @param text The text to search
     * @param position The position to start searching from
     * @return The position of the nearest word boundary, or the original position if none found
     */
    private int findWordBoundary(String text, int position) {
        // Look backward from position to find a word boundary
        // Search up to 100 characters back to avoid excessive searching
        int searchStart = Math.max(0, position - 100);
        
        for (int i = position - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c) || isPunctuation(c)) {
                // Return position after the boundary character
                return i + 1;
            }
        }
        
        // If no boundary found within reasonable distance, use original position
        return position;
    }
    
    /**
     * Checks if a character is punctuation.
     * Pure function for character classification.
     * 
     * @param c The character to check
     * @return true if the character is punctuation
     */
    private boolean isPunctuation(char c) {
        return ".!?,;:".indexOf(c) >= 0;
    }
}

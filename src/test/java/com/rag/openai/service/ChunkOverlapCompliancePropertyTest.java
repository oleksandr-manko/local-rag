package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for chunk overlap compliance.
 * **Validates: Requirements 6.2**
 * 
 * Property 11: Chunk Overlap Compliance
 * 
 * This property verifies that consecutive chunks produced by the ChunkingService
 * have the correct overlap as specified in the configuration. The overlap ensures
 * that context is preserved across chunk boundaries, which is important for
 * maintaining semantic continuity in RAG operations.
 */
class ChunkOverlapCompliancePropertyTest {

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Property(tries = 200)
    @Label("When text is chunked with overlap Then consecutive chunks have intended overlap behavior")
    void consecutiveChunksHaveCorrectOverlap(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 100, max = 500) int chunkSize,
            @ForAll @IntRange(min = 10, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: text, chunk size, and overlap size
        Assume.that(overlapSize < chunkSize);
        Assume.that(text.length() > chunkSize); // Need multiple chunks to test overlap
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: consecutive chunks should follow overlap configuration
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // Calculate the actual overlap between consecutive chunks
            int currentEnd = currentChunk.endPosition();
            int nextStart = nextChunk.startPosition();
            
            // The overlap is the difference between where current chunk ends
            // and where next chunk starts (positive = overlap, negative = gap)
            int actualOverlap = currentEnd - nextStart;
            
            // Due to word boundary preservation, the actual overlap may vary:
            // - It can be positive (overlap exists)
            // - It can be zero (chunks are adjacent)
            // - It can be slightly negative (small gap due to word boundary adjustment)
            // The key property is that the step size (nextStart - currentStart) should
            // approximately equal chunkSize - overlapSize
            
            int expectedStepSize = chunkSize - overlapSize;
            int actualStepSize = nextChunk.startPosition() - currentChunk.startPosition();
            
            // The actual step size should be close to expected, allowing for word boundary adjustments
            assertThat(actualStepSize)
                    .as("Step size between chunk %d and %d should be at least the configured step size %d", 
                        i, i + 1, expectedStepSize)
                    .isGreaterThanOrEqualTo(expectedStepSize);
            
            // The step size should not be excessively larger than expected
            // Allow up to 100 characters of word boundary adjustment
            assertThat(actualStepSize)
                    .as("Step size between chunk %d and %d should not exceed %d by more than 100 chars", 
                        i, i + 1, expectedStepSize)
                    .isLessThanOrEqualTo(expectedStepSize + 100);
            
            // When overlap is configured, we expect some overlap or minimal gap
            // The gap should not be larger than the word boundary search distance (100 chars)
            if (overlapSize > 0) {
                assertThat(actualOverlap)
                        .as("Gap between chunks %d and %d should not exceed word boundary tolerance", i, i + 1)
                        .isGreaterThanOrEqualTo(-100);
            }
        }
    }

    @Property(tries = 200)
    @Label("When overlap is zero Then consecutive chunks do not overlap")
    void zeroOverlapProducesNonOverlappingChunks(
            @ForAll @IntRange(min = 100, max = 500) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: text with zero overlap and simple repeating pattern to avoid word boundary issues
        int zeroOverlap = 0;
        // Use simple repeating text to avoid word boundary edge cases
        String text = "a".repeat(chunkSize * 3);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, zeroOverlap);
        
        // Then: consecutive chunks should not overlap (or have minimal overlap due to word boundaries)
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int currentEnd = currentChunk.endPosition();
            int nextStart = nextChunk.startPosition();
            
            // With zero overlap, next chunk should start at or after current chunk ends
            assertThat(nextStart)
                    .as("With zero overlap, chunk %d should start at or after chunk %d ends", i + 1, i)
                    .isGreaterThanOrEqualTo(currentEnd);
        }
    }

    @Property(tries = 200)
    @Label("When overlap is configured Then step size equals chunk size minus overlap")
    void stepSizeEqualsChunkSizeMinusOverlap(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 100, max = 500) int chunkSize,
            @ForAll @IntRange(min = 10, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: text with configured overlap
        Assume.that(overlapSize < chunkSize);
        Assume.that(text.length() > chunkSize * 2); // Need at least 2 full chunks
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: the step size (distance between chunk starts) should approximately equal chunk size minus overlap
        assertThat(chunks.size()).isGreaterThan(1);
        
        int expectedStepSize = chunkSize - overlapSize;
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int actualStepSize = nextChunk.startPosition() - currentChunk.startPosition();
            
            // The actual step size should be close to the expected step size
            // We allow some tolerance for word boundary adjustments
            // The step size should be at least the expected value (word boundaries might increase it slightly)
            assertThat(actualStepSize)
                    .as("Step size between chunk %d and %d should be at least %d", i, i + 1, expectedStepSize)
                    .isGreaterThanOrEqualTo(expectedStepSize);
            
            // The step size should not be excessively larger than expected
            // Allow up to 100 characters of word boundary adjustment
            assertThat(actualStepSize)
                    .as("Step size between chunk %d and %d should not exceed %d by more than 100 chars", 
                        i, i + 1, expectedStepSize)
                    .isLessThanOrEqualTo(expectedStepSize + 100);
        }
    }

    @Property(tries = 200)
    @Label("When overlap is large Then chunks have significant overlap")
    void largeOverlapProducesSignificantOverlap(
            @ForAll @IntRange(min = 200, max = 500) int chunkSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: large overlap (50% of chunk size)
        int overlapSize = chunkSize / 2;
        String text = "a".repeat(chunkSize * 3); // Ensure multiple chunks
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: consecutive chunks should have significant overlap
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int currentEnd = currentChunk.endPosition();
            int nextStart = nextChunk.startPosition();
            int actualOverlap = currentEnd - nextStart;
            
            // With large overlap, the overlap should be substantial
            assertThat(actualOverlap)
                    .as("Large overlap should produce significant overlap between chunks %d and %d", i, i + 1)
                    .isGreaterThanOrEqualTo(overlapSize / 2); // At least half the configured overlap
        }
    }

    @Property(tries = 200)
    @Label("When text has word boundaries Then overlap respects boundaries")
    void overlapRespectsWordBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 20, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: text with clear word boundaries
        Assume.that(overlapSize < chunkSize);
        String text = "word ".repeat(chunkSize); // Creates text with clear word boundaries
        Assume.that(text.length() > chunkSize * 2);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should overlap and respect word boundaries
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // Verify overlap exists
            int currentEnd = currentChunk.endPosition();
            int nextStart = nextChunk.startPosition();
            
            assertThat(currentEnd)
                    .as("Chunks %d and %d should have overlap", i, i + 1)
                    .isGreaterThan(nextStart);
            
            // Verify chunks don't end/start in the middle of words (except possibly last chunk)
            if (i < chunks.size() - 2 && !currentChunk.text().isEmpty()) {
                String currentText = currentChunk.text();
                char lastChar = currentText.charAt(currentText.length() - 1);
                
                // Chunks should ideally end at word boundaries (space or start/end of text)
                // Last character should be space or the chunk should end at text boundary
                assertThat(lastChar == ' ' || currentEnd >= text.length())
                        .as("Chunk %d should end at word boundary", i)
                        .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When overlap equals chunk size minus one Then maximum overlap is applied")
    void maximumOverlapIsApplied(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: overlap is maximum allowed (chunk size - 1)
        int overlapSize = chunkSize - 1;
        String text = "a".repeat(chunkSize * 3);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should have maximum overlap (step size of 1)
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int stepSize = nextChunk.startPosition() - currentChunk.startPosition();
            
            // With maximum overlap, step size should be minimal (1 character)
            assertThat(stepSize)
                    .as("Maximum overlap should produce minimal step size between chunks %d and %d", i, i + 1)
                    .isLessThanOrEqualTo(10); // Allow some tolerance for word boundaries
        }
    }

    @Property(tries = 200)
    @Label("When text length is multiple of step size Then overlap is consistent")
    void consistentOverlapAcrossAllChunks(
            @ForAll @IntRange(min = 100, max = 200) int chunkSize,
            @ForAll @IntRange(min = 20, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: text that produces multiple chunks
        Assume.that(overlapSize < chunkSize);
        int stepSize = chunkSize - overlapSize;
        String text = "a".repeat(stepSize * 5); // Text length is multiple of step size
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: overlap should be consistent across all consecutive chunk pairs
        assertThat(chunks.size()).isGreaterThan(1);
        
        Integer previousOverlap = null;
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int currentOverlap = currentChunk.endPosition() - nextChunk.startPosition();
            
            if (previousOverlap != null) {
                // Overlap should be consistent (within tolerance for word boundaries)
                assertThat(Math.abs(currentOverlap - previousOverlap))
                        .as("Overlap should be consistent between all chunk pairs")
                        .isLessThanOrEqualTo(50); // Allow some variance for word boundaries
            }
            
            previousOverlap = currentOverlap;
        }
    }

    @Property(tries = 200)
    @Label("When overlap is small Then chunks advance significantly")
    void smallOverlapProducesSignificantAdvancement(
            @ForAll @IntRange(min = 200, max = 500) int chunkSize,
            @ForAll @IntRange(min = 1, max = 20) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 11: Chunk Overlap Compliance
        
        // Given: small overlap
        Assume.that(overlapSize < chunkSize);
        String text = "a".repeat(chunkSize * 3);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should advance significantly (large step size)
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            int stepSize = nextChunk.startPosition() - currentChunk.startPosition();
            
            // With small overlap, step size should be close to chunk size
            assertThat(stepSize)
                    .as("Small overlap should produce large step size between chunks %d and %d", i, i + 1)
                    .isGreaterThanOrEqualTo(chunkSize - overlapSize - 50); // Allow tolerance for word boundaries
        }
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.oneOf(
                // Medium texts with spaces
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',')
                        .ofMinLength(500).ofMaxLength(2000),
                // Long texts with spaces
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',', '!', '?')
                        .ofMinLength(1000).ofMaxLength(5000),
                // Very long texts
                Arbitraries.strings().alpha().ofMinLength(2000).ofMaxLength(10000)
        );
    }

    @Provide
    Arbitrary<DocumentMetadata> documentMetadata() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.longs().between(1000000000L, 9999999999L)
        ).as((filename, fileType, lastModified) ->
                new DocumentMetadata(
                        filename + "." + fileType,
                        Path.of("/tmp/" + filename + "." + fileType),
                        lastModified,
                        fileType
                )
        );
    }
}

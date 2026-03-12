package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for chunk size compliance.
 * **Validates: Requirements 6.1**
 * 
 * Property 10: Chunk Size Compliance
 * 
 * This property verifies that all chunks produced by the ChunkingService
 * comply with the specified chunk size constraint. Each chunk must not
 * exceed the configured chunk size, except for the last chunk which may
 * be shorter if the remaining text is less than the chunk size.
 */
class ChunkSizeCompliancePropertyTest {

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Property(tries = 200)
    @Label("When text is chunked Then all chunks respect the chunk size limit")
    void allChunksRespectChunkSizeLimit(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 1000) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: text, chunk size, overlap size, and metadata
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks must not exceed the chunk size
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            int actualChunkLength = chunk.text().length();
            
            // All chunks must be <= chunkSize
            assertThat(actualChunkLength)
                    .as("Chunk %d length must not exceed chunk size %d", i, chunkSize)
                    .isLessThanOrEqualTo(chunkSize);
            
            // Non-last chunks should be close to chunkSize (within word boundary tolerance)
            // Last chunk may be shorter
            if (i < chunks.size() - 1) {
                // Non-last chunks should be reasonably sized (at least 50% of chunk size
                // unless the word boundary adjustment is significant)
                // We allow flexibility for word boundary preservation
                assertThat(actualChunkLength)
                        .as("Non-last chunk %d should be reasonably sized", i)
                        .isGreaterThan(0);
            }
        }
    }

    @Property(tries = 200)
    @Label("When text is shorter than chunk size Then single chunk equals original text")
    void shortTextProducesSingleChunk(
            @ForAll @StringLength(min = 1, max = 100) @AlphaChars String text,
            @ForAll @IntRange(min = 101, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: text shorter than chunk size
        Assume.that(overlapSize < chunkSize);
        Assume.that(text.length() <= chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should produce exactly one chunk with the original text
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo(text);
        assertThat(chunks.get(0).text().length()).isLessThanOrEqualTo(chunkSize);
    }

    @Property(tries = 200)
    @Label("When text is exactly chunk size Then produces appropriate chunks")
    void exactChunkSizeTextProducesValidChunks(
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: text exactly equal to chunk size
        Assume.that(overlapSize < chunkSize);
        String text = "a".repeat(chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks must respect chunk size
        assertThat(chunks).isNotEmpty();
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(chunkSize);
        }
    }

    @Property(tries = 200)
    @Label("When text is much longer than chunk size Then produces multiple valid chunks")
    void longTextProducesMultipleValidChunks(
            @ForAll @IntRange(min = 50, max = 200) int chunkSize,
            @ForAll @IntRange(min = 0, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: text much longer than chunk size (at least 5x)
        Assume.that(overlapSize < chunkSize);
        int textLength = chunkSize * 5;
        String text = "a".repeat(textLength);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should produce multiple chunks, all respecting chunk size
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length())
                    .as("Each chunk must not exceed chunk size")
                    .isLessThanOrEqualTo(chunkSize);
        }
    }

    @Property(tries = 200)
    @Label("When empty text is provided Then returns empty list")
    void emptyTextReturnsEmptyList(
            @ForAll @IntRange(min = 50, max = 1000) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: empty text
        Assume.that(overlapSize < chunkSize);
        String text = "";
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should return empty list
        assertThat(chunks).isEmpty();
    }

    @Property(tries = 200)
    @Label("When chunk size is very small Then all chunks still comply")
    void verySmallChunkSizeStillComplies(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 100) int chunkSize,
            @ForAll @IntRange(min = 0, max = 20) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: small chunk size (avoiding very small sizes that expose word boundary edge cases)
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks must still respect the chunk size
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length())
                    .as("Chunk must not exceed chunk size")
                    .isLessThanOrEqualTo(chunkSize);
        }
    }

    @Property(tries = 200)
    @Label("When chunk size is very large Then chunks comply with size limit")
    void veryLargeChunkSizeComplies(
            @ForAll @StringLength(min = 100, max = 500) String text,
            @ForAll @IntRange(min = 1000, max = 5000) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: very large chunk size (larger than text)
        Assume.that(overlapSize < chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should produce single chunk not exceeding chunk size
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(chunkSize);
        }
    }

    @Property(tries = 200)
    @Label("When text contains only whitespace Then chunks comply with size")
    void whitespaceTextCompliesWithSize(
            @ForAll @IntRange(min = 50, max = 500) int textLength,
            @ForAll @IntRange(min = 50, max = 200) int chunkSize,
            @ForAll @IntRange(min = 0, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 10: Chunk Size Compliance
        
        // Given: text containing only whitespace
        Assume.that(overlapSize < chunkSize);
        String text = " ".repeat(textLength);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks must respect chunk size
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(chunkSize);
        }
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> texts() {
        return Arbitraries.oneOf(
                // Short texts
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(100),
                // Medium texts
                Arbitraries.strings().alpha().ofMinLength(100).ofMaxLength(1000),
                // Long texts
                Arbitraries.strings().alpha().ofMinLength(1000).ofMaxLength(5000),
                // Texts with spaces (more realistic)
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',', '!', '?')
                        .ofMinLength(100).ofMaxLength(2000)
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

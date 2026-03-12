package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for chunk metadata completeness.
 * **Validates: Requirements 6.4**
 * 
 * Property 13: Chunk Metadata Completeness
 * 
 * This property verifies that all chunks produced by the ChunkingService
 * contain complete and accurate metadata. Each chunk must include:
 * - Document metadata (filename, file path, last modified, file type)
 * - Chunk index (sequential, starting from 0)
 * - Start position (character position in original text)
 * - End position (character position in original text)
 * 
 * The metadata enables traceability from chunks back to source documents
 * and supports proper ordering and reconstruction of document content.
 */
class ChunkMetadataCompletenessPropertyTest {

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Property(tries = 200)
    @Label("When text is chunked Then all chunks have complete metadata")
    void allChunksHaveCompleteMetadata(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text, chunk size, overlap size, and metadata
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks must have complete metadata
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            // Verify metadata is present and not null
            assertThat(chunk.metadata())
                    .as("Chunk metadata must not be null")
                    .isNotNull();
            
            // Verify metadata matches the input metadata
            assertThat(chunk.metadata())
                    .as("Chunk metadata must match input metadata")
                    .isEqualTo(metadata);
            
            // Verify chunk index is non-negative
            assertThat(chunk.chunkIndex())
                    .as("Chunk index must be non-negative")
                    .isGreaterThanOrEqualTo(0);
            
            // Verify start position is non-negative
            assertThat(chunk.startPosition())
                    .as("Start position must be non-negative")
                    .isGreaterThanOrEqualTo(0);
            
            // Verify end position is greater than or equal to start position
            assertThat(chunk.endPosition())
                    .as("End position must be >= start position")
                    .isGreaterThanOrEqualTo(chunk.startPosition());
            
            // Verify positions are within text bounds
            assertThat(chunk.startPosition())
                    .as("Start position must be within text bounds")
                    .isLessThanOrEqualTo(text.length());
            
            assertThat(chunk.endPosition())
                    .as("End position must be within text bounds")
                    .isLessThanOrEqualTo(text.length());
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then chunk indices are sequential starting from zero")
    void chunkIndicesAreSequential(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text that will produce multiple chunks
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunk indices should be sequential starting from 0
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex())
                    .as("Chunk at position %d should have index %d", i, i)
                    .isEqualTo(i);
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then start and end positions are consistent with chunk text")
    void positionsConsistentWithChunkText(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text, chunk size, and overlap size
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: positions should match the actual chunk text from original text
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            int start = chunk.startPosition();
            int end = chunk.endPosition();
            
            // Extract the substring from original text using the positions
            String extractedText = text.substring(start, end);
            
            // The extracted text should match the chunk text
            assertThat(chunk.text())
                    .as("Chunk text should match substring from original text using positions")
                    .isEqualTo(extractedText);
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then metadata includes source filename")
    void metadataIncludesSourceFilename(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text and metadata with filename
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks should include the source filename in metadata
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.metadata().filename())
                    .as("Chunk metadata must include source filename")
                    .isNotNull()
                    .isNotBlank()
                    .isEqualTo(metadata.filename());
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then metadata includes file path")
    void metadataIncludesFilePath(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text and metadata with file path
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks should include the file path in metadata
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.metadata().filePath())
                    .as("Chunk metadata must include file path")
                    .isNotNull()
                    .isEqualTo(metadata.filePath());
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then metadata includes last modified timestamp")
    void metadataIncludesLastModified(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text and metadata with last modified timestamp
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks should include the last modified timestamp in metadata
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.metadata().lastModified())
                    .as("Chunk metadata must include last modified timestamp")
                    .isEqualTo(metadata.lastModified());
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then metadata includes file type")
    void metadataIncludesFileType(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text and metadata with file type
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all chunks should include the file type in metadata
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.metadata().fileType())
                    .as("Chunk metadata must include file type")
                    .isNotNull()
                    .isNotBlank()
                    .isEqualTo(metadata.fileType());
        }
    }

    @Property(tries = 200)
    @Label("When single chunk is created Then it has index zero")
    void singleChunkHasIndexZero(
            @ForAll @StringLength(min = 1, max = 100) String text,
            @ForAll @IntRange(min = 101, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text shorter than chunk size (will produce single chunk)
        Assume.that(overlapSize < chunkSize);
        Assume.that(text.length() <= chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should produce exactly one chunk with index 0
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).chunkIndex())
                .as("Single chunk should have index 0")
                .isEqualTo(0);
    }

    @Property(tries = 200)
    @Label("When multiple chunks are created Then indices increment by one")
    void multipleChunksHaveIncrementingIndices(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: text that will produce multiple chunks
        Assume.that(overlapSize < chunkSize);
        String text = "a".repeat(chunkSize * 3); // Ensure multiple chunks
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: indices should increment by exactly 1
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 1; i < chunks.size(); i++) {
            int previousIndex = chunks.get(i - 1).chunkIndex();
            int currentIndex = chunks.get(i).chunkIndex();
            
            assertThat(currentIndex)
                    .as("Chunk index should increment by 1")
                    .isEqualTo(previousIndex + 1);
        }
    }

    @Property(tries = 200)
    @Label("When text is chunked Then first chunk starts at position zero")
    void firstChunkStartsAtZero(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: any non-empty text
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: first chunk should start at position 0
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).startPosition())
                .as("First chunk should start at position 0")
                .isEqualTo(0);
    }

    @Property(tries = 200)
    @Label("When text is chunked Then last chunk ends at text length")
    void lastChunkEndsAtTextLength(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: any non-empty text
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: last chunk should end at text length
        assertThat(chunks).isNotEmpty();
        TextChunk lastChunk = chunks.get(chunks.size() - 1);
        assertThat(lastChunk.endPosition())
                .as("Last chunk should end at text length")
                .isEqualTo(text.length());
    }

    @Property(tries = 200)
    @Label("When text is chunked Then chunk positions cover entire text")
    void chunkPositionsCoverEntireText(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: any non-empty text
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should cover the entire text
        assertThat(chunks).isNotEmpty();
        
        // First chunk starts at 0
        assertThat(chunks.get(0).startPosition()).isEqualTo(0);
        
        // Last chunk ends at text length
        assertThat(chunks.get(chunks.size() - 1).endPosition()).isEqualTo(text.length());
        
        // Verify gaps between chunks are reasonable (allowing for word boundary adjustments)
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // Calculate gap (positive = gap, negative = overlap)
            int gap = nextChunk.startPosition() - currentChunk.endPosition();
            
            // Gap should not be excessive (allow up to 100 chars for word boundary adjustment)
            assertThat(gap)
                    .as("Gap between chunk %d and %d should not exceed word boundary tolerance", i, i + 1)
                    .isLessThanOrEqualTo(100);
        }
    }

    @Property(tries = 200)
    @Label("When empty text is provided Then returns empty list with no metadata issues")
    void emptyTextReturnsEmptyListSafely(
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: empty text
        Assume.that(overlapSize < chunkSize);
        String text = "";
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: should return empty list (no metadata to validate)
        assertThat(chunks).isEmpty();
    }

    @Property(tries = 200)
    @Label("When text is chunked Then chunk text length equals position difference")
    void chunkTextLengthEqualsPositionDifference(
            @ForAll("texts") String text,
            @ForAll @IntRange(min = 50, max = 500) int chunkSize,
            @ForAll @IntRange(min = 0, max = 100) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 13: Chunk Metadata Completeness
        
        // Given: any non-empty text
        Assume.that(overlapSize < chunkSize);
        Assume.that(!text.isEmpty());
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunk text length should equal end position minus start position
        assertThat(chunks).isNotEmpty();
        
        for (TextChunk chunk : chunks) {
            int expectedLength = chunk.endPosition() - chunk.startPosition();
            int actualLength = chunk.text().length();
            
            assertThat(actualLength)
                    .as("Chunk text length should equal position difference")
                    .isEqualTo(expectedLength);
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

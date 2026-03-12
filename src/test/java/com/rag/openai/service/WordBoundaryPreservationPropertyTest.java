package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for word boundary preservation.
 * **Validates: Requirements 6.3**
 * 
 * Property 12: Word Boundary Preservation
 * 
 * This property verifies that the ChunkingService preserves word boundaries
 * when splitting text into chunks. Chunks should not split words in the middle,
 * except when a single word exceeds the chunk size or when no word boundary
 * is found within a reasonable search distance (100 characters).
 */
class WordBoundaryPreservationPropertyTest {

    private final ChunkingService chunkingService = new ChunkingServiceImpl();

    @Property(tries = 200)
    @Label("When text contains words Then chunks do not split words")
    void chunksDoNotSplitWords(
            @ForAll @IntRange(min = 100, max = 500) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with clear word boundaries (longer than chunk size)
        Assume.that(overlapSize < chunkSize);
        String text = "word ".repeat(chunkSize); // Ensures text > chunkSize with clear boundaries
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: non-last chunks should end at word boundaries
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                // Non-last chunks should end at word boundaries (space in this case)
                boolean endsAtWordBoundary = Character.isWhitespace(lastChar) 
                    || isPunctuation(lastChar);
                
                assertThat(endsAtWordBoundary)
                    .as("Chunk %d should end at word boundary (last char: '%c')", i, lastChar)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When text has clear word boundaries Then chunks respect those boundaries")
    void chunksRespectClearWordBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with clear word boundaries (space-separated words)
        Assume.that(overlapSize < chunkSize);
        String text = "word ".repeat(chunkSize); // Creates text with clear boundaries
        Assume.that(text.length() > chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: all non-last chunks should end with space (word boundary)
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                assertThat(lastChar)
                    .as("Chunk %d should end with space (word boundary)", i)
                    .isEqualTo(' ');
            }
        }
    }

    @Property(tries = 200)
    @Label("When text has punctuation Then chunks can end at punctuation boundaries")
    void chunksCanEndAtPunctuationBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with punctuation boundaries (ensure it's longer than chunk size)
        Assume.that(overlapSize < chunkSize);
        String text = "sentence. ".repeat(chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: non-last chunks should end at word boundaries (space or punctuation)
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                boolean endsAtBoundary = Character.isWhitespace(lastChar) || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Chunk %d should end at word boundary (space or punctuation)", i)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When chunk boundary falls mid-word Then boundary is adjusted backward")
    void chunkBoundaryAdjustedBackwardForMidWord(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text where chunk boundary would fall mid-word
        Assume.that(overlapSize < chunkSize);
        // Create text with long words that will force mid-word boundaries
        String longWord = "a".repeat(50);
        String text = (longWord + " ").repeat(chunkSize / 25);
        Assume.that(text.length() > chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should be adjusted to end at word boundaries
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                // Should end at space (word boundary)
                assertThat(lastChar)
                    .as("Chunk %d should be adjusted to end at space", i)
                    .isEqualTo(' ');
            }
        }
    }

    @Property(tries = 200)
    @Label("When text has no word boundaries nearby Then chunk uses original position")
    void noNearbyBoundaryUsesOriginalPosition(
            @ForAll @IntRange(min = 150, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with very long word (no boundaries within search distance)
        Assume.that(overlapSize < chunkSize);
        // Create a word longer than the search distance (100 chars)
        String veryLongWord = "a".repeat(150);
        String text = veryLongWord + " " + veryLongWord;
        Assume.that(text.length() > chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should be created even if no boundary found
        // (falls back to original position)
        assertThat(chunks).isNotEmpty();
        
        // At least one chunk should exist
        assertThat(chunks.size()).isGreaterThanOrEqualTo(1);
        
        // All chunks should respect the chunk size limit
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length())
                .as("Chunk should not exceed chunk size")
                .isLessThanOrEqualTo(chunkSize);
        }
    }

    @Property(tries = 200)
    @Label("When text has mixed boundaries Then chunks end at nearest boundary")
    void chunksEndAtNearestBoundary(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with mixed word boundaries (spaces, punctuation, etc.)
        Assume.that(overlapSize < chunkSize);
        // Create text with guaranteed word boundaries
        String text = "word. ".repeat(chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: non-last chunks should end at some word boundary
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                // Should end at whitespace or punctuation
                boolean endsAtBoundary = Character.isWhitespace(lastChar) 
                    || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Chunk %d should end at word boundary", i)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When last chunk is created Then it can end anywhere")
    void lastChunkCanEndAnywhere(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text that will produce multiple chunks
        Assume.that(overlapSize < chunkSize);
        String text = "word ".repeat(chunkSize); // Ensures text > chunkSize
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: last chunk should end at text boundary (no word boundary requirement)
        assertThat(chunks).isNotEmpty();
        
        TextChunk lastChunk = chunks.get(chunks.size() - 1);
        
        // Last chunk should end at the end of the text
        assertThat(lastChunk.endPosition())
            .as("Last chunk should end at text boundary")
            .isEqualTo(text.length());
    }

    @Property(tries = 200)
    @Label("When text has newlines Then chunks can end at newline boundaries")
    void chunksCanEndAtNewlineBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with newline boundaries (ensure it's longer than chunk size)
        Assume.that(overlapSize < chunkSize);
        String text = "line\n".repeat(chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: non-last chunks should end at word boundaries (including newlines)
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                // Newline is whitespace, so it's a valid word boundary
                boolean endsAtBoundary = Character.isWhitespace(lastChar) || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Chunk %d should end at word boundary (including newline)", i)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When chunk size is very small Then word boundaries are still preserved")
    void smallChunkSizePreservesWordBoundaries(
            @ForAll @IntRange(min = 50, max = 100) int chunkSize,
            @ForAll @IntRange(min = 5, max = 20) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: small chunk size with clear word boundaries
        Assume.that(overlapSize < chunkSize);
        String text = "word ".repeat(chunkSize);
        Assume.that(text.length() > chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: even with small chunks, word boundaries should be preserved
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                boolean endsAtBoundary = Character.isWhitespace(lastChar) || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Small chunk %d should still end at word boundary", i)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When text has tabs Then chunks can end at tab boundaries")
    void chunksCanEndAtTabBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with tab boundaries (ensure it's longer than chunk size)
        Assume.that(overlapSize < chunkSize);
        String text = "word\t".repeat(chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: non-last chunks should end at word boundaries (including tabs)
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                // Tab is whitespace, so it's a valid word boundary
                boolean endsAtBoundary = Character.isWhitespace(lastChar) || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Chunk %d should end at word boundary (including tab)", i)
                    .isTrue();
            }
        }
    }

    @Property(tries = 200)
    @Label("When text has multiple punctuation types Then all are recognized as boundaries")
    void allPunctuationTypesRecognizedAsBoundaries(
            @ForAll @IntRange(min = 100, max = 300) int chunkSize,
            @ForAll @IntRange(min = 10, max = 50) int overlapSize,
            @ForAll("documentMetadata") DocumentMetadata metadata
    ) {
        // Feature: rag-openai-api-ollama, Property 12: Word Boundary Preservation
        
        // Given: text with various punctuation marks
        Assume.that(overlapSize < chunkSize);
        String[] punctuations = {".", "!", "?", ",", ";", ":"};
        StringBuilder textBuilder = new StringBuilder();
        for (int i = 0; i < chunkSize; i++) {
            textBuilder.append("word").append(punctuations[i % punctuations.length]).append(" ");
        }
        String text = textBuilder.toString();
        Assume.that(text.length() > chunkSize);
        
        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, metadata, chunkSize, overlapSize);
        
        // Then: chunks should end at word boundaries (space or punctuation)
        assertThat(chunks).isNotEmpty();
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk chunk = chunks.get(i);
            String chunkText = chunk.text();
            
            if (!chunkText.isEmpty()) {
                char lastChar = chunkText.charAt(chunkText.length() - 1);
                
                boolean endsAtBoundary = Character.isWhitespace(lastChar) || isPunctuation(lastChar);
                
                assertThat(endsAtBoundary)
                    .as("Chunk %d should end at word boundary (space or any punctuation)", i)
                    .isTrue();
            }
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Checks if a character is punctuation.
     * Matches the implementation in ChunkingServiceImpl.
     */
    private boolean isPunctuation(char c) {
        return ".!?,;:".indexOf(c) >= 0;
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> textsWithWords() {
        return Arbitraries.oneOf(
                // Simple space-separated words
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ')
                        .ofMinLength(200).ofMaxLength(2000),
                // Words with punctuation
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',', '!', '?')
                        .ofMinLength(200).ofMaxLength(2000),
                // Realistic text with various boundaries
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',', '!', '?', ';', ':')
                        .ofMinLength(500).ofMaxLength(3000)
        );
    }

    @Provide
    Arbitrary<String> textsWithMixedBoundaries() {
        return Arbitraries.oneOf(
                // Text with spaces and punctuation
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '.', ',', '!', '?', ';', ':')
                        .ofMinLength(200).ofMaxLength(2000),
                // Text with newlines
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '\n', '.', ',')
                        .ofMinLength(200).ofMaxLength(2000),
                // Text with tabs
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '\t', '.', ',')
                        .ofMinLength(200).ofMaxLength(2000),
                // Text with mixed whitespace
                Arbitraries.strings().withCharRange('a', 'z').withChars(' ', '\n', '\t', '.', ',', '!', '?')
                        .ofMinLength(300).ofMaxLength(2500)
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

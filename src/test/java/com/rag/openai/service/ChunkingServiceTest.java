package com.rag.openai.service;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for ChunkingService implementation.
 * Tests specific examples and edge cases for text chunking functionality.
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5**
 */
class ChunkingServiceTest {

    private ChunkingService chunkingService;
    private DocumentMetadata testMetadata;

    @BeforeEach
    void setUp() {
        chunkingService = new ChunkingServiceImpl();
        testMetadata = new DocumentMetadata(
            "test-document.pdf",
            Path.of("/tmp/test-document.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );
    }

    // ==================== Fixed-Size Chunking Tests ====================

    @Test
    @DisplayName("When text is chunked with fixed size Then produces chunks of correct size")
    void testFixedSizeChunking() {
        // Given: text that should produce multiple chunks
        String text = "This is a test document. It contains multiple sentences. " +
                      "We want to split it into fixed-size chunks. " +
                      "Each chunk should respect the configured size.";
        int chunkSize = 50;
        int overlapSize = 10;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should produce multiple chunks with correct sizes
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            assertThat(chunk.text().length())
                .as("Chunk %d should not exceed chunk size", i)
                .isLessThanOrEqualTo(chunkSize);
            assertThat(chunk.chunkIndex()).isEqualTo(i);
            assertThat(chunk.metadata()).isEqualTo(testMetadata);
        }
    }

    @Test
    @DisplayName("When text is exactly chunk size Then produces single chunk")
    void testExactChunkSize() {
        // Given: text exactly equal to chunk size
        String text = "A".repeat(50); // Exactly 50 characters
        int chunkSize = 50;
        int overlapSize = 0;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should produce exactly one chunk
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).hasSize(chunkSize);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).startPosition()).isEqualTo(0);
        assertThat(chunks.get(0).endPosition()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("When text is longer than chunk size Then produces multiple chunks")
    void testMultipleChunks() {
        // Given: text longer than chunk size
        String text = "The quick brown fox jumps over the lazy dog. " +
                      "The quick brown fox jumps over the lazy dog. " +
                      "The quick brown fox jumps over the lazy dog.";
        int chunkSize = 50;
        int overlapSize = 5;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should produce multiple chunks
        assertThat(chunks.size()).isGreaterThan(2);
        
        // Verify chunk indices are sequential
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).chunkIndex()).isEqualTo(i);
        }
    }

    // ==================== Overlap Application Tests ====================

    @Test
    @DisplayName("When overlap is configured Then consecutive chunks have correct step size")
    void testOverlapApplication() {
        // Given: text with known content and overlap configuration
        String text = "A".repeat(200); // Long text without word boundaries
        int chunkSize = 50;
        int overlapSize = 10;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: consecutive chunks should advance by step size (chunkSize - overlapSize)
        assertThat(chunks.size()).isGreaterThan(1);
        
        int expectedStepSize = chunkSize - overlapSize;
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // Verify that chunks advance by the expected step size
            int actualStep = nextChunk.startPosition() - currentChunk.startPosition();
            assertThat(actualStep)
                .as("Chunks should advance by step size %d", expectedStepSize)
                .isEqualTo(expectedStepSize);
        }
    }

    @Test
    @DisplayName("When overlap is zero Then chunks do not overlap")
    void testNoOverlap() {
        // Given: text with zero overlap
        String text = "AAAAAAAAAA BBBBBBBBBB CCCCCCCCCC DDDDDDDDDD";
        int chunkSize = 15;
        int overlapSize = 0;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: chunks should be adjacent without overlap
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // With zero overlap and word boundary preservation,
            // next chunk should start at or after current chunk ends
            assertThat(nextChunk.startPosition())
                .as("With zero overlap, next chunk should start at or after current ends")
                .isGreaterThanOrEqualTo(currentChunk.endPosition());
        }
    }

    @Test
    @DisplayName("When overlap is large Then chunks have significant overlap")
    void testLargeOverlap() {
        // Given: text with large overlap
        String text = "The quick brown fox jumps over the lazy dog and runs away quickly.";
        int chunkSize = 30;
        int overlapSize = 15;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should produce chunks with significant overlap
        assertThat(chunks.size()).isGreaterThan(1);
        
        // Verify overlap is applied (step size should be chunkSize - overlapSize)
        int expectedStepSize = chunkSize - overlapSize;
        for (int i = 0; i < chunks.size() - 1; i++) {
            TextChunk currentChunk = chunks.get(i);
            TextChunk nextChunk = chunks.get(i + 1);
            
            // Next chunk should start approximately stepSize characters after current
            // (with some tolerance for word boundary adjustments)
            int actualStep = nextChunk.startPosition() - currentChunk.startPosition();
            assertThat(actualStep)
                .as("Step between chunks should be approximately %d", expectedStepSize)
                .isCloseTo(expectedStepSize, within(20)); // Allow tolerance for word boundaries
        }
    }

    // ==================== Edge Case: Short Text ====================

    @Test
    @DisplayName("When text is shorter than chunk size Then returns single chunk with full text")
    void testShortText() {
        // Given: text shorter than chunk size
        String text = "Short text";
        int chunkSize = 100;
        int overlapSize = 10;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should return single chunk containing all text
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo(text);
        assertThat(chunks.get(0).chunkIndex()).isEqualTo(0);
        assertThat(chunks.get(0).startPosition()).isEqualTo(0);
        assertThat(chunks.get(0).endPosition()).isEqualTo(text.length());
        assertThat(chunks.get(0).metadata()).isEqualTo(testMetadata);
    }

    @Test
    @DisplayName("When text is single character Then returns single chunk")
    void testSingleCharacter() {
        // Given: single character text
        String text = "A";
        int chunkSize = 50;
        int overlapSize = 5;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should return single chunk
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("A");
        assertThat(chunks.get(0).text().length()).isEqualTo(1);
    }

    @Test
    @DisplayName("When text is very short Then chunk metadata is correct")
    void testShortTextMetadata() {
        // Given: very short text
        String text = "Hi";
        int chunkSize = 100;
        int overlapSize = 0;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: metadata should be correctly attached
        assertThat(chunks).hasSize(1);
        TextChunk chunk = chunks.get(0);
        assertThat(chunk.metadata().filename()).isEqualTo("test-document.pdf");
        assertThat(chunk.metadata().fileType()).isEqualTo("pdf");
        assertThat(chunk.chunkIndex()).isEqualTo(0);
    }

    // ==================== Edge Case: Empty Text ====================

    @Test
    @DisplayName("When text is empty Then returns empty list")
    void testEmptyText() {
        // Given: empty text
        String text = "";
        int chunkSize = 50;
        int overlapSize = 10;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should return empty list
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("When text is empty with zero overlap Then returns empty list")
    void testEmptyTextZeroOverlap() {
        // Given: empty text with zero overlap
        String text = "";
        int chunkSize = 100;
        int overlapSize = 0;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should return empty list
        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("When text is empty with large chunk size Then returns empty list")
    void testEmptyTextLargeChunkSize() {
        // Given: empty text with large chunk size
        String text = "";
        int chunkSize = 10000;
        int overlapSize = 100;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should return empty list
        assertThat(chunks).isEmpty();
    }

    // ==================== Additional Edge Cases ====================

    @Test
    @DisplayName("When text contains only whitespace Then chunks correctly")
    void testWhitespaceOnlyText() {
        // Given: text containing only whitespace
        String text = "     ";
        int chunkSize = 10;
        int overlapSize = 2;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should handle whitespace text
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo(text);
    }

    @Test
    @DisplayName("When text has no word boundaries Then still chunks correctly")
    void testNoWordBoundaries() {
        // Given: text with no spaces or punctuation
        String text = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        int chunkSize = 10;
        int overlapSize = 2;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should chunk at fixed positions
        assertThat(chunks.size()).isGreaterThan(1);
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length()).isLessThanOrEqualTo(chunkSize);
        }
    }

    @Test
    @DisplayName("When chunk size is minimal Then produces many small chunks")
    void testMinimalChunkSize() {
        // Given: text with minimal chunk size
        String text = "The quick brown fox";
        int chunkSize = 5;
        int overlapSize = 1;

        // When: chunking the text
        List<TextChunk> chunks = chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize);

        // Then: should produce multiple small chunks
        assertThat(chunks.size()).isGreaterThan(2);
        
        for (TextChunk chunk : chunks) {
            assertThat(chunk.text().length())
                .as("Each chunk should not exceed minimal chunk size")
                .isLessThanOrEqualTo(chunkSize);
        }
    }

    // ==================== Validation Tests ====================

    @Test
    @DisplayName("When text is null Then throws NullPointerException")
    void testNullText() {
        // Given: null text
        String text = null;
        int chunkSize = 50;
        int overlapSize = 10;

        // When/Then: should throw NullPointerException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Text must not be null");
    }

    @Test
    @DisplayName("When metadata is null Then throws NullPointerException")
    void testNullMetadata() {
        // Given: null metadata
        String text = "Test text";
        DocumentMetadata metadata = null;
        int chunkSize = 50;
        int overlapSize = 10;

        // When/Then: should throw NullPointerException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, metadata, chunkSize, overlapSize)
        )
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("Metadata must not be null");
    }

    @Test
    @DisplayName("When chunk size is zero Then throws IllegalArgumentException")
    void testZeroChunkSize() {
        // Given: zero chunk size
        String text = "Test text";
        int chunkSize = 0;
        int overlapSize = 0;

        // When/Then: should throw IllegalArgumentException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chunk size must be positive");
    }

    @Test
    @DisplayName("When chunk size is negative Then throws IllegalArgumentException")
    void testNegativeChunkSize() {
        // Given: negative chunk size
        String text = "Test text";
        int chunkSize = -10;
        int overlapSize = 0;

        // When/Then: should throw IllegalArgumentException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Chunk size must be positive");
    }

    @Test
    @DisplayName("When overlap size is negative Then throws IllegalArgumentException")
    void testNegativeOverlapSize() {
        // Given: negative overlap size
        String text = "Test text";
        int chunkSize = 50;
        int overlapSize = -5;

        // When/Then: should throw IllegalArgumentException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlap size must be non-negative");
    }

    @Test
    @DisplayName("When overlap size equals chunk size Then throws IllegalArgumentException")
    void testOverlapEqualsChunkSize() {
        // Given: overlap size equal to chunk size
        String text = "Test text";
        int chunkSize = 50;
        int overlapSize = 50;

        // When/Then: should throw IllegalArgumentException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlap size must be less than chunk size");
    }

    @Test
    @DisplayName("When overlap size exceeds chunk size Then throws IllegalArgumentException")
    void testOverlapExceedsChunkSize() {
        // Given: overlap size greater than chunk size
        String text = "Test text";
        int chunkSize = 50;
        int overlapSize = 60;

        // When/Then: should throw IllegalArgumentException
        assertThatThrownBy(() -> 
            chunkingService.chunkText(text, testMetadata, chunkSize, overlapSize)
        )
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Overlap size must be less than chunk size");
    }
}

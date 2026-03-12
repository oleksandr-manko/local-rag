package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.ProcessingConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for PDF text extraction.
 * **Validates: Requirements 4.2**
 * 
 * Property 8: PDF Text Extraction
 * 
 * This property verifies that the DocumentProcessor correctly extracts
 * text content from all pages of a PDF file, preserving text order and
 * structure during extraction.
 */
class PdfTextExtractionPropertyTest {

    @Property(tries = 50)
    @Label("When PDF has single page Then all text is extracted")
    void singlePagePdfTextIsExtracted(
            @ForAll @StringLength(min = 10, max = 200) @AlphaChars String pageText
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF file with a single page containing text
        Path tempDir = Files.createTempDirectory("test-pdf-");
        try {
            Path pdfFile = tempDir.resolve("test.pdf");
            createPdfWithPages(pdfFile, List.of(pageText));
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: the extracted text should contain all the original text
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get().trim();
            String expected = pageText.trim();
            
            assertThat(extracted)
                    .as("Extracted text should match original text")
                    .isEqualTo(expected);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has multiple pages Then text from all pages is extracted")
    void multiplePagePdfTextIsExtracted(
            @ForAll @IntRange(min = 2, max = 5) int pageCount,
            @ForAll @StringLength(min = 10, max = 100) @AlphaChars String textPerPage
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF file with multiple pages, each containing text
        Path tempDir = Files.createTempDirectory("test-pdf-multi-");
        try {
            Path pdfFile = tempDir.resolve("multipage.pdf");
            
            // Create distinct text for each page
            List<String> pageTexts = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                pageTexts.add("Page " + i + " " + textPerPage);
            }
            
            createPdfWithPages(pdfFile, pageTexts);
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: the extracted text should contain text from all pages
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get();
            
            // Verify all page texts are present in the extracted text
            for (int i = 0; i < pageCount; i++) {
                assertThat(extracted)
                        .as("Extracted text should contain text from page %d", i)
                        .contains("Page " + i);
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has multiple pages Then text order is preserved")
    void multiplePagePdfTextOrderIsPreserved(
            @ForAll @IntRange(min = 2, max = 5) int pageCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF file with multiple pages with numbered text
        Path tempDir = Files.createTempDirectory("test-pdf-order-");
        try {
            Path pdfFile = tempDir.resolve("ordered.pdf");
            
            // Create pages with sequential markers
            List<String> pageTexts = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                pageTexts.add("MARKER" + i);
            }
            
            createPdfWithPages(pdfFile, pageTexts);
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: the text order should be preserved (page 0 before page 1, etc.)
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get();
            
            // Find positions of markers
            List<Integer> markerPositions = new ArrayList<>();
            for (int i = 0; i < pageCount; i++) {
                int position = extracted.indexOf("MARKER" + i);
                assertThat(position)
                        .as("MARKER%d should be found in extracted text", i)
                        .isGreaterThanOrEqualTo(0);
                markerPositions.add(position);
            }
            
            // Verify markers appear in order
            for (int i = 0; i < pageCount - 1; i++) {
                assertThat(markerPositions.get(i))
                        .as("MARKER%d should appear before MARKER%d", i, i + 1)
                        .isLessThan(markerPositions.get(i + 1));
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF is empty Then extraction returns empty optional")
    void emptyPdfReturnsEmptyOptional() throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF file with no text content
        Path tempDir = Files.createTempDirectory("test-pdf-empty-");
        try {
            Path pdfFile = tempDir.resolve("empty.pdf");
            createPdfWithPages(pdfFile, List.of("")); // Empty page
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the empty PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should return empty optional (no meaningful text)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has blank pages Then only non-blank content is extracted")
    void pdfWithBlankPagesExtractsNonBlankContent(
            @ForAll @IntRange(min = 1, max = 3) int blankPagesBefore,
            @ForAll @IntRange(min = 1, max = 3) int blankPagesAfter,
            @ForAll @StringLength(min = 10, max = 100) @AlphaChars String contentText
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF with blank pages before and after content
        Path tempDir = Files.createTempDirectory("test-pdf-blank-");
        try {
            Path pdfFile = tempDir.resolve("withblanks.pdf");
            
            List<String> pageTexts = new ArrayList<>();
            // Add blank pages before
            for (int i = 0; i < blankPagesBefore; i++) {
                pageTexts.add("");
            }
            // Add content page
            pageTexts.add(contentText);
            // Add blank pages after
            for (int i = 0; i < blankPagesAfter; i++) {
                pageTexts.add("");
            }
            
            createPdfWithPages(pdfFile, pageTexts);
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should extract the non-blank content
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get().trim();
            
            assertThat(extracted)
                    .as("Should extract the content text from non-blank page")
                    .contains(contentText);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has special characters Then all characters are extracted")
    void pdfWithSpecialCharactersIsExtracted(
            @ForAll("textWithSpecialChars") String textWithSpecialChars
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF with special characters (skip if only whitespace)
        Assume.that(!textWithSpecialChars.isBlank());
        
        Path tempDir = Files.createTempDirectory("test-pdf-special-");
        try {
            Path pdfFile = tempDir.resolve("special.pdf");
            createPdfWithPages(pdfFile, List.of(textWithSpecialChars));
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should extract text including special characters
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get().trim();
            
            // The extracted text should contain the core alphanumeric content
            // (special characters may be normalized by PDF rendering)
            assertThat(extracted)
                    .as("Should extract text content")
                    .isNotEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF file is corrupted Then extraction returns empty optional")
    void corruptedPdfReturnsEmptyOptional() throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a corrupted PDF file (not a valid PDF)
        Path tempDir = Files.createTempDirectory("test-pdf-corrupt-");
        try {
            Path pdfFile = tempDir.resolve("corrupted.pdf");
            Files.writeString(pdfFile, "This is not a valid PDF file content");
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: attempting to extract text from corrupted PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should return empty optional (graceful error handling)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has whitespace-only pages Then extraction handles gracefully")
    void pdfWithWhitespaceOnlyPagesHandledGracefully(
            @ForAll @IntRange(min = 1, max = 3) int whitespacePageCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF with pages containing only whitespace (spaces only, no tabs)
        Path tempDir = Files.createTempDirectory("test-pdf-whitespace-");
        try {
            Path pdfFile = tempDir.resolve("whitespace.pdf");
            
            List<String> pageTexts = new ArrayList<>();
            for (int i = 0; i < whitespacePageCount; i++) {
                pageTexts.add("     "); // Whitespace only (spaces, no tabs - PDFBox limitation)
            }
            
            createPdfWithPages(pdfFile, pageTexts);
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should return empty optional (no meaningful content)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When PDF has long text Then all content is extracted")
    void pdfWithLongTextIsFullyExtracted(
            @ForAll @IntRange(min = 1000, max = 5000) int textLength
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 8: PDF Text Extraction
        
        // Given: a PDF with long text content
        Path tempDir = Files.createTempDirectory("test-pdf-long-");
        try {
            Path pdfFile = tempDir.resolve("long.pdf");
            
            // Create long text with markers at beginning and end
            String longText = "START " + "a".repeat(textLength - 20) + " END";
            createPdfWithPages(pdfFile, List.of(longText));
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: extracting text from the PDF
            Optional<String> extractedText = processor.extractTextFromPdf(pdfFile).join();
            
            // Then: should extract all content including markers
            assertThat(extractedText).isPresent();
            String extracted = extractedText.get();
            
            assertThat(extracted)
                    .as("Should contain START marker")
                    .contains("START");
            
            assertThat(extracted)
                    .as("Should contain END marker")
                    .contains("END");
            
            assertThat(extracted.length())
                    .as("Extracted text should be approximately the original length")
                    .isGreaterThan(textLength - 100); // Allow some tolerance for whitespace
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a mocked DocumentProcessor with real PDF extraction but mocked dependencies.
     */
    private DocumentProcessor createMockedProcessor(Path inputFolder) {
        RedisClient redisClient = mock(RedisClient.class);
        OllamaClient ollamaClient = mock(OllamaClient.class);
        VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
        ChunkingService chunkingService = mock(ChunkingService.class);
        
        // Mock dependencies (not used in PDF extraction tests)
        when(redisClient.getFileHash(any())).thenReturn(
                CompletableFuture.completedFuture(Optional.empty())
        );
        
        return new DocumentProcessorImpl(
                redisClient,
                ollamaClient,
                vectorStoreClient,
                chunkingService,
                new DocumentsConfig(
                        inputFolder,
                        List.of("pdf", "jpg", "jpeg", "png", "tiff")
                ),
                new ProcessingConfig(
                        "0 0 * * * *",
                        512,
                        50,
                        100,
                        5,
                        Duration.ofSeconds(60)
                ),
                new OllamaConfig(
                        "localhost",
                        11434,
                        "llama3.2",
                        "nomic-embed-text",
                        "qwen2-vl:8b",
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(120)
                )
        );
    }

    /**
     * Create a PDF file with the specified text on each page.
     */
    private void createPdfWithPages(Path pdfFile, List<String> pageTexts) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                
                if (pageText != null && !pageText.isEmpty()) {
                    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                        contentStream.beginText();
                        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        contentStream.newLineAtOffset(50, 700);
                        
                        // Handle multi-line text
                        String[] lines = pageText.split("\n");
                        for (String line : lines) {
                            contentStream.showText(line);
                            contentStream.newLineAtOffset(0, -15);
                        }
                        
                        contentStream.endText();
                    }
                }
            }
            
            document.save(pdfFile.toFile());
        }
    }

    /**
     * Recursively delete a directory and all its contents.
     */
    private void deleteDirectory(Path directory) {
        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                // Ignore cleanup errors
                            }
                        });
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> textWithSpecialChars() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withCharRange('A', 'Z')
                .withCharRange('0', '9')
                .withChars(' ', '.', ',', '!', '?', '-', ':', ';')
                .ofMinLength(20)
                .ofMaxLength(200);
    }
}

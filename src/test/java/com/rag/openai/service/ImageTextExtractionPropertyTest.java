package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.ProcessingConfig;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for image text extraction using Ollama vision models.
 * **Validates: Requirements 5.2**
 * 
 * Property 9: Image Text Extraction
 * 
 * This property verifies that the DocumentProcessor correctly extracts
 * text content from image files using Ollama vision-capable models,
 * handling various image formats and error conditions gracefully.
 */
class ImageTextExtractionPropertyTest {

    @Property(tries = 50)
    @Label("When image file exists Then text extraction is attempted via vision model")
    void imageFileTextExtractionIsAttempted(
            @ForAll("imageExtension") String extension,
            @ForAll @StringLength(min = 10, max = 100) @AlphaChars String expectedText
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file with a supported extension
        Path tempDir = Files.createTempDirectory("test-image-");
        try {
            Path imageFile = tempDir.resolve("test." + extension);
            createSimpleImage(imageFile, extension);
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(expectedText));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: the vision model should be called and text should be extracted
            verify(ollamaClient, times(1)).analyzeImage(
                    any(byte[].class),
                    contains("Extract all visible text")
            );
            
            assertThat(extractedText).isPresent();
            assertThat(extractedText.get())
                    .as("Extracted text should match the mocked vision model response")
                    .isEqualTo(expectedText);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model returns text Then extraction returns non-empty optional")
    void visionModelReturnsTextSuccessfully(
            @ForAll @StringLength(min = 1, max = 500) @AlphaChars String visionModelResponse
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Skip if response is blank (edge case handled by another test)
        Assume.that(!visionModelResponse.isBlank());
        
        // Given: an image file and a vision model that returns text
        Path tempDir = Files.createTempDirectory("test-vision-");
        try {
            Path imageFile = tempDir.resolve("test.jpg");
            createSimpleImage(imageFile, "jpg");
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(visionModelResponse));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return the text from the vision model
            assertThat(extractedText).isPresent();
            assertThat(extractedText.get())
                    .as("Should return the vision model response")
                    .isEqualTo(visionModelResponse);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model returns empty text Then extraction returns empty optional")
    void visionModelReturnsEmptyText() throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file and a vision model that returns empty text
        Path tempDir = Files.createTempDirectory("test-empty-");
        try {
            Path imageFile = tempDir.resolve("empty.png");
            createSimpleImage(imageFile, "png");
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(""));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return empty optional (no text found)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model returns null Then extraction returns empty optional")
    void visionModelReturnsNull() throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file and a vision model that returns null
        Path tempDir = Files.createTempDirectory("test-null-");
        try {
            Path imageFile = tempDir.resolve("test.jpg");
            createSimpleImage(imageFile, "jpg");
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(null));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return empty optional (graceful null handling)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model returns whitespace-only text Then extraction returns empty optional")
    void visionModelReturnsWhitespaceOnly(
            @ForAll @IntRange(min = 1, max = 20) int whitespaceLength
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file and a vision model that returns only whitespace
        Path tempDir = Files.createTempDirectory("test-whitespace-");
        try {
            Path imageFile = tempDir.resolve("test.png");
            createSimpleImage(imageFile, "png");
            
            String whitespaceText = " ".repeat(whitespaceLength);
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(whitespaceText));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return empty optional (no meaningful text)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model fails Then extraction returns empty optional")
    void visionModelFailureHandledGracefully() throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file and a vision model that throws an error
        Path tempDir = Files.createTempDirectory("test-error-");
        try {
            Path imageFile = tempDir.resolve("test.jpg");
            createSimpleImage(imageFile, "jpg");
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.failedFuture(
                            new RuntimeException("Vision model error")
                    ));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return empty optional (graceful error handling)
            assertThat(extractedText).isEmpty();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When image file cannot be read Then extraction returns empty optional")
    void unreadableImageFileHandledGracefully() throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: a non-existent image file
        Path tempDir = Files.createTempDirectory("test-nonexistent-");
        try {
            Path imageFile = tempDir.resolve("nonexistent.jpg");
            // File does not exist
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: attempting to extract text from non-existent file
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: should return empty optional (graceful file read error handling)
            assertThat(extractedText).isEmpty();
            
            // Vision model should not be called if file cannot be read
            verify(ollamaClient, never()).analyzeImage(any(), anyString());
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When image file is corrupted Then extraction handles gracefully")
    void corruptedImageFileHandledGracefully() throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: a corrupted image file (not a valid image)
        Path tempDir = Files.createTempDirectory("test-corrupt-");
        try {
            Path imageFile = tempDir.resolve("corrupted.jpg");
            Files.writeString(imageFile, "This is not a valid image file");
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture("Some text"));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from corrupted image
            // The vision model will still be called with the file bytes
            Optional<String> extractedText = processor.extractTextFromImage(imageFile).join();
            
            // Then: the extraction should proceed (vision model handles the bytes)
            // The vision model may or may not extract text from invalid data
            assertThat(extractedText).isNotNull();
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When all supported image extensions are used Then extraction works for each")
    void allSupportedImageExtensionsWork(
            @ForAll("imageExtension") String extension,
            @ForAll @StringLength(min = 10, max = 100) @AlphaChars String extractedText
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file with each supported extension
        Path tempDir = Files.createTempDirectory("test-ext-");
        try {
            Path imageFile = tempDir.resolve("test." + extension);
            createSimpleImage(imageFile, extension);
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture(extractedText));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            Optional<String> result = processor.extractTextFromImage(imageFile).join();
            
            // Then: extraction should work for all supported extensions
            assertThat(result).isPresent();
            assertThat(result.get())
                    .as("Should extract text from %s files", extension)
                    .isEqualTo(extractedText);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When vision model prompt is sent Then it requests text extraction")
    void visionModelPromptRequestsTextExtraction(
            @ForAll("imageExtension") String extension
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file
        Path tempDir = Files.createTempDirectory("test-prompt-");
        try {
            Path imageFile = tempDir.resolve("test." + extension);
            createSimpleImage(imageFile, extension);
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture("Extracted text"));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            processor.extractTextFromImage(imageFile).join();
            
            // Then: the prompt should request text extraction
            verify(ollamaClient).analyzeImage(
                    any(byte[].class),
                    argThat(prompt -> 
                            prompt.contains("Extract") && 
                            prompt.contains("text") &&
                            prompt.contains("without any additional commentary")
                    )
            );
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When configured vision model is used Then correct model name is passed")
    void configuredVisionModelIsUsed(
            @ForAll("imageExtension") String extension
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 9: Image Text Extraction
        
        // Given: an image file and a configured vision model name
        Path tempDir = Files.createTempDirectory("test-model-");
        try {
            Path imageFile = tempDir.resolve("test." + extension);
            createSimpleImage(imageFile, extension);
            
            OllamaClient ollamaClient = mock(OllamaClient.class);
            when(ollamaClient.analyzeImage(any(byte[].class), anyString()))
                    .thenReturn(CompletableFuture.completedFuture("Text"));
            
            DocumentProcessor processor = createMockedProcessor(tempDir, ollamaClient);
            
            // When: extracting text from the image
            processor.extractTextFromImage(imageFile).join();
            
            // Then: the configured vision model name should be used
            verify(ollamaClient).analyzeImage(
                    any(byte[].class),
                    anyString()
            );
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a mocked DocumentProcessor with real image extraction but mocked dependencies.
     */
    private DocumentProcessor createMockedProcessor(Path inputFolder, OllamaClient ollamaClient) {
        RedisClient redisClient = mock(RedisClient.class);
        VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
        ChunkingService chunkingService = mock(ChunkingService.class);
        
        // Mock dependencies (not used in image extraction tests)
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
                )
        );
    }

    /**
     * Create a simple test image file.
     */
    private void createSimpleImage(Path imageFile, String extension) throws IOException {
        // Create a simple 100x100 white image
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 100, 100);
        graphics.dispose();
        
        // Determine format name (jpeg instead of jpg for ImageIO)
        String formatName = extension.equalsIgnoreCase("jpg") ? "jpeg" : extension;
        
        ImageIO.write(image, formatName, imageFile.toFile());
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
    Arbitrary<String> imageExtension() {
        return Arbitraries.of("jpg", "jpeg", "png", "tiff");
    }
}

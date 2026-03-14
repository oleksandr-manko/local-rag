package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.ProcessingConfig;
import com.rag.openai.domain.model.ProcessingResult;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for document discovery.
 * **Validates: Requirements 4.1, 5.1**
 * 
 * Property 7: Document Discovery
 * 
 * This property verifies that the DocumentProcessor correctly discovers
 * and identifies all supported file types (PDFs and images) in the
 * configured input folder, regardless of folder structure or file naming.
 */
class DocumentDiscoveryPropertyTest {

    @Property(tries = 100)
    @Label("When folder contains PDF files Then all PDFs are discovered")
    void allPdfFilesAreDiscovered(
            @ForAll @IntRange(min = 1, max = 10) int pdfCount,
            @ForAll @IntRange(min = 0, max = 5) int otherFileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with PDF files and other files
        Path tempDir = Files.createTempDirectory("test-docs-");
        try {
            Set<String> pdfFiles = new HashSet<>();
            
            // Create PDF files
            for (int i = 0; i < pdfCount; i++) {
                String filename = "document_" + i + ".pdf";
                Path pdfFile = tempDir.resolve(filename);
                Files.writeString(pdfFile, "PDF content " + i);
                pdfFiles.add(filename);
            }
            
            // Create non-supported files
            for (int i = 0; i < otherFileCount; i++) {
                Path otherFile = tempDir.resolve("other_" + i + ".txt");
                Files.writeString(otherFile, "Other content");
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all PDF files should be discovered and processed or skipped
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover all %d PDF files", pdfCount)
                    .isEqualTo(pdfCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains image files Then all supported images are discovered")
    void allImageFilesAreDiscovered(
            @ForAll @IntRange(min = 1, max = 10) int imageCount,
            @ForAll @IntRange(min = 0, max = 5) int otherFileCount,
            @ForAll("imageExtension") String extension
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with image files and other files
        Path tempDir = Files.createTempDirectory("test-images-");
        try {
            Set<String> imageFiles = new HashSet<>();
            
            // Create image files
            for (int i = 0; i < imageCount; i++) {
                String filename = "image_" + i + "." + extension;
                Path imageFile = tempDir.resolve(filename);
                Files.writeString(imageFile, "Image content " + i);
                imageFiles.add(filename);
            }
            
            // Create non-supported files
            for (int i = 0; i < otherFileCount; i++) {
                Path otherFile = tempDir.resolve("other_" + i + ".txt");
                Files.writeString(otherFile, "Other content");
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all image files should be discovered and processed or skipped
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover all %d image files with extension %s", imageCount, extension)
                    .isEqualTo(imageCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains mixed file types Then only supported files are discovered")
    void onlySupportedFilesAreDiscovered(
            @ForAll @IntRange(min = 0, max = 5) int pdfCount,
            @ForAll @IntRange(min = 0, max = 5) int jpgCount,
            @ForAll @IntRange(min = 0, max = 5) int pngCount,
            @ForAll @IntRange(min = 1, max = 10) int unsupportedCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with mixed file types
        Path tempDir = Files.createTempDirectory("test-mixed-");
        try {
            int expectedSupportedFiles = pdfCount + jpgCount + pngCount;
            
            // Skip test if no supported files
            Assume.that(expectedSupportedFiles > 0);
            
            // Create PDF files
            for (int i = 0; i < pdfCount; i++) {
                Path pdfFile = tempDir.resolve("doc_" + i + ".pdf");
                Files.writeString(pdfFile, "PDF content");
            }
            
            // Create JPG files
            for (int i = 0; i < jpgCount; i++) {
                Path jpgFile = tempDir.resolve("image_" + i + ".jpg");
                Files.writeString(jpgFile, "JPG content");
            }
            
            // Create PNG files
            for (int i = 0; i < pngCount; i++) {
                Path pngFile = tempDir.resolve("image_" + i + ".png");
                Files.writeString(pngFile, "PNG content");
            }
            
            // Create unsupported files
            String[] unsupportedExtensions = {"txt", "doc", "docx", "xml", "json", "csv"};
            for (int i = 0; i < unsupportedCount; i++) {
                String ext = unsupportedExtensions[i % unsupportedExtensions.length];
                Path unsupportedFile = tempDir.resolve("unsupported_" + i + "." + ext);
                Files.writeString(unsupportedFile, "Unsupported content");
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: only supported files should be discovered
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover only %d supported files (PDFs: %d, JPGs: %d, PNGs: %d), not %d unsupported files",
                            expectedSupportedFiles, pdfCount, jpgCount, pngCount, unsupportedCount)
                    .isEqualTo(expectedSupportedFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When folder has nested subdirectories Then all supported files are discovered recursively")
    void nestedFilesAreDiscoveredRecursively(
            @ForAll @IntRange(min = 1, max = 3) int depth,
            @ForAll @IntRange(min = 1, max = 3) int filesPerLevel
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with nested subdirectories containing files
        Path tempDir = Files.createTempDirectory("test-nested-");
        try {
            int totalFiles = createNestedStructure(tempDir, depth, filesPerLevel, 0);
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files in all subdirectories should be discovered
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover all %d files across %d levels of nesting", totalFiles, depth)
                    .isEqualTo(totalFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains files with case variations Then files are discovered case-insensitively")
    void filesWithCaseVariationsAreDiscovered(
            @ForAll @IntRange(min = 1, max = 5) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with files having different case extensions
        Path tempDir = Files.createTempDirectory("test-case-");
        try {
            String[] extensions = {"PDF", "pdf", "Pdf", "JPG", "jpg", "Jpg", "PNG", "png", "Png"};
            
            for (int i = 0; i < fileCount; i++) {
                String ext = extensions[i % extensions.length];
                Path file = tempDir.resolve("file_" + i + "." + ext);
                Files.writeString(file, "Content " + i);
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files should be discovered regardless of case
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover all %d files regardless of extension case", fileCount)
                    .isEqualTo(fileCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains all supported image extensions Then all are discovered")
    void allSupportedImageExtensionsAreDiscovered(
            @ForAll @IntRange(min = 1, max = 3) int filesPerExtension
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with all supported image extensions (jpg, jpeg, png, tiff)
        Path tempDir = Files.createTempDirectory("test-all-images-");
        try {
            String[] imageExtensions = {"jpg", "jpeg", "png", "tiff"};
            int expectedFiles = imageExtensions.length * filesPerExtension;
            
            for (String ext : imageExtensions) {
                for (int i = 0; i < filesPerExtension; i++) {
                    Path imageFile = tempDir.resolve(ext + "_file_" + i + "." + ext);
                    Files.writeString(imageFile, "Image content");
                }
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all image files should be discovered
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Should discover all %d image files across all supported extensions", expectedFiles)
                    .isEqualTo(expectedFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When folder is empty Then no files are discovered")
    void emptyFolderDiscoveredNoFiles() throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: an empty folder
        Path tempDir = Files.createTempDirectory("test-empty-");
        try {
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: no files should be discovered
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Empty folder should discover 0 files")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When folder contains only subdirectories with no files Then no files are discovered")
    void emptySubdirectoriesDiscoverNoFiles(
            @ForAll @IntRange(min = 1, max = 5) int subdirCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 7: Document Discovery
        
        // Given: a folder with empty subdirectories
        Path tempDir = Files.createTempDirectory("test-empty-subdirs-");
        try {
            for (int i = 0; i < subdirCount; i++) {
                Files.createDirectories(tempDir.resolve("subdir_" + i));
            }
            
            DocumentProcessor processor = createMockedProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: no files should be discovered
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Folder with only empty subdirectories should discover 0 files")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a mocked DocumentProcessor with real file scanning but mocked dependencies.
     */
    private DocumentProcessor createMockedProcessor(Path inputFolder) {
        RedisClient redisClient = mock(RedisClient.class);
        OllamaClient ollamaClient = mock(OllamaClient.class);
        VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
        ChunkingService chunkingService = mock(ChunkingService.class);
        
        // Mock Redis to return stored hash that matches (so files are skipped, not processed)
        // This way we test discovery without worrying about extraction failures
        when(redisClient.getFileHash(any())).thenReturn(
                CompletableFuture.completedFuture(Optional.of("matching-hash"))
        );
        when(redisClient.storeFileHash(any(), any())).thenReturn(
                CompletableFuture.completedFuture(null)
        );
        
        // Mock Ollama to return valid text for images
        when(ollamaClient.analyzeImage(any(), any())).thenReturn(
                CompletableFuture.completedFuture("Extracted text from image")
        );
        when(ollamaClient.generateEmbedding(any())).thenReturn(
                CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f))
        );
        
        // Mock VectorStore operations
        when(vectorStoreClient.deleteEmbeddingsByFilename(any())).thenReturn(
                CompletableFuture.completedFuture(null)
        );
        when(vectorStoreClient.storeEmbeddings(any())).thenReturn(
                CompletableFuture.completedFuture(null)
        );
        
        // Mock ChunkingService to return a single chunk
        when(chunkingService.chunkText(any(), any(), anyInt(), anyInt())).thenReturn(
                List.of()
        );
        
        // Create a spy to override computeFileHash to return consistent hash
        DocumentProcessor realProcessor = new DocumentProcessorImpl(
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
        
        DocumentProcessor processor = spy(realProcessor);
        
        // Override computeFileHash to return consistent hash that matches Redis mock
        when(processor.computeFileHash(any())).thenReturn(
                CompletableFuture.completedFuture("matching-hash")
        );
        
        // Override computeFileHash to return consistent hash that matches Redis mock
        when(processor.computeFileHash(any())).thenReturn(
                CompletableFuture.completedFuture("matching-hash")
        );
        
        return processor;
    }

    /**
     * Create nested directory structure with files.
     * Returns the total number of files created.
     */
    private int createNestedStructure(Path parent, int depth, int filesPerLevel, int currentDepth) throws IOException {
        if (currentDepth >= depth) {
            return 0;
        }
        
        int totalFiles = 0;
        
        // Create files at current level
        for (int i = 0; i < filesPerLevel; i++) {
            Path file = parent.resolve("file_" + currentDepth + "_" + i + ".pdf");
            Files.writeString(file, "Content at depth " + currentDepth);
            totalFiles++;
        }
        
        // Create subdirectory and recurse
        Path subdir = parent.resolve("subdir_" + currentDepth);
        Files.createDirectories(subdir);
        totalFiles += createNestedStructure(subdir, depth, filesPerLevel, currentDepth + 1);
        
        return totalFiles;
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

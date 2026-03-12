package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.OllamaConfig;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for complete folder processing.
 * **Validates: Requirements 8.2**
 * 
 * Property 19: Complete Folder Processing
 * 
 * This property verifies that when the Processing_Job executes,
 * it processes ALL documents in the configured input folder,
 * ensuring no files are missed during discovery and processing.
 */
class CompleteFolderProcessingPropertyTest {

    @Property(tries = 100)
    @Label("When folder contains N files Then all N files are discovered and processed")
    void allFilesInFolderAreProcessed(
            @ForAll @IntRange(min = 1, max = 20) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with N files
        Path tempDir = Files.createTempDirectory("test-complete-");
        try {
            Set<String> createdFiles = new HashSet<>();
            
            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Content " + i);
                createdFiles.add(file.getFileName().toString());
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all N files should be processed
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files in folder should be discovered and processed", fileCount)
                    .isEqualTo(fileCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains mixed file types Then all supported files are processed")
    void allSupportedFileTypesAreProcessed(
            @ForAll @IntRange(min = 1, max = 5) int pdfCount,
            @ForAll @IntRange(min = 1, max = 5) int jpgCount,
            @ForAll @IntRange(min = 1, max = 5) int pngCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with mixed supported file types
        Path tempDir = Files.createTempDirectory("test-mixed-types-");
        try {
            int totalFiles = 0;
            
            // Create PDF files
            for (int i = 0; i < pdfCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "PDF content " + i);
                totalFiles++;
            }
            
            // Create JPG files
            for (int i = 0; i < jpgCount; i++) {
                Path file = tempDir.resolve("image_" + i + ".jpg");
                Files.writeString(file, "JPG content " + i);
                totalFiles++;
            }
            
            // Create PNG files
            for (int i = 0; i < pngCount; i++) {
                Path file = tempDir.resolve("image_" + i + ".png");
                Files.writeString(file, "PNG content " + i);
                totalFiles++;
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all supported files should be processed
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d supported files should be discovered and processed", totalFiles)
                    .isEqualTo(totalFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains unsupported files Then only supported files are processed")
    void unsupportedFilesAreIgnored(
            @ForAll @IntRange(min = 1, max = 5) int supportedCount,
            @ForAll @IntRange(min = 1, max = 5) int unsupportedCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with both supported and unsupported files
        Path tempDir = Files.createTempDirectory("test-unsupported-");
        try {
            // Create supported files (PDF)
            for (int i = 0; i < supportedCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "PDF content " + i);
            }
            
            // Create unsupported files (TXT, DOCX, etc.)
            for (int i = 0; i < unsupportedCount; i++) {
                Path file = tempDir.resolve("unsupported_" + i + ".txt");
                Files.writeString(file, "Text content " + i);
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: only supported files should be processed
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("Only %d supported files should be processed, ignoring %d unsupported files", 
                        supportedCount, unsupportedCount)
                    .isEqualTo(supportedCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains subdirectories Then all files including subdirectories are processed")
    void subdirectoriesAreTraversed(
            @ForAll @IntRange(min = 1, max = 5) int rootFiles,
            @ForAll @IntRange(min = 1, max = 5) int subFiles
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with files in root and subdirectories
        Path tempDir = Files.createTempDirectory("test-subdir-");
        try {
            // Create files in root
            for (int i = 0; i < rootFiles; i++) {
                Path file = tempDir.resolve("root_" + i + ".pdf");
                Files.writeString(file, "Root content " + i);
            }
            
            // Create subdirectory with files
            Path subDir = tempDir.resolve("subfolder");
            Files.createDirectory(subDir);
            for (int i = 0; i < subFiles; i++) {
                Path file = subDir.resolve("sub_" + i + ".pdf");
                Files.writeString(file, "Sub content " + i);
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files including subdirectories should be processed
            int totalFiles = rootFiles + subFiles;
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files (including %d in subdirectories) should be processed", 
                        totalFiles, subFiles)
                    .isEqualTo(totalFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder is empty Then no files are processed")
    void emptyFolderProcessedCorrectly() throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: an empty folder
        Path tempDir = Files.createTempDirectory("test-empty-");
        try {
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: no files should be processed
            assertThat(result.documentsProcessed())
                    .as("Empty folder should result in 0 processed files")
                    .isEqualTo(0);
            
            assertThat(result.documentsSkipped())
                    .as("Empty folder should result in 0 skipped files")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When files have various extensions Then all matching configured extensions are processed")
    void allConfiguredExtensionsAreProcessed(
            @ForAll @IntRange(min = 1, max = 3) int pdfCount,
            @ForAll @IntRange(min = 1, max = 3) int jpgCount,
            @ForAll @IntRange(min = 1, max = 3) int jpegCount,
            @ForAll @IntRange(min = 1, max = 3) int pngCount,
            @ForAll @IntRange(min = 1, max = 3) int tiffCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with all supported extensions
        Path tempDir = Files.createTempDirectory("test-all-extensions-");
        try {
            int totalFiles = 0;
            
            // Create files with each supported extension
            for (int i = 0; i < pdfCount; i++) {
                Files.writeString(tempDir.resolve("doc_" + i + ".pdf"), "PDF " + i);
                totalFiles++;
            }
            for (int i = 0; i < jpgCount; i++) {
                Files.writeString(tempDir.resolve("img_" + i + ".jpg"), "JPG " + i);
                totalFiles++;
            }
            for (int i = 0; i < jpegCount; i++) {
                Files.writeString(tempDir.resolve("img_" + i + ".jpeg"), "JPEG " + i);
                totalFiles++;
            }
            for (int i = 0; i < pngCount; i++) {
                Files.writeString(tempDir.resolve("img_" + i + ".png"), "PNG " + i);
                totalFiles++;
            }
            for (int i = 0; i < tiffCount; i++) {
                Files.writeString(tempDir.resolve("img_" + i + ".tiff"), "TIFF " + i);
                totalFiles++;
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files with supported extensions should be processed
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files with supported extensions should be processed", totalFiles)
                    .isEqualTo(totalFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When files have mixed case extensions Then all are processed")
    void mixedCaseExtensionsAreProcessed(
            @ForAll @IntRange(min = 1, max = 10) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with files having mixed case extensions
        Path tempDir = Files.createTempDirectory("test-mixed-case-");
        try {
            String[] extensions = {".pdf", ".PDF", ".Pdf", ".jpg", ".JPG", ".Jpg", ".png", ".PNG"};
            
            for (int i = 0; i < fileCount; i++) {
                String ext = extensions[i % extensions.length];
                Path file = tempDir.resolve("document_" + i + ext);
                Files.writeString(file, "Content " + i);
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files should be processed regardless of case
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files should be processed regardless of extension case", fileCount)
                    .isEqualTo(fileCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When folder contains hidden files Then hidden files are processed if they match extensions")
    void hiddenFilesAreProcessed(
            @ForAll @IntRange(min = 1, max = 5) int normalFiles,
            @ForAll @IntRange(min = 1, max = 5) int hiddenFiles
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with normal and hidden files
        Path tempDir = Files.createTempDirectory("test-hidden-");
        try {
            // Create normal files
            for (int i = 0; i < normalFiles; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Normal content " + i);
            }
            
            // Create hidden files (starting with .)
            for (int i = 0; i < hiddenFiles; i++) {
                Path file = tempDir.resolve(".hidden_" + i + ".pdf");
                Files.writeString(file, "Hidden content " + i);
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files (including hidden) should be processed
            int totalFiles = normalFiles + hiddenFiles;
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files (including hidden) should be processed", totalFiles)
                    .isEqualTo(totalFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When folder contains files with special characters Then all are processed")
    void filesWithSpecialCharactersAreProcessed(
            @ForAll @IntRange(min = 1, max = 10) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with files having special characters in names
        Path tempDir = Files.createTempDirectory("test-special-chars-");
        try {
            String[] specialNames = {
                "document-with-dash.pdf",
                "document_with_underscore.pdf",
                "document with spaces.pdf",
                "document(with)parens.pdf",
                "document[with]brackets.pdf",
                "document&with&ampersand.pdf",
                "document@with@at.pdf",
                "document#with#hash.pdf",
                "document$with$dollar.pdf",
                "document%with%percent.pdf"
            };
            
            int actualCount = Math.min(fileCount, specialNames.length);
            for (int i = 0; i < actualCount; i++) {
                Path file = tempDir.resolve(specialNames[i]);
                Files.writeString(file, "Content " + i);
            }
            
            DocumentProcessor processor = createProcessor(tempDir);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files with special characters should be processed
            int totalProcessed = result.documentsProcessed() + result.documentsSkipped();
            assertThat(totalProcessed)
                    .as("All %d files with special characters should be processed", actualCount)
                    .isEqualTo(actualCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When processing multiple times Then same files are discovered each time")
    void consistentFileDiscoveryAcrossRuns(
            @ForAll @IntRange(min = 1, max = 10) int fileCount,
            @ForAll @IntRange(min = 2, max = 5) int runCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 19: Complete Folder Processing
        
        // Given: a folder with files
        Path tempDir = Files.createTempDirectory("test-consistent-");
        try {
            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Content " + i);
            }
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing multiple times
            List<Integer> totalCounts = new ArrayList<>();
            for (int run = 0; run < runCount; run++) {
                ProcessingResult result = processor.processDocuments(tempDir).join();
                int total = result.documentsProcessed() + result.documentsSkipped();
                totalCounts.add(total);
            }
            
            // Then: same number of files should be discovered each time
            assertThat(totalCounts)
                    .as("File discovery should be consistent across %d runs", runCount)
                    .allMatch(count -> count == fileCount);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a DocumentProcessor with mocked dependencies.
     */
    private DocumentProcessor createProcessor(Path inputFolder) {
        return createProcessorWithStoredHashes(inputFolder, new ConcurrentHashMap<>());
    }

    /**
     * Create a DocumentProcessor with mocked dependencies and a simulated hash store.
     */
    private DocumentProcessor createProcessorWithStoredHashes(
            Path inputFolder,
            Map<Path, String> storedHashes
    ) {
        RedisClient redisClient = mock(RedisClient.class);
        OllamaClient ollamaClient = mock(OllamaClient.class);
        VectorStoreClient vectorStoreClient = mock(VectorStoreClient.class);
        ChunkingService chunkingService = mock(ChunkingService.class);
        
        // Mock Redis to return stored hashes and update them
        when(redisClient.getFileHash(any())).thenAnswer(invocation -> {
            Path file = invocation.getArgument(0);
            String hash = storedHashes.get(file);
            return CompletableFuture.completedFuture(Optional.ofNullable(hash));
        });
        
        when(redisClient.storeFileHash(any(), any())).thenAnswer(invocation -> {
            Path file = invocation.getArgument(0);
            String hash = invocation.getArgument(1);
            storedHashes.put(file, hash);
            return CompletableFuture.completedFuture(null);
        });
        
        // Mock Ollama to return valid text
        when(ollamaClient.analyzeImage(any(), any(), any())).thenReturn(
                CompletableFuture.completedFuture("Extracted text from image")
        );
        when(ollamaClient.generateEmbedding(any(), any())).thenReturn(
                CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f))
        );
        
        // Mock VectorStore operations
        when(vectorStoreClient.deleteEmbeddingsByFilename(any())).thenReturn(
                CompletableFuture.completedFuture(null)
        );
        when(vectorStoreClient.storeEmbeddings(any())).thenReturn(
                CompletableFuture.completedFuture(null)
        );
        
        // Mock ChunkingService to return empty list
        when(chunkingService.chunkText(any(), any(), anyInt(), anyInt())).thenReturn(
                List.of()
        );
        
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
        
        // Spy on the processor to mock extraction methods
        DocumentProcessor processor = spy(realProcessor);
        
        // Mock PDF extraction to return the file content as text
        when(processor.extractTextFromPdf(any())).thenAnswer(invocation -> {
            Path file = invocation.getArgument(0);
            try {
                String content = Files.readString(file);
                return CompletableFuture.completedFuture(Optional.of(content));
            } catch (IOException e) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
        
        // Mock image extraction to return the file content as text
        when(processor.extractTextFromImage(any())).thenAnswer(invocation -> {
            Path file = invocation.getArgument(0);
            try {
                String content = Files.readString(file);
                return CompletableFuture.completedFuture(Optional.of(content));
            } catch (IOException e) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        });
        
        return processor;
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
}

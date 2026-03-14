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
 * Property-based tests for modified file detection.
 * **Validates: Requirements 8.4**
 * 
 * Property 18: Modified File Detection
 * 
 * This property verifies that if a file is modified after being processed,
 * the next processing job execution should reprocess that file.
 */
class ModifiedFileDetectionPropertyTest {

    @Property(tries = 100)
    @Label("When file is modified after processing Then next execution reprocesses it")
    void modifiedFileIsReprocessedOnNextExecution(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String originalContent,
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String modifiedContent
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        Assume.that(!originalContent.equals(modifiedContent));
        
        // Given: a file with original content
        Path tempDir = Files.createTempDirectory("test-modified-detection-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, originalContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing documents for the first time
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed
            assertThat(firstRun.documentsProcessed())
                    .as("First run should process the new file")
                    .isEqualTo(1);
            
            assertThat(firstRun.documentsSkipped())
                    .as("First run should not skip any files")
                    .isEqualTo(0);
            
            // When: file is modified
            Files.writeString(file, modifiedContent);
            
            // And: processing documents again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: modified file should be reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Second run should reprocess the modified file")
                    .isEqualTo(1);
            
            assertThat(secondRun.documentsSkipped())
                    .as("Second run should not skip the modified file")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When multiple files are modified Then all modified files are reprocessed")
    void multipleModifiedFilesAreReprocessed(
            @ForAll @IntRange(min = 2, max = 10) int totalFiles,
            @ForAll @IntRange(min = 1, max = 5) int modifiedCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        Assume.that(modifiedCount <= totalFiles);
        
        // Given: multiple files processed initially
        Path tempDir = Files.createTempDirectory("test-multi-modified-");
        try {
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // Create and process files initially
            for (int i = 0; i < totalFiles; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Original content " + i);
            }
            
            // When: processing documents for the first time
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: all files should be processed
            assertThat(firstRun.documentsProcessed())
                    .as("First run should process all %d files", totalFiles)
                    .isEqualTo(totalFiles);
            
            // When: some files are modified
            for (int i = 0; i < modifiedCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Modified content " + i);
            }
            
            // And: processing documents again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: only modified files should be reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Second run should reprocess %d modified files", modifiedCount)
                    .isEqualTo(modifiedCount);
            
            int expectedSkipped = totalFiles - modifiedCount;
            assertThat(secondRun.documentsSkipped())
                    .as("Second run should skip %d unchanged files", expectedSkipped)
                    .isEqualTo(expectedSkipped);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file is modified multiple times Then each modification triggers reprocessing")
    void multipleModificationsTriggerReprocessing(
            @ForAll @IntRange(min = 2, max = 5) int modificationCount,
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String baseContent
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        // Given: a file that will be modified multiple times
        Path tempDir = Files.createTempDirectory("test-multi-mods-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, baseContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing and modifying multiple times
            for (int i = 0; i < modificationCount; i++) {
                // Process
                ProcessingResult result = processor.processDocuments(tempDir).join();
                
                // Then: file should be processed (first time) or reprocessed (subsequent times)
                assertThat(result.documentsProcessed())
                        .as("Iteration %d should process the file", i + 1)
                        .isEqualTo(1);
                
                if (i > 0) {
                    assertThat(result.documentsSkipped())
                            .as("Iteration %d should not skip the modified file", i + 1)
                            .isEqualTo(0);
                }
                
                // Modify for next iteration (if not last)
                if (i < modificationCount - 1) {
                    Files.writeString(file, baseContent + " - modification " + (i + 1));
                }
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file content changes by single byte Then file is detected as modified")
    void singleByteChangeDetected(
            @ForAll @AlphaChars @StringLength(min = 20, max = 100) String content,
            @ForAll @IntRange(min = 0, max = 25) int changePosition
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        Assume.that(changePosition < content.length());
        Assume.that(content.charAt(changePosition) != 'X');
        
        // Given: a file with specific content
        Path tempDir = Files.createTempDirectory("test-byte-change-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, content);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing initially
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed
            assertThat(firstRun.documentsProcessed()).isEqualTo(1);
            
            // When: changing a single character
            char[] chars = content.toCharArray();
            chars[changePosition] = 'X';
            String modifiedContent = new String(chars);
            Files.writeString(file, modifiedContent);
            
            // And: processing again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be detected as modified and reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Single byte change should trigger reprocessing")
                    .isEqualTo(1);
            
            assertThat(secondRun.documentsSkipped())
                    .as("Modified file should not be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file is appended Then file is detected as modified")
    void appendedContentDetected(
            @ForAll @AlphaChars @StringLength(min = 10, max = 50) String originalContent,
            @ForAll @AlphaChars @StringLength(min = 5, max = 30) String appendedContent
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        // Given: a file with original content
        Path tempDir = Files.createTempDirectory("test-append-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, originalContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing initially
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed
            assertThat(firstRun.documentsProcessed()).isEqualTo(1);
            
            // When: appending content to file
            Files.writeString(file, originalContent + appendedContent);
            
            // And: processing again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be detected as modified and reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Appended content should trigger reprocessing")
                    .isEqualTo(1);
            
            assertThat(secondRun.documentsSkipped())
                    .as("Modified file should not be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file is truncated Then file is detected as modified")
    void truncatedContentDetected(
            @ForAll @AlphaChars @StringLength(min = 20, max = 100) String originalContent,
            @ForAll @IntRange(min = 5, max = 15) int truncateLength
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        Assume.that(truncateLength < originalContent.length());
        
        // Given: a file with original content
        Path tempDir = Files.createTempDirectory("test-truncate-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, originalContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing initially
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed
            assertThat(firstRun.documentsProcessed()).isEqualTo(1);
            
            // When: truncating file content
            String truncatedContent = originalContent.substring(0, truncateLength);
            Files.writeString(file, truncatedContent);
            
            // And: processing again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be detected as modified and reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Truncated content should trigger reprocessing")
                    .isEqualTo(1);
            
            assertThat(secondRun.documentsSkipped())
                    .as("Modified file should not be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When file is replaced with different content Then file is detected as modified")
    void replacedFileDetected(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String originalContent,
            @ForAll @NumericChars @StringLength(min = 10, max = 100) String replacementContent
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        // Given: a file with original content
        Path tempDir = Files.createTempDirectory("test-replace-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, originalContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing initially
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed
            assertThat(firstRun.documentsProcessed()).isEqualTo(1);
            
            // When: replacing file with completely different content
            Files.writeString(file, replacementContent);
            
            // And: processing again
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: file should be detected as modified and reprocessed
            assertThat(secondRun.documentsProcessed())
                    .as("Replaced content should trigger reprocessing")
                    .isEqualTo(1);
            
            assertThat(secondRun.documentsSkipped())
                    .as("Modified file should not be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file is unchanged Then file is not reprocessed")
    void unchangedFileNotReprocessed(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String content,
            @ForAll @IntRange(min = 2, max = 5) int processingRuns
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 18: Modified File Detection
        
        // Given: a file with content that doesn't change
        Path tempDir = Files.createTempDirectory("test-unchanged-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, content);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing multiple times without modifications
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: first run should process the file
            assertThat(firstRun.documentsProcessed()).isEqualTo(1);
            
            // When: processing again without modifications
            for (int i = 1; i < processingRuns; i++) {
                ProcessingResult run = processor.processDocuments(tempDir).join();
                
                // Then: file should be skipped (not reprocessed)
                assertThat(run.documentsSkipped())
                        .as("Run %d should skip unchanged file", i + 1)
                        .isEqualTo(1);
                
                assertThat(run.documentsProcessed())
                        .as("Run %d should not reprocess unchanged file", i + 1)
                        .isEqualTo(0);
            }
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

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
        
        // Mock ChunkingService to return empty list (we're not testing chunking here)
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
                )
        );
        
        // Spy on the processor to mock PDF extraction
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

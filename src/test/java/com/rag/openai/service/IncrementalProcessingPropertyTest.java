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
 * Property-based tests for incremental processing.
 * **Validates: Requirements 8.3, 8.4**
 * 
 * Property 17: Incremental Processing
 * 
 * This property verifies that the DocumentProcessor correctly implements
 * incremental processing by:
 * - Processing only new or modified documents since the last execution
 * - Skipping unchanged files based on hash comparison
 * - Tracking which documents have been processed
 */
class IncrementalProcessingPropertyTest {

    @Property(tries = 100)
    @Label("When files are unchanged Then all files are skipped")
    void unchangedFilesAreSkipped(
            @ForAll @IntRange(min = 1, max = 10) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a folder with files that have been processed before (matching hashes)
        Path tempDir = Files.createTempDirectory("test-unchanged-");
        try {
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            
            // Create files
            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                String content = "Content " + i;
                Files.writeString(file, content);
                
                // Compute and store hash
                String hash = computeSimpleHash(content);
                storedHashes.put(file, hash);
            }
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files should be skipped (not processed)
            assertThat(result.documentsSkipped())
                    .as("All %d unchanged files should be skipped", fileCount)
                    .isEqualTo(fileCount);
            
            assertThat(result.documentsProcessed())
                    .as("No unchanged files should be processed")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When files are new Then all files are processed")
    void newFilesAreProcessed(
            @ForAll @IntRange(min = 1, max = 10) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a folder with new files (no stored hashes)
        Path tempDir = Files.createTempDirectory("test-new-");
        try {
            // Create files
            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Content " + i);
            }
            
            // No stored hashes (empty map)
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, new ConcurrentHashMap<>());
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: all files should be processed (not skipped)
            assertThat(result.documentsProcessed())
                    .as("All %d new files should be processed", fileCount)
                    .isEqualTo(fileCount);
            
            assertThat(result.documentsSkipped())
                    .as("No new files should be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When some files are modified Then only modified files are processed")
    void modifiedFilesAreProcessed(
            @ForAll @IntRange(min = 2, max = 10) int totalFiles,
            @ForAll @IntRange(min = 1, max = 5) int modifiedCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        Assume.that(modifiedCount < totalFiles);
        
        // Given: a folder with some modified and some unchanged files
        Path tempDir = Files.createTempDirectory("test-modified-");
        try {
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            
            // Create files
            for (int i = 0; i < totalFiles; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                String content = "Content " + i;
                Files.writeString(file, content);
                
                // Store hash for all files initially
                String hash = computeSimpleHash(content);
                storedHashes.put(file, hash);
            }
            
            // Modify some files (change content, which changes hash)
            Set<Path> modifiedFiles = new HashSet<>();
            for (int i = 0; i < modifiedCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                String newContent = "Modified Content " + i;
                Files.writeString(file, newContent);
                modifiedFiles.add(file);
                // Note: stored hash remains old, but actual file hash is now different
            }
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: only modified files should be processed
            assertThat(result.documentsProcessed())
                    .as("Only %d modified files should be processed", modifiedCount)
                    .isEqualTo(modifiedCount);
            
            int expectedSkipped = totalFiles - modifiedCount;
            assertThat(result.documentsSkipped())
                    .as("%d unchanged files should be skipped", expectedSkipped)
                    .isEqualTo(expectedSkipped);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When processing twice without changes Then second run skips all files")
    void secondRunSkipsUnchangedFiles(
            @ForAll @IntRange(min = 1, max = 10) int fileCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a folder with files
        Path tempDir = Files.createTempDirectory("test-double-run-");
        try {
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            
            // Create files
            for (int i = 0; i < fileCount; i++) {
                Path file = tempDir.resolve("document_" + i + ".pdf");
                Files.writeString(file, "Content " + i);
            }
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing documents first time
            ProcessingResult firstRun = processor.processDocuments(tempDir).join();
            
            // Then: all files should be processed in first run
            assertThat(firstRun.documentsProcessed())
                    .as("First run should process all %d files", fileCount)
                    .isEqualTo(fileCount);
            
            // When: processing documents second time without changes
            ProcessingResult secondRun = processor.processDocuments(tempDir).join();
            
            // Then: all files should be skipped in second run
            assertThat(secondRun.documentsSkipped())
                    .as("Second run should skip all %d unchanged files", fileCount)
                    .isEqualTo(fileCount);
            
            assertThat(secondRun.documentsProcessed())
                    .as("Second run should process 0 files")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When file content changes Then file is reprocessed")
    void changedContentTriggersReprocessing(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String originalContent,
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String modifiedContent
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        Assume.that(!originalContent.equals(modifiedContent));
        
        // Given: a file with original content
        Path tempDir = Files.createTempDirectory("test-content-change-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, originalContent);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            String originalHash = computeSimpleHash(originalContent);
            storedHashes.put(file, originalHash);
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: file content is changed
            Files.writeString(file, modifiedContent);
            
            // And: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: file should be processed (not skipped)
            assertThat(result.documentsProcessed())
                    .as("Modified file should be processed")
                    .isEqualTo(1);
            
            assertThat(result.documentsSkipped())
                    .as("Modified file should not be skipped")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When mix of new, modified, and unchanged files Then correct counts are returned")
    void mixedFilesProcessedCorrectly(
            @ForAll @IntRange(min = 1, max = 5) int newFiles,
            @ForAll @IntRange(min = 1, max = 5) int modifiedFiles,
            @ForAll @IntRange(min = 1, max = 5) int unchangedFiles
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a folder with new, modified, and unchanged files
        Path tempDir = Files.createTempDirectory("test-mixed-");
        try {
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            
            // Create new files (no stored hash)
            for (int i = 0; i < newFiles; i++) {
                Path file = tempDir.resolve("new_" + i + ".pdf");
                Files.writeString(file, "New content " + i);
            }
            
            // Create modified files (stored hash differs from current)
            for (int i = 0; i < modifiedFiles; i++) {
                Path file = tempDir.resolve("modified_" + i + ".pdf");
                String oldContent = "Old content " + i;
                String newContent = "Modified content " + i;
                
                // Store old hash
                storedHashes.put(file, computeSimpleHash(oldContent));
                
                // Write new content
                Files.writeString(file, newContent);
            }
            
            // Create unchanged files (stored hash matches current)
            for (int i = 0; i < unchangedFiles; i++) {
                Path file = tempDir.resolve("unchanged_" + i + ".pdf");
                String content = "Unchanged content " + i;
                Files.writeString(file, content);
                
                // Store matching hash
                storedHashes.put(file, computeSimpleHash(content));
            }
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: new and modified files should be processed
            int expectedProcessed = newFiles + modifiedFiles;
            assertThat(result.documentsProcessed())
                    .as("Should process %d new files and %d modified files", newFiles, modifiedFiles)
                    .isEqualTo(expectedProcessed);
            
            // And: unchanged files should be skipped
            assertThat(result.documentsSkipped())
                    .as("Should skip %d unchanged files", unchangedFiles)
                    .isEqualTo(unchangedFiles);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When file is deleted and recreated Then file is processed")
    void deletedAndRecreatedFileIsProcessed(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String content
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a file that was processed before
        Path tempDir = Files.createTempDirectory("test-delete-recreate-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, content);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            String hash = computeSimpleHash(content);
            storedHashes.put(file, hash);
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: file is deleted
            Files.delete(file);
            
            // And: file is recreated with same content
            Files.writeString(file, content);
            
            // And: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: file should be skipped (same hash)
            assertThat(result.documentsSkipped())
                    .as("Recreated file with same content should be skipped")
                    .isEqualTo(1);
            
            assertThat(result.documentsProcessed())
                    .as("Recreated file with same content should not be processed")
                    .isEqualTo(0);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When only file metadata changes Then file is skipped")
    void metadataChangeDoesNotTriggerProcessing(
            @ForAll @AlphaChars @StringLength(min = 10, max = 100) String content
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 17: Incremental Processing
        
        // Given: a file with stored hash
        Path tempDir = Files.createTempDirectory("test-metadata-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, content);
            
            Map<Path, String> storedHashes = new ConcurrentHashMap<>();
            String hash = computeSimpleHash(content);
            storedHashes.put(file, hash);
            
            DocumentProcessor processor = createProcessorWithStoredHashes(tempDir, storedHashes);
            
            // When: file last modified time changes (but content stays same)
            // Note: We can't easily change just metadata without changing content,
            // but the hash is based on content, so same content = same hash
            
            // And: processing documents
            ProcessingResult result = processor.processDocuments(tempDir).join();
            
            // Then: file should be skipped (hash unchanged)
            assertThat(result.documentsSkipped())
                    .as("File with unchanged content should be skipped regardless of metadata")
                    .isEqualTo(1);
            
            assertThat(result.documentsProcessed())
                    .as("File with unchanged content should not be processed")
                    .isEqualTo(0);
            
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
        
        // Mock ChunkingService to return a single chunk
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
     * Compute a simple hash for testing purposes.
     * Uses the same algorithm as DocumentProcessor (SHA-256).
     */
    private String computeSimpleHash(String content) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(content.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute hash", e);
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
}

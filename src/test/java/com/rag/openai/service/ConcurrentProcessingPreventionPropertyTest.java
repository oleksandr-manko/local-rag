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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for concurrent processing prevention.
 * **Validates: Requirements 9.5**
 * 
 * Property 25: Concurrent Processing Prevention
 * 
 * This property verifies that when processing is already running,
 * subsequent calls to triggerProcessing() return immediately with
 * a conflict status without starting a new processing job.
 */
class ConcurrentProcessingPreventionPropertyTest {

    @Property(tries = 100)
    @Label("When processing is running Then concurrent trigger returns conflict")
    void concurrentTriggerReturnsConflict(
            @ForAll @IntRange(min = 2, max = 10) int concurrentCalls
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob with slow document processing
        Path tempDir = Files.createTempDirectory("test-concurrent-");
        try {
            // Create a test file
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, "Test content");
            
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJob processingJob = createSlowProcessingJob(tempDir, actualProcessingCount, 500);
            
            // When: triggering processing multiple times concurrently
            ExecutorService executor = Executors.newFixedThreadPool(concurrentCalls);
            List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>();
            
            for (int i = 0; i < concurrentCalls; i++) {
                CompletableFuture<ProcessingResult> future = CompletableFuture.supplyAsync(
                        processingJob::triggerProcessing,
                        executor
                ).thenCompose(f -> f);
                
                futures.add(future);
            }
            
            // Wait for all calls to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Then: only one processing should have actually executed
            assertThat(actualProcessingCount.get())
                    .as("Only one processing execution should occur despite %d concurrent calls", concurrentCalls)
                    .isEqualTo(1);
            
            // And: at least one call should have received a conflict result
            List<ProcessingResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            long conflictResults = results.stream()
                    .filter(r -> r.errors().contains("Processing already in progress"))
                    .count();
            
            assertThat(conflictResults)
                    .as("At least %d calls should receive conflict status", concurrentCalls - 1)
                    .isGreaterThanOrEqualTo(concurrentCalls - 1);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When processing completes Then next trigger starts new processing")
    void afterCompletionNextTriggerStarts(
            @ForAll @IntRange(min = 2, max = 5) int sequentialCalls
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob
        Path tempDir = Files.createTempDirectory("test-sequential-");
        try {
            // Create a test file
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, "Test content");
            
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJob processingJob = createSlowProcessingJob(tempDir, actualProcessingCount, 100);
            
            // When: triggering processing sequentially (waiting for each to complete)
            for (int i = 0; i < sequentialCalls; i++) {
                ProcessingResult result = processingJob.triggerProcessing().join();
                
                // Then: each call should successfully process (not conflict)
                assertThat(result.errors())
                        .as("Sequential call %d should not have conflict error", i + 1)
                        .doesNotContain("Processing already in progress");
            }
            
            // And: all calls should have executed
            assertThat(actualProcessingCount.get())
                    .as("All %d sequential calls should execute", sequentialCalls)
                    .isEqualTo(sequentialCalls);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When isProcessingInProgress is true Then triggerProcessing returns conflict")
    void processingInProgressReturnsConflict() throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob with slow processing
        Path tempDir = Files.createTempDirectory("test-in-progress-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, "Test content");
            
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJob processingJob = createSlowProcessingJob(tempDir, actualProcessingCount, 500);
            
            // When: starting processing asynchronously
            CompletableFuture<ProcessingResult> firstCall = processingJob.triggerProcessing();
            
            // Wait a bit to ensure processing has started
            Thread.sleep(50);
            
            // Then: isProcessingInProgress should return true
            assertThat(processingJob.isProcessingInProgress())
                    .as("Processing should be in progress")
                    .isTrue();
            
            // When: triggering again while processing is in progress
            ProcessingResult secondResult = processingJob.triggerProcessing().join();
            
            // Then: second call should return conflict
            assertThat(secondResult.errors())
                    .as("Second call should receive conflict error")
                    .contains("Processing already in progress");
            
            // Wait for first call to complete
            firstCall.join();
            
            // Then: after completion, isProcessingInProgress should return false
            assertThat(processingJob.isProcessingInProgress())
                    .as("Processing should not be in progress after completion")
                    .isFalse();
            
            // And: only one processing should have executed
            assertThat(actualProcessingCount.get())
                    .as("Only one processing should have executed")
                    .isEqualTo(1);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When multiple threads trigger simultaneously Then AtomicBoolean prevents race conditions")
    void atomicBooleanPreventsRaceConditions(
            @ForAll @IntRange(min = 5, max = 20) int threadCount
    ) throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob with slow processing
        Path tempDir = Files.createTempDirectory("test-race-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, "Test content");
            
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJob processingJob = createSlowProcessingJob(tempDir, actualProcessingCount, 300);
            
            // When: many threads trigger processing at exactly the same time
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            List<CompletableFuture<ProcessingResult>> futures = new ArrayList<>();
            
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                CompletableFuture<ProcessingResult> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Wait for all threads to be ready
                        startLatch.await();
                        
                        // Trigger processing
                        return processingJob.triggerProcessing().join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } finally {
                        doneLatch.countDown();
                    }
                }, executor);
                
                futures.add(future);
            }
            
            // Release all threads at once
            startLatch.countDown();
            
            // Wait for all to complete
            doneLatch.await(10, TimeUnit.SECONDS);
            
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            
            // Then: exactly one processing should have executed (no race condition)
            assertThat(actualProcessingCount.get())
                    .as("Exactly one processing should execute despite %d simultaneous calls", threadCount)
                    .isEqualTo(1);
            
            // And: all other calls should have received conflict
            List<ProcessingResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .toList();
            
            long conflictCount = results.stream()
                    .filter(r -> r.errors().contains("Processing already in progress"))
                    .count();
            
            assertThat(conflictCount)
                    .as("Exactly %d calls should receive conflict", threadCount - 1)
                    .isEqualTo(threadCount - 1);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 100)
    @Label("When processing fails Then next trigger can start new processing")
    void afterFailureNextTriggerStarts() throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob that will fail
        Path tempDir = Files.createTempDirectory("test-failure-");
        try {
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJob processingJob = createFailingProcessingJob(tempDir, actualProcessingCount);
            
            // When: triggering processing that fails
            ProcessingResult firstResult = processingJob.triggerProcessing().join();
            
            // Then: processing should have failed
            assertThat(firstResult.errors())
                    .as("First call should have error")
                    .isNotEmpty();
            
            // And: isProcessingInProgress should be false after failure
            assertThat(processingJob.isProcessingInProgress())
                    .as("Processing should not be in progress after failure")
                    .isFalse();
            
            // When: triggering again after failure
            ProcessingResult secondResult = processingJob.triggerProcessing().join();
            
            // Then: second call should also execute (not conflict)
            assertThat(actualProcessingCount.get())
                    .as("Both calls should execute despite first failure")
                    .isEqualTo(2);
            
        } finally {
            deleteDirectory(tempDir);
        }
    }

    @Property(tries = 50)
    @Label("When scheduled and on-demand processing overlap Then only one executes")
    void scheduledAndOnDemandDoNotOverlap() throws IOException {
        // Feature: rag-openai-api-ollama, Property 25: Concurrent Processing Prevention
        
        // Given: a ProcessingJob with slow processing
        Path tempDir = Files.createTempDirectory("test-overlap-");
        try {
            Path file = tempDir.resolve("document.pdf");
            Files.writeString(file, "Test content");
            
            AtomicInteger actualProcessingCount = new AtomicInteger(0);
            ProcessingJobImpl processingJob = (ProcessingJobImpl) createSlowProcessingJob(tempDir, actualProcessingCount, 500);
            
            // When: starting on-demand processing
            CompletableFuture<ProcessingResult> onDemand = processingJob.triggerProcessing();
            
            // Wait a bit to ensure processing has started
            Thread.sleep(50);
            
            // And: scheduled processing tries to execute
            CompletableFuture<Void> scheduled = CompletableFuture.runAsync(
                    processingJob::executeScheduledProcessing
            );
            
            // Wait for both to complete
            onDemand.join();
            scheduled.join();
            
            // Then: only one processing should have executed
            assertThat(actualProcessingCount.get())
                    .as("Only one processing should execute when scheduled and on-demand overlap")
                    .isEqualTo(1);
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        } finally {
            deleteDirectory(tempDir);
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a ProcessingJob with slow processing to simulate concurrent scenarios.
     */
    private ProcessingJob createSlowProcessingJob(
            Path inputFolder,
            AtomicInteger processingCount,
            long delayMs
    ) {
        DocumentProcessor documentProcessor = createSlowDocumentProcessor(processingCount, delayMs);
        DocumentsConfig documentsConfig = new DocumentsConfig(
                inputFolder,
                List.of("pdf", "jpg", "jpeg", "png", "tiff")
        );
        ProcessingConfig processingConfig = new ProcessingConfig(
                "0 */15 * * * *",
                512,
                50,
                100,
                5,
                java.time.Duration.ofSeconds(60)
        );
        
        return new ProcessingJobImpl(documentProcessor, documentsConfig, processingConfig);
    }

    /**
     * Create a ProcessingJob that fails during processing.
     */
    private ProcessingJob createFailingProcessingJob(
            Path inputFolder,
            AtomicInteger processingCount
    ) {
        DocumentProcessor documentProcessor = mock(DocumentProcessor.class);
        
        when(documentProcessor.processDocuments(any())).thenAnswer(invocation -> {
            processingCount.incrementAndGet();
            return CompletableFuture.failedFuture(new RuntimeException("Simulated failure"));
        });
        
        DocumentsConfig documentsConfig = new DocumentsConfig(
                inputFolder,
                List.of("pdf", "jpg", "jpeg", "png", "tiff")
        );
        ProcessingConfig processingConfig = new ProcessingConfig(
                "0 */15 * * * *",
                512,
                50,
                100,
                5,
                java.time.Duration.ofSeconds(60)
        );
        
        return new ProcessingJobImpl(documentProcessor, documentsConfig, processingConfig);
    }

    /**
     * Create a DocumentProcessor that simulates slow processing.
     */
    private DocumentProcessor createSlowDocumentProcessor(
            AtomicInteger processingCount,
            long delayMs
    ) {
        DocumentProcessor documentProcessor = mock(DocumentProcessor.class);
        
        when(documentProcessor.processDocuments(any())).thenAnswer(invocation -> {
            processingCount.incrementAndGet();
            
            return CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                return new ProcessingResult(1, 0, 1, 1, delayMs, List.of());
            });
        });
        
        return documentProcessor;
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

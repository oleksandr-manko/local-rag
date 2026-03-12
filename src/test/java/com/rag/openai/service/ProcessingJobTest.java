package com.rag.openai.service;

import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.ProcessingConfig;
import com.rag.openai.domain.model.ProcessingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProcessingJobTest {
    
    private ProcessingJob processingJob;
    private DocumentProcessor documentProcessor;
    private DocumentsConfig documentsConfig;
    private ProcessingConfig processingConfig;
    
    @TempDir
    Path tempDir;
    
    @BeforeEach
    void setUp() {
        // Given: Mock dependencies
        documentProcessor = mock(DocumentProcessor.class);
        
        documentsConfig = new DocumentsConfig(
            tempDir,
            List.of("pdf", "jpg", "jpeg", "png", "tiff")
        );
        
        processingConfig = new ProcessingConfig(
            "0 */15 * * * *",
            512,
            50,
            100,
            5,
            Duration.ofSeconds(60)
        );
        
        processingJob = new ProcessingJobImpl(
            documentProcessor,
            documentsConfig,
            processingConfig
        );
    }
    
    @Test
    @DisplayName("When triggering processing on-demand Then returns processing result")
    void testTriggerProcessing_Success() {
        // Given: Document processor returns successful result
        ProcessingResult expectedResult = new ProcessingResult(
            5,
            2,
            100,
            100,
            1000L,
            List.of()
        );
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Triggering processing
        ProcessingResult result = processingJob.triggerProcessing().join();
        
        // Then: Should return the processing result
        assertNotNull(result);
        assertEquals(5, result.documentsProcessed());
        assertEquals(2, result.documentsSkipped());
        assertEquals(100, result.chunksCreated());
        assertEquals(100, result.embeddingsStored());
        verify(documentProcessor).processDocuments(tempDir);
    }
    
    @Test
    @DisplayName("When triggering processing while already running Then prevents concurrent execution")
    void testTriggerProcessing_ConcurrentExecutionPrevention() throws Exception {
        // Given: Document processor with slow processing
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch processingCanFinish = new CountDownLatch(1);
        
        when(documentProcessor.processDocuments(any()))
            .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                processingStarted.countDown();
                try {
                    processingCanFinish.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ProcessingResult(1, 0, 10, 10, 100L, List.of());
            }));
        
        // When: Starting first processing
        CompletableFuture<ProcessingResult> firstProcessing = processingJob.triggerProcessing();
        
        // Wait for first processing to start
        assertTrue(processingStarted.await(2, TimeUnit.SECONDS));
        assertTrue(processingJob.isProcessingInProgress());
        
        // When: Attempting second processing while first is running
        CompletableFuture<ProcessingResult> secondProcessing = processingJob.triggerProcessing();
        
        // Then: Second processing should complete immediately with empty result
        ProcessingResult secondResult = secondProcessing.join();
        assertEquals(0, secondResult.documentsProcessed());
        assertEquals(0, secondResult.documentsSkipped());
        
        // Allow first processing to complete
        processingCanFinish.countDown();
        ProcessingResult firstResult = firstProcessing.join();
        assertEquals(1, firstResult.documentsProcessed());
        
        // Then: Processing should no longer be in progress
        assertFalse(processingJob.isProcessingInProgress());
    }
    
    @Test
    @DisplayName("When scheduled execution runs Then processes documents")
    void testExecuteScheduledProcessing_Success() {
        // Given: Document processor returns successful result
        ProcessingResult expectedResult = new ProcessingResult(
            3,
            1,
            50,
            50,
            500L,
            List.of()
        );
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Executing scheduled processing
        processingJob.executeScheduledProcessing();
        
        // Then: Should process documents
        verify(documentProcessor, timeout(2000)).processDocuments(tempDir);
    }
    
    @Test
    @DisplayName("When processing encounters error Then logs error and continues")
    void testTriggerProcessing_ErrorRecovery() {
        // Given: Document processor throws an error
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Processing error")
            ));
        
        // When: Triggering processing
        CompletableFuture<ProcessingResult> future = processingJob.triggerProcessing();
        
        // Then: Should handle error gracefully and return result with error
        ProcessingResult result = future.join();
        assertNotNull(result);
        assertFalse(result.errors().isEmpty());
        assertTrue(result.errors().get(0).contains("Processing error"));
    }
    
    @Test
    @DisplayName("When scheduled execution encounters error Then continues on next execution")
    void testExecuteScheduledProcessing_ErrorRecovery() {
        // Given: Document processor throws an error on first call, succeeds on second
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("First execution error")
            ))
            .thenReturn(CompletableFuture.completedFuture(
                new ProcessingResult(2, 0, 20, 20, 200L, List.of())
            ));
        
        // When: Executing scheduled processing twice
        processingJob.executeScheduledProcessing();
        
        // Wait for first execution to complete
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        processingJob.executeScheduledProcessing();
        
        // Then: Should execute both times despite first error
        verify(documentProcessor, timeout(2000).times(2)).processDocuments(tempDir);
    }
    
    @Test
    @DisplayName("When checking processing status Then returns correct state")
    void testIsProcessingInProgress_StateTracking() throws Exception {
        // Given: Document processor with controlled processing
        CountDownLatch processingStarted = new CountDownLatch(1);
        CountDownLatch processingCanFinish = new CountDownLatch(1);
        
        when(documentProcessor.processDocuments(any()))
            .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                processingStarted.countDown();
                try {
                    processingCanFinish.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ProcessingResult(1, 0, 10, 10, 100L, List.of());
            }));
        
        // When: Initially no processing
        assertFalse(processingJob.isProcessingInProgress());
        
        // When: Starting processing
        CompletableFuture<ProcessingResult> processing = processingJob.triggerProcessing();
        
        // Wait for processing to start
        assertTrue(processingStarted.await(2, TimeUnit.SECONDS));
        
        // Then: Should report processing in progress
        assertTrue(processingJob.isProcessingInProgress());
        
        // When: Allowing processing to complete
        processingCanFinish.countDown();
        processing.join();
        
        // Then: Should report processing not in progress
        assertFalse(processingJob.isProcessingInProgress());
    }
    
    @Test
    @DisplayName("When multiple threads trigger processing Then only one executes")
    void testTriggerProcessing_ThreadSafety() throws Exception {
        // Given: Document processor with slow processing
        AtomicInteger executionCount = new AtomicInteger(0);
        CountDownLatch allThreadsStarted = new CountDownLatch(5);
        
        when(documentProcessor.processDocuments(any()))
            .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> {
                executionCount.incrementAndGet();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new ProcessingResult(1, 0, 10, 10, 100L, List.of());
            }));
        
        // When: Multiple threads trigger processing simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<CompletableFuture<ProcessingResult>> futures = new java.util.ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            CompletableFuture<ProcessingResult> future = CompletableFuture.supplyAsync(() -> {
                allThreadsStarted.countDown();
                try {
                    allThreadsStarted.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return processingJob.triggerProcessing().join();
            }, executor);
            futures.add(future);
        }
        
        // Wait for all futures to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        
        // Then: Document processor should be called only once
        assertEquals(1, executionCount.get());
        verify(documentProcessor, times(1)).processDocuments(any());
    }
    
    @Test
    @DisplayName("When processing completes successfully Then state is reset")
    void testTriggerProcessing_StateReset() {
        // Given: Document processor returns successful result
        ProcessingResult expectedResult = new ProcessingResult(
            2,
            1,
            30,
            30,
            300L,
            List.of()
        );
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Triggering processing first time
        ProcessingResult result1 = processingJob.triggerProcessing().join();
        
        // Then: Should complete successfully
        assertEquals(2, result1.documentsProcessed());
        assertFalse(processingJob.isProcessingInProgress());
        
        // When: Triggering processing second time
        ProcessingResult result2 = processingJob.triggerProcessing().join();
        
        // Then: Should execute again successfully
        assertEquals(2, result2.documentsProcessed());
        assertFalse(processingJob.isProcessingInProgress());
        verify(documentProcessor, times(2)).processDocuments(tempDir);
    }
    
    @Test
    @DisplayName("When processing with empty result Then returns valid result")
    void testTriggerProcessing_EmptyResult() {
        // Given: Document processor returns empty result (no documents found)
        ProcessingResult emptyResult = new ProcessingResult(
            0,
            0,
            0,
            0,
            50L,
            List.of()
        );
        when(documentProcessor.processDocuments(any()))
            .thenReturn(CompletableFuture.completedFuture(emptyResult));
        
        // When: Triggering processing
        ProcessingResult result = processingJob.triggerProcessing().join();
        
        // Then: Should return valid empty result
        assertNotNull(result);
        assertEquals(0, result.documentsProcessed());
        assertEquals(0, result.documentsSkipped());
        assertEquals(0, result.chunksCreated());
        assertEquals(0, result.embeddingsStored());
        assertTrue(result.errors().isEmpty());
    }
}

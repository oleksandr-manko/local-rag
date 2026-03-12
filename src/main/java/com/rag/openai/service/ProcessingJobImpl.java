package com.rag.openai.service;

import com.rag.openai.config.DocumentsConfig;
import com.rag.openai.config.ProcessingConfig;
import com.rag.openai.domain.model.ProcessingResult;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Implementation of ProcessingJob that coordinates scheduled and on-demand document processing.
 * Uses atomic state management to prevent concurrent execution.
 */
@Component
public class ProcessingJobImpl implements ProcessingJob {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingJobImpl.class);
    
    private final DocumentProcessor documentProcessor;
    private final DocumentsConfig documentsConfig;
    private final ProcessingConfig processingConfig;
    private final AtomicBoolean processingInProgress;
    private volatile CompletableFuture<ProcessingResult> currentProcessing;
    
    public ProcessingJobImpl(
        DocumentProcessor documentProcessor,
        DocumentsConfig documentsConfig,
        ProcessingConfig processingConfig
    ) {
        this.documentProcessor = documentProcessor;
        this.documentsConfig = documentsConfig;
        this.processingConfig = processingConfig;
        this.processingInProgress = new AtomicBoolean(false);
    }
    
    @Override
    @Scheduled(cron = "${processing.schedule}")
    public void executeScheduledProcessing() {
        logger.info("Scheduled processing triggered");
        
        if (!processingInProgress.compareAndSet(false, true)) {
            logger.warn("Processing already in progress, skipping scheduled execution");
            return;
        }
        
        try {
            processDocumentsInternal()
                .whenComplete((result, error) -> {
                    processingInProgress.set(false);
                    if (error != null) {
                        logger.error("Scheduled processing failed", error);
                    }
                })
                .join();
        } catch (Exception e) {
            processingInProgress.set(false);
            logger.error("Scheduled processing encountered an error", e);
        }
    }
    
    @Override
    public CompletableFuture<ProcessingResult> triggerProcessing() {
        logger.info("On-demand processing triggered");
        
        if (!processingInProgress.compareAndSet(false, true)) {
            logger.warn("Processing already in progress, returning empty result");
            return CompletableFuture.completedFuture(
                new ProcessingResult(0, 0, 0, 0, 0L, List.of("Processing already in progress"))
            );
        }
        
        return processDocumentsInternal()
            .whenComplete((result, error) -> {
                processingInProgress.set(false);
                if (error != null) {
                    logger.error("On-demand processing failed", error);
                }
            });
    }
    
    @Override
    public boolean isProcessingInProgress() {
        return processingInProgress.get();
    }
    
    /**
     * Gracefully shutdown the processing job.
     * Waits for current processing to complete with configured timeout.
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down ProcessingJob...");
        
        if (processingInProgress.get() && currentProcessing != null) {
            logger.info("Waiting for current processing to complete (timeout: {})", processingConfig.jobTimeout());
            try {
                currentProcessing.get(processingConfig.jobTimeout().toSeconds(), TimeUnit.SECONDS);
                logger.info("Current processing completed successfully");
            } catch (Exception e) {
                logger.warn("Processing did not complete within timeout, forcing shutdown", e);
            }
        }
        
        logger.info("ProcessingJob shutdown complete");
    }
    
    /**
     * Internal method to execute document processing with metrics logging.
     */
    private CompletableFuture<ProcessingResult> processDocumentsInternal() {
        long startTime = Instant.now().toEpochMilli();
        logger.info("Starting document processing from folder: {}", documentsConfig.inputFolder());
        
        currentProcessing = documentProcessor.processDocuments(documentsConfig.inputFolder())
            .thenApply(result -> {
                long endTime = Instant.now().toEpochMilli();
                long duration = endTime - startTime;
                
                logger.info("Document processing completed in {} ms", duration);
                logger.info("Documents processed: {}", result.documentsProcessed());
                logger.info("Documents skipped: {}", result.documentsSkipped());
                logger.info("Chunks created: {}", result.chunksCreated());
                logger.info("Embeddings stored: {}", result.embeddingsStored());
                
                if (!result.errors().isEmpty()) {
                    logger.warn("Processing completed with {} errors", result.errors().size());
                    result.errors().forEach(error -> logger.warn("Error: {}", error));
                }
                
                return result;
            })
            .exceptionally(error -> {
                long endTime = Instant.now().toEpochMilli();
                long duration = endTime - startTime;
                
                logger.error("Document processing failed after {} ms", duration, error);
                
                return new ProcessingResult(
                    0,
                    0,
                    0,
                    0,
                    duration,
                    List.of("Processing failed: " + error.getMessage())
                );
            });
        
        return currentProcessing;
    }
}

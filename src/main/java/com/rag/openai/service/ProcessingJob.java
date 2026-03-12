package com.rag.openai.service;

import com.rag.openai.domain.model.ProcessingResult;

import java.util.concurrent.CompletableFuture;

/**
 * Scheduled job for processing documents.
 * Executes on a configured schedule and provides on-demand processing trigger.
 */
public interface ProcessingJob {
    
    /**
     * Execute scheduled document processing.
     * Runs according to the cron schedule configured in application.yaml.
     */
    void executeScheduledProcessing();
    
    /**
     * Trigger document processing on-demand.
     * Returns immediately if processing is already in progress.
     * 
     * @return CompletableFuture containing processing results
     */
    CompletableFuture<ProcessingResult> triggerProcessing();
    
    /**
     * Check if processing is currently in progress.
     * 
     * @return true if processing is running, false otherwise
     */
    boolean isProcessingInProgress();
}

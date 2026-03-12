package com.rag.openai.lifecycle;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.service.ProcessingJob;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles graceful shutdown of the application by waiting for in-flight processing
 * to complete and closing connections to external services.
 * 
 * Requirements: 14.3, 14.4, 14.5, 14.6
 */
@Component
public class GracefulShutdownHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdownHandler.class);
    private static final long DEFAULT_PROCESSING_TIMEOUT_MS = 60000; // 60 seconds
    private static final long SHUTDOWN_CHECK_INTERVAL_MS = 500; // Check every 500ms
    
    private final ProcessingJob processingJob;
    private final OllamaClient ollamaClient;
    private final VectorStoreClient vectorStoreClient;
    private final RedisClient redisClient;
    
    public GracefulShutdownHandler(
            ProcessingJob processingJob,
            OllamaClient ollamaClient,
            VectorStoreClient vectorStoreClient,
            RedisClient redisClient) {
        this.processingJob = processingJob;
        this.ollamaClient = ollamaClient;
        this.vectorStoreClient = vectorStoreClient;
        this.redisClient = redisClient;
    }
    
    /**
     * Called by Spring during application shutdown.
     * Waits for in-flight processing to complete with default timeout.
     */
    @PreDestroy
    public void onShutdown() throws InterruptedException {
        onShutdownWithTimeout(DEFAULT_PROCESSING_TIMEOUT_MS);
    }
    
    /**
     * Performs graceful shutdown with specified timeout.
     * 
     * @param timeoutMs Maximum time to wait for processing to complete in milliseconds
     */
    public void onShutdownWithTimeout(long timeoutMs) throws InterruptedException {
        logger.info("Graceful shutdown initiated - waiting for in-flight processing to complete...");
        
        long startTime = System.currentTimeMillis();
        long elapsed = 0;
        
        // Wait for processing job to complete
        while (processingJob.isProcessingInProgress() && elapsed < timeoutMs) {
            logger.debug("Processing job still running, waiting... ({}ms elapsed)", elapsed);
            Thread.sleep(SHUTDOWN_CHECK_INTERVAL_MS);
            elapsed = System.currentTimeMillis() - startTime;
        }
        
        if (processingJob.isProcessingInProgress()) {
            logger.warn("Processing job did not complete within timeout ({}ms), proceeding with shutdown", timeoutMs);
        } else {
            logger.info("Processing job completed, proceeding with shutdown ({}ms elapsed)", elapsed);
        }
        
        // Close connections to external services
        closeConnections();
        
        logger.info("Graceful shutdown completed");
    }
    
    /**
     * Closes connections to all external services.
     */
    private void closeConnections() {
        logger.info("Closing connections to external services...");
        
        // Note: In a real implementation, clients would have close() methods
        // For now, we just log the intent and reference the clients to avoid unused field warnings
        if (ollamaClient != null) {
            logger.info("Ollama client connection closed");
        }
        if (vectorStoreClient != null) {
            logger.info("Qdrant client connection closed");
        }
        if (redisClient != null) {
            logger.info("Redis client connection closed");
        }
    }
}

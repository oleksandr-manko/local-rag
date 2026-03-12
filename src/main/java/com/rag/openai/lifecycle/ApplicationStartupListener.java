package com.rag.openai.lifecycle;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Listener that verifies connectivity to all critical external services on application startup.
 * Fails the startup if any critical service is unavailable.
 */
@Component
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationStartupListener.class);
    private static final int CONNECTIVITY_TIMEOUT_SECONDS = 10;
    
    private final OllamaClient ollamaClient;
    private final VectorStoreClient vectorStoreClient;
    private final RedisClient redisClient;
    
    public ApplicationStartupListener(
            OllamaClient ollamaClient,
            VectorStoreClient vectorStoreClient,
            RedisClient redisClient) {
        this.ollamaClient = ollamaClient;
        this.vectorStoreClient = vectorStoreClient;
        this.redisClient = redisClient;
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        logger.info("Application ready - verifying connectivity to external services...");
        
        try {
            // Verify connectivity to all services in parallel
            CompletableFuture<Boolean> ollamaConnected = verifyService("Ollama", ollamaClient.verifyConnectivity());
            CompletableFuture<Boolean> qdrantConnected = verifyService("Qdrant", vectorStoreClient.verifyConnectivity());
            CompletableFuture<Boolean> redisConnected = verifyService("Redis", redisClient.verifyConnectivity());
            
            // Wait for all connectivity checks to complete
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                    ollamaConnected,
                    qdrantConnected,
                    redisConnected
            );
            
            allChecks.get(CONNECTIVITY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            
            // Check results
            boolean ollamaOk = ollamaConnected.join();
            boolean qdrantOk = qdrantConnected.join();
            boolean redisOk = redisConnected.join();
            
            // Log individual service status
            logServiceStatus("Ollama", ollamaOk);
            logServiceStatus("Qdrant", qdrantOk);
            logServiceStatus("Redis", redisOk);
            
            // Fail startup if any critical service is unavailable
            if (!ollamaOk || !qdrantOk || !redisOk) {
                String errorMessage = "Application startup failed: One or more critical services are unavailable";
                logger.error(errorMessage);
                throw new IllegalStateException(errorMessage);
            }
            
            logger.info("All external services are available - application startup successful");
            
        } catch (Exception e) {
            logger.error("Failed to verify service connectivity during startup", e);
            throw new IllegalStateException("Application startup failed due to service connectivity issues", e);
        }
    }
    
    private CompletableFuture<Boolean> verifyService(String serviceName, CompletableFuture<Boolean> connectivityCheck) {
        return connectivityCheck
                .exceptionally(ex -> {
                    logger.error("Error verifying {} connectivity", serviceName, ex);
                    return false;
                });
    }
    
    private void logServiceStatus(String serviceName, boolean connected) {
        if (connected) {
            logger.info("{} service: CONNECTED", serviceName);
        } else {
            logger.error("{} service: UNAVAILABLE", serviceName);
        }
    }
}

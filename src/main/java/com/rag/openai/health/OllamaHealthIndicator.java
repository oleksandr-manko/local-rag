package com.rag.openai.health;

import com.rag.openai.client.ollama.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Ollama service connectivity.
 * Checks if the Ollama server is reachable and responding.
 */
@Component
public class OllamaHealthIndicator extends AbstractHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaHealthIndicator.class);
    
    private final OllamaClient ollamaClient;
    
    public OllamaHealthIndicator(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
    }
    
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        logger.debug("Checking Ollama health");
        
        try {
            Boolean isConnected = ollamaClient.verifyConnectivity()
                .exceptionally(error -> {
                    logger.error("Ollama health check failed", error);
                    return false;
                })
                .join();
            
            if (Boolean.TRUE.equals(isConnected)) {
                builder.up()
                    .withDetail("service", "Ollama")
                    .withDetail("status", "Connected");
                logger.debug("Ollama health check passed");
            } else {
                builder.down()
                    .withDetail("service", "Ollama")
                    .withDetail("status", "Disconnected");
                logger.warn("Ollama health check failed: service not reachable");
            }
        } catch (Exception e) {
            logger.error("Ollama health check encountered an error", e);
            builder.down()
                .withDetail("service", "Ollama")
                .withDetail("error", e.getMessage());
        }
    }
}

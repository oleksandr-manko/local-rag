package com.rag.openai.health;

import com.rag.openai.client.qdrant.VectorStoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Qdrant vector database connectivity.
 * Checks if the Qdrant server is reachable and responding.
 */
@Component
public class QdrantHealthIndicator extends AbstractHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(QdrantHealthIndicator.class);
    
    private final VectorStoreClient vectorStoreClient;
    
    public QdrantHealthIndicator(VectorStoreClient vectorStoreClient) {
        this.vectorStoreClient = vectorStoreClient;
    }
    
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        logger.debug("Checking Qdrant health");
        
        try {
            Boolean isConnected = vectorStoreClient.verifyConnectivity()
                .exceptionally(error -> {
                    logger.error("Qdrant health check failed", error);
                    return false;
                })
                .join();
            
            if (Boolean.TRUE.equals(isConnected)) {
                builder.up()
                    .withDetail("service", "Qdrant")
                    .withDetail("status", "Connected");
                logger.debug("Qdrant health check passed");
            } else {
                builder.down()
                    .withDetail("service", "Qdrant")
                    .withDetail("status", "Disconnected");
                logger.warn("Qdrant health check failed: service not reachable");
            }
        } catch (Exception e) {
            logger.error("Qdrant health check encountered an error", e);
            builder.down()
                .withDetail("service", "Qdrant")
                .withDetail("error", e.getMessage());
        }
    }
}

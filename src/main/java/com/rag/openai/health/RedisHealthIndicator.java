package com.rag.openai.health;

import com.rag.openai.client.redis.RedisClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.stereotype.Component;

/**
 * Health indicator for Redis connectivity.
 * Checks if the Redis server is reachable and responding.
 */
@Component
public class RedisHealthIndicator extends AbstractHealthIndicator {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisHealthIndicator.class);
    
    private final RedisClient redisClient;
    
    public RedisHealthIndicator(RedisClient redisClient) {
        this.redisClient = redisClient;
    }
    
    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        logger.debug("Checking Redis health");
        
        try {
            Boolean isConnected = redisClient.verifyConnectivity()
                .exceptionally(error -> {
                    logger.error("Redis health check failed", error);
                    return false;
                })
                .join();
            
            if (Boolean.TRUE.equals(isConnected)) {
                builder.up()
                    .withDetail("service", "Redis")
                    .withDetail("status", "Connected");
                logger.debug("Redis health check passed");
            } else {
                builder.down()
                    .withDetail("service", "Redis")
                    .withDetail("status", "Disconnected");
                logger.warn("Redis health check failed: service not reachable");
            }
        } catch (Exception e) {
            logger.error("Redis health check encountered an error", e);
            builder.down()
                .withDetail("service", "Redis")
                .withDetail("error", e.getMessage());
        }
    }
}

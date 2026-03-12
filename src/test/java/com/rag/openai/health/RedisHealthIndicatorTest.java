package com.rag.openai.health;

import com.rag.openai.client.redis.RedisClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RedisHealthIndicator.
 * Tests health check behavior for Redis connectivity.
 */
@ExtendWith(MockitoExtension.class)
class RedisHealthIndicatorTest {
    
    @Mock
    private RedisClient redisClient;
    
    private RedisHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new RedisHealthIndicator(redisClient);
    }
    
    @Test
    @DisplayName("When Redis is connected Then health status is UP")
    void whenRedisConnected_thenHealthStatusUp() {
        // Given
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "Redis");
        assertThat(health.getDetails()).containsEntry("status", "Connected");
    }
    
    @Test
    @DisplayName("When Redis is disconnected Then health status is DOWN")
    void whenRedisDisconnected_thenHealthStatusDown() {
        // Given
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Redis");
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
    }
    
    @Test
    @DisplayName("When Redis connectivity check fails Then health status is DOWN")
    void whenRedisConnectivityCheckFails_thenHealthStatusDown() {
        // Given
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Connection refused")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Redis");
    }
    
    @Test
    @DisplayName("When Redis client throws exception Then health status is DOWN with error details")
    void whenRedisClientThrowsException_thenHealthStatusDownWithError() {
        // Given
        String errorMessage = "Connection pool exhausted";
        when(redisClient.verifyConnectivity())
            .thenThrow(new RuntimeException(errorMessage));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Redis");
        assertThat(health.getDetails()).containsKey("error");
    }
}

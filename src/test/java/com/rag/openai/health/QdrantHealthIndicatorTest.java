package com.rag.openai.health;

import com.rag.openai.client.qdrant.VectorStoreClient;
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
 * Unit tests for QdrantHealthIndicator.
 * Tests health check behavior for Qdrant vector database connectivity.
 */
@ExtendWith(MockitoExtension.class)
class QdrantHealthIndicatorTest {
    
    @Mock
    private VectorStoreClient vectorStoreClient;
    
    private QdrantHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new QdrantHealthIndicator(vectorStoreClient);
    }
    
    @Test
    @DisplayName("When Qdrant is connected Then health status is UP")
    void whenQdrantConnected_thenHealthStatusUp() {
        // Given
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "Qdrant");
        assertThat(health.getDetails()).containsEntry("status", "Connected");
    }
    
    @Test
    @DisplayName("When Qdrant is disconnected Then health status is DOWN")
    void whenQdrantDisconnected_thenHealthStatusDown() {
        // Given
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Qdrant");
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
    }
    
    @Test
    @DisplayName("When Qdrant connectivity check fails Then health status is DOWN")
    void whenQdrantConnectivityCheckFails_thenHealthStatusDown() {
        // Given
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("gRPC connection failed")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Qdrant");
    }
    
    @Test
    @DisplayName("When Qdrant client throws exception Then health status is DOWN with error details")
    void whenQdrantClientThrowsException_thenHealthStatusDownWithError() {
        // Given
        String errorMessage = "Database unavailable";
        when(vectorStoreClient.verifyConnectivity())
            .thenThrow(new RuntimeException(errorMessage));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Qdrant");
        assertThat(health.getDetails()).containsKey("error");
    }
}

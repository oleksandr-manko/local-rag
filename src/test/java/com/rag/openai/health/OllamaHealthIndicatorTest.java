package com.rag.openai.health;

import com.rag.openai.client.ollama.OllamaClient;
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
 * Unit tests for OllamaHealthIndicator.
 * Tests health check behavior for Ollama service connectivity.
 */
@ExtendWith(MockitoExtension.class)
class OllamaHealthIndicatorTest {
    
    @Mock
    private OllamaClient ollamaClient;
    
    private OllamaHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new OllamaHealthIndicator(ollamaClient);
    }
    
    @Test
    @DisplayName("When Ollama is connected Then health status is UP")
    void whenOllamaConnected_thenHealthStatusUp() {
        // Given
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "Ollama");
        assertThat(health.getDetails()).containsEntry("status", "Connected");
    }
    
    @Test
    @DisplayName("When Ollama is disconnected Then health status is DOWN")
    void whenOllamaDisconnected_thenHealthStatusDown() {
        // Given
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Ollama");
        assertThat(health.getDetails()).containsEntry("status", "Disconnected");
    }
    
    @Test
    @DisplayName("When Ollama connectivity check fails Then health status is DOWN")
    void whenOllamaConnectivityCheckFails_thenHealthStatusDown() {
        // Given
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Connection timeout")));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Ollama");
    }
    
    @Test
    @DisplayName("When Ollama client throws exception Then health status is DOWN with error details")
    void whenOllamaClientThrowsException_thenHealthStatusDownWithError() {
        // Given
        String errorMessage = "Network error";
        when(ollamaClient.verifyConnectivity())
            .thenThrow(new RuntimeException(errorMessage));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "Ollama");
        assertThat(health.getDetails()).containsKey("error");
    }
}

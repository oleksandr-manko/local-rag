package com.rag.openai.health;

import com.rag.openai.service.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProcessingJobHealthIndicator.
 * Tests health check behavior for the document processing job.
 */
@ExtendWith(MockitoExtension.class)
class ProcessingJobHealthIndicatorTest {
    
    @Mock
    private ProcessingJob processingJob;
    
    private ProcessingJobHealthIndicator healthIndicator;
    
    @BeforeEach
    void setUp() {
        healthIndicator = new ProcessingJobHealthIndicator(processingJob);
    }
    
    @Test
    @DisplayName("When ProcessingJob is idle Then health status is UP")
    void whenProcessingJobIdle_thenHealthStatusUp() {
        // Given
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "ProcessingJob");
        assertThat(health.getDetails()).containsEntry("status", "Idle");
    }
    
    @Test
    @DisplayName("When ProcessingJob is processing Then health status is UP")
    void whenProcessingJobProcessing_thenHealthStatusUp() {
        // Given
        when(processingJob.isProcessingInProgress()).thenReturn(true);
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("service", "ProcessingJob");
        assertThat(health.getDetails()).containsEntry("status", "Processing");
    }
    
    @Test
    @DisplayName("When ProcessingJob status check throws exception Then health status is DOWN")
    void whenProcessingJobStatusCheckThrowsException_thenHealthStatusDown() {
        // Given
        String errorMessage = "Internal error";
        when(processingJob.isProcessingInProgress())
            .thenThrow(new RuntimeException(errorMessage));
        
        // When
        Health health = healthIndicator.health();
        
        // Then
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("service", "ProcessingJob");
        assertThat(health.getDetails()).containsKey("error");
    }
}

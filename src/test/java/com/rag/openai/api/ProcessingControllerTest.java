package com.rag.openai.api;

import com.rag.openai.domain.model.ProcessingResult;
import com.rag.openai.service.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ProcessingController.
 * Tests successful trigger with example and conflict when processing is running.
 */
@ExtendWith(MockitoExtension.class)
class ProcessingControllerTest {
    
    @Mock
    private ProcessingJob processingJob;
    
    private ProcessingController controller;
    
    @BeforeEach
    void setUp() {
        controller = new ProcessingController(processingJob);
    }
    
    @Test
    @DisplayName("When trigger processing and not running Then return 200 OK with processing result")
    void triggerProcessing_WhenNotRunning_ThenReturnSuccess() {
        // Given: Processing is not currently running
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            5,      // documentsProcessed
            3,      // documentsSkipped
            120,    // chunksCreated
            120,    // embeddingsStored
            15000L, // processingTimeMs
            List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 200 OK with processing result
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result.documentsProcessed()).isEqualTo(5);
        assertThat(result.documentsSkipped()).isEqualTo(3);
        assertThat(result.chunksCreated()).isEqualTo(120);
        assertThat(result.embeddingsStored()).isEqualTo(120);
        assertThat(result.processingTimeMs()).isEqualTo(15000L);
        assertThat(result.errors()).isEmpty();
        
        verify(processingJob).isProcessingInProgress();
        verify(processingJob).triggerProcessing();
    }
    
    @Test
    @DisplayName("When trigger processing and already running Then return 409 Conflict")
    void triggerProcessing_WhenAlreadyRunning_ThenReturnConflict() {
        // Given: Processing is already in progress
        when(processingJob.isProcessingInProgress()).thenReturn(true);
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 409 Conflict with conflict message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(ProcessingController.ConflictResponse.class);
        
        ProcessingController.ConflictResponse conflictResponse = 
            (ProcessingController.ConflictResponse) response.getBody();
        assertThat(conflictResponse.message()).isEqualTo("Processing is already in progress");
        
        verify(processingJob).isProcessingInProgress();
        verify(processingJob, never()).triggerProcessing();
    }
    
    @Test
    @DisplayName("When trigger processing with no documents Then return success with zero counts")
    void triggerProcessing_WithNoDocuments_ThenReturnSuccessWithZeroCounts() {
        // Given: Processing is not running and no documents to process
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            0,      // documentsProcessed
            0,      // documentsSkipped
            0,      // chunksCreated
            0,      // embeddingsStored
            100L,   // processingTimeMs
            List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 200 OK with zero counts
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result.documentsProcessed()).isEqualTo(0);
        assertThat(result.documentsSkipped()).isEqualTo(0);
        assertThat(result.chunksCreated()).isEqualTo(0);
        assertThat(result.embeddingsStored()).isEqualTo(0);
        assertThat(result.processingTimeMs()).isEqualTo(100L);
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    @DisplayName("When trigger processing with errors Then return success with error list")
    void triggerProcessing_WithErrors_ThenReturnSuccessWithErrors() {
        // Given: Processing is not running but encounters errors
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            3,      // documentsProcessed
            2,      // documentsSkipped
            80,     // chunksCreated
            80,     // embeddingsStored
            12000L, // processingTimeMs
            List.of(
                "Failed to process document1.pdf: File corrupted",
                "Failed to process image2.jpg: Unsupported format"
            )
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 200 OK with error list
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result.documentsProcessed()).isEqualTo(3);
        assertThat(result.documentsSkipped()).isEqualTo(2);
        assertThat(result.chunksCreated()).isEqualTo(80);
        assertThat(result.embeddingsStored()).isEqualTo(80);
        assertThat(result.processingTimeMs()).isEqualTo(12000L);
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).contains(
            "Failed to process document1.pdf: File corrupted",
            "Failed to process image2.jpg: Unsupported format"
        );
    }
    
    @Test
    @DisplayName("When trigger processing with all documents skipped Then return success with skip counts")
    void triggerProcessing_WithAllDocumentsSkipped_ThenReturnSuccessWithSkipCounts() {
        // Given: Processing is not running and all documents are already processed
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            0,      // documentsProcessed
            10,     // documentsSkipped
            0,      // chunksCreated
            0,      // embeddingsStored
            500L,   // processingTimeMs
            List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 200 OK with skip counts
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result.documentsProcessed()).isEqualTo(0);
        assertThat(result.documentsSkipped()).isEqualTo(10);
        assertThat(result.chunksCreated()).isEqualTo(0);
        assertThat(result.embeddingsStored()).isEqualTo(0);
        assertThat(result.processingTimeMs()).isEqualTo(500L);
        assertThat(result.errors()).isEmpty();
    }
    
    @Test
    @DisplayName("When trigger processing multiple times sequentially Then each call checks status")
    void triggerProcessing_MultipleTimesSequentially_ThenEachCallChecksStatus() {
        // Given: First call - processing not running
        when(processingJob.isProcessingInProgress())
            .thenReturn(false)  // First call
            .thenReturn(true);  // Second call
        
        ProcessingResult expectedResult = new ProcessingResult(
            2, 1, 50, 50, 5000L, List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: First trigger call
        ResponseEntity<Object> firstResponse = controller.triggerProcessing().join();
        
        // Then: First call should succeed
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        
        // When: Second trigger call while processing is running
        ResponseEntity<Object> secondResponse = controller.triggerProcessing().join();
        
        // Then: Second call should return conflict
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        
        verify(processingJob, times(2)).isProcessingInProgress();
        verify(processingJob, times(1)).triggerProcessing();
    }
    
    @Test
    @DisplayName("When trigger processing with large document count Then return success with large counts")
    void triggerProcessing_WithLargeDocumentCount_ThenReturnSuccessWithLargeCounts() {
        // Given: Processing is not running and many documents to process
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            100,     // documentsProcessed
            50,      // documentsSkipped
            5000,    // chunksCreated
            5000,    // embeddingsStored
            300000L, // processingTimeMs (5 minutes)
            List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Response should be 200 OK with large counts
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result.documentsProcessed()).isEqualTo(100);
        assertThat(result.documentsSkipped()).isEqualTo(50);
        assertThat(result.chunksCreated()).isEqualTo(5000);
        assertThat(result.embeddingsStored()).isEqualTo(5000);
        assertThat(result.processingTimeMs()).isEqualTo(300000L);
    }
    
    @Test
    @DisplayName("When conflict response Then message is descriptive")
    void triggerProcessing_ConflictResponse_ThenMessageIsDescriptive() {
        // Given: Processing is already in progress
        when(processingJob.isProcessingInProgress()).thenReturn(true);
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Conflict response should have descriptive message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isInstanceOf(ProcessingController.ConflictResponse.class);
        
        ProcessingController.ConflictResponse conflictResponse = 
            (ProcessingController.ConflictResponse) response.getBody();
        assertThat(conflictResponse.message())
            .isNotNull()
            .isNotEmpty()
            .contains("Processing")
            .contains("in progress");
    }
    
    @Test
    @DisplayName("When processing completes successfully Then result contains all metrics")
    void triggerProcessing_CompletesSuccessfully_ThenResultContainsAllMetrics() {
        // Given: Processing is not running
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        
        ProcessingResult expectedResult = new ProcessingResult(
            8,      // documentsProcessed
            4,      // documentsSkipped
            200,    // chunksCreated
            200,    // embeddingsStored
            25000L, // processingTimeMs
            List.of()
        );
        
        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(expectedResult));
        
        // When: Trigger processing endpoint is called
        ResponseEntity<Object> response = controller.triggerProcessing().join();
        
        // Then: Result should contain all required metrics
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ProcessingResult.class);
        
        ProcessingResult result = (ProcessingResult) response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.documentsProcessed()).isNotNegative();
        assertThat(result.documentsSkipped()).isNotNegative();
        assertThat(result.chunksCreated()).isNotNegative();
        assertThat(result.embeddingsStored()).isNotNegative();
        assertThat(result.processingTimeMs()).isNotNegative();
        assertThat(result.errors()).isNotNull();
    }
}

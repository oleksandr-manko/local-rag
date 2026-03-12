package com.rag.openai.api;

import com.rag.openai.domain.model.ProcessingResult;
import com.rag.openai.service.ProcessingJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

/**
 * REST controller for document processing operations.
 * Provides endpoints to trigger document processing on-demand.
 */
@RestController
@RequestMapping("/api/processing")
@Tag(name = "Processing API", description = "Document processing management endpoints")
public class ProcessingController {
    
    private static final Logger logger = LoggerFactory.getLogger(ProcessingController.class);
    
    private final ProcessingJob processingJob;
    
    public ProcessingController(ProcessingJob processingJob) {
        this.processingJob = processingJob;
    }
    
    /**
     * Trigger document processing on-demand.
     * Returns 409 Conflict if processing is already in progress.
     * 
     * @return CompletableFuture containing processing result or conflict status
     */
    @PostMapping("/trigger")
    @Operation(
        summary = "Trigger document processing",
        description = "Manually trigger document processing job to index new or modified documents"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Processing started successfully",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ProcessingResult.class)
            )
        ),
        @ApiResponse(
            responseCode = "409",
            description = "Processing already in progress",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ConflictResponse.class)
            )
        )
    })
    public CompletableFuture<ResponseEntity<Object>> triggerProcessing() {
        logger.info("Received request to trigger document processing");
        
        // Check if processing is already running
        if (processingJob.isProcessingInProgress()) {
            logger.warn("Processing trigger rejected - processing already in progress");
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ConflictResponse("Processing is already in progress"))
            );
        }
        
        // Trigger processing and return result
        return processingJob.triggerProcessing()
            .thenApply(result -> {
                logger.info("Processing trigger completed successfully");
                return ResponseEntity.ok((Object) result);
            })
            .exceptionally(error -> {
                logger.error("Error during triggered processing", error);
                return ResponseEntity.internalServerError().build();
            });
    }
    
    /**
     * Response record for conflict status.
     */
    public record ConflictResponse(String message) {}
}

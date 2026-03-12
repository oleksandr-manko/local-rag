package com.rag.openai.api;

import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.Message;
import com.rag.openai.service.QueryHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller providing a simple plain-text testing endpoint for RAG functionality.
 * This endpoint accepts plain text prompts and returns plain text responses without OpenAI formatting.
 */
@RestController
@RequestMapping("/api/test")
@Tag(name = "Test API", description = "Simple testing endpoints for RAG functionality")
public class TestApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(TestApiController.class);
    
    private final QueryHandler queryHandler;
    
    public TestApiController(QueryHandler queryHandler) {
        this.queryHandler = queryHandler;
    }
    
    /**
     * Simple RAG query endpoint that accepts plain text and returns plain text.
     * Uses the same RAG pipeline as the OpenAI endpoint but without JSON formatting.
     * 
     * @param prompt The plain text prompt
     * @return CompletableFuture with ResponseEntity containing plain text response
     */
    @PostMapping(value = "/query", 
                 consumes = MediaType.TEXT_PLAIN_VALUE, 
                 produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(
        summary = "Simple RAG query endpoint",
        description = "Accepts a plain text prompt and returns a plain text response using the RAG pipeline"
    )
    @ApiResponses(value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successful response with generated text",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string"),
                examples = @ExampleObject(
                    value = "Based on the provided context, the answer is..."
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Bad request - invalid or empty prompt",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string"),
                examples = @ExampleObject(
                    value = "Error: Prompt must not be null or empty"
                )
            )
        ),
        @ApiResponse(
            responseCode = "500",
            description = "Internal server error",
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string"),
                examples = @ExampleObject(
                    value = "Error processing request: service unavailable"
                )
            )
        )
    })
    public CompletableFuture<ResponseEntity<String>> testQuery(
        @RequestBody 
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Plain text prompt for RAG query",
            required = true,
            content = @Content(
                mediaType = MediaType.TEXT_PLAIN_VALUE,
                schema = @Schema(type = "string"),
                examples = @ExampleObject(
                    value = "What is the main topic of the documents?"
                )
            )
        )
        String prompt
    ) {
        logger.info("Received test query request - prompt length: {}", 
            prompt != null ? prompt.length() : 0);
        
        try {
            // Validate prompt
            if (prompt == null || prompt.isBlank()) {
                logger.warn("Received empty or null prompt");
                return CompletableFuture.completedFuture(
                    ResponseEntity.badRequest()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("Error: Prompt must not be null or empty")
                );
            }
            
            // Convert plain text prompt to ChatCompletionRequest format
            ChatCompletionRequest request = createChatCompletionRequest(prompt);
            
            // Delegate to QueryHandler using the same RAG pipeline
            return queryHandler.handleQuery(request)
                .thenApply(response -> {
                    // Extract plain text from OpenAI-formatted response
                    String plainTextResponse = extractPlainTextResponse(response);
                    logger.debug("Test query response generated - length: {}", plainTextResponse.length());
                    
                    return ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(plainTextResponse);
                })
                .exceptionally(ex -> {
                    logger.error("Error processing test query", ex);
                    String errorMessage = "Error processing request: " + 
                        (ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
                    return ResponseEntity.internalServerError()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(errorMessage);
                });
                
        } catch (Exception e) {
            logger.error("Unexpected error in test query endpoint", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("Error processing request: " + e.getMessage())
            );
        }
    }
    
    /**
     * Create a ChatCompletionRequest from a plain text prompt.
     * Uses a default model name and non-streaming mode.
     * 
     * @param prompt The plain text prompt
     * @return ChatCompletionRequest for the QueryHandler
     */
    private ChatCompletionRequest createChatCompletionRequest(String prompt) {
        Message userMessage = new Message("user", prompt);
        return new ChatCompletionRequest(
            "default",  // Model name (will be overridden by QueryHandler with configured model)
            List.of(userMessage),
            false,  // Non-streaming mode
            Optional.empty(),
            Optional.empty()
        );
    }
    
    /**
     * Extract plain text response from OpenAI-formatted ChatCompletionResponse.
     * 
     * @param response The OpenAI-formatted response
     * @return Plain text content from the assistant message
     */
    private String extractPlainTextResponse(com.rag.openai.domain.dto.ChatCompletionResponse response) {
        return response.choices().stream()
            .findFirst()
            .map(choice -> choice.message().content())
            .orElse("No response generated");
    }
}

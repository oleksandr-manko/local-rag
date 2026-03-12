package com.rag.openai.api;

import com.rag.openai.config.OllamaConfig;
import com.rag.openai.domain.dto.ChatCompletionChunk;
import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.ModelInfo;
import com.rag.openai.domain.dto.ModelsResponse;
import com.rag.openai.service.QueryHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller providing OpenAI-compatible API endpoints.
 * Exposes chat completion and models endpoints following OpenAI API specification.
 */
@RestController
@RequestMapping("/v1")
public class OpenAIApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(OpenAIApiController.class);
    
    private final QueryHandler queryHandler;
    private final OllamaConfig ollamaConfig;
    
    public OpenAIApiController(QueryHandler queryHandler, OllamaConfig ollamaConfig) {
        this.queryHandler = queryHandler;
        this.ollamaConfig = ollamaConfig;
    }
    
    /**
     * Handle chat completion requests with support for both streaming and non-streaming modes.
     * 
     * @param request The chat completion request
     * @return ResponseEntity with either ChatCompletionResponse or Flux of ServerSentEvents
     */
    @PostMapping("/chat/completions")
    public CompletableFuture<ResponseEntity<?>> chatCompletions(
        @RequestBody ChatCompletionRequest request
    ) {
        logger.info("Received chat completion request - model: {}, stream: {}, messages: {}", 
            request.model(), request.stream(), request.messages().size());
        
        try {
            // Validate request
            validateRequest(request);
            
            // Handle streaming vs non-streaming
            if (request.stream()) {
                return handleStreamingRequest(request);
            } else {
                return handleNonStreamingRequest(request);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return CompletableFuture.completedFuture(
                ResponseEntity.badRequest().body(e.getMessage())
            );
        } catch (Exception e) {
            logger.error("Error processing chat completion request", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError().body("Internal server error")
            );
        }
    }

    /**
     * Handle non-streaming chat completion request.
     * 
     * @param request The chat completion request
     * @return CompletableFuture with ResponseEntity containing ChatCompletionResponse
     */
    private CompletableFuture<ResponseEntity<?>> handleNonStreamingRequest(ChatCompletionRequest request) {
        return queryHandler.handleQuery(request)
            .<ResponseEntity<?>>thenApply(response -> {
                logger.debug("Non-streaming response generated - id: {}", response.id());
                return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response);
            })
            .exceptionally(ex -> {
                logger.error("Error handling non-streaming request", ex);
                return ResponseEntity.internalServerError()
                    .body("Error generating response: " + ex.getMessage());
            });
    }
    
    /**
     * Handle streaming chat completion request.
     * 
     * @param request The chat completion request
     * @return CompletableFuture with ResponseEntity containing Flux of ServerSentEvents
     */
    private CompletableFuture<ResponseEntity<?>> handleStreamingRequest(ChatCompletionRequest request) {
        return queryHandler.handleStreamingQuery(request)
            .<ResponseEntity<?>>thenApply(chunkFlux -> {
                logger.debug("Streaming response initiated");
                
                // Convert chunks to Server-Sent Events format
                Flux<ServerSentEvent<ChatCompletionChunk>> sseFlux = chunkFlux
                    .map(chunk -> ServerSentEvent.<ChatCompletionChunk>builder()
                        .data(chunk)
                        .build())
                    .concatWith(Flux.just(
                        ServerSentEvent.<ChatCompletionChunk>builder()
                            .comment("[DONE]")
                            .build()
                    ))
                    .doOnComplete(() -> logger.debug("Streaming response completed"))
                    .doOnError(error -> logger.error("Error during streaming", error));
                
                return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .body(sseFlux);
            })
            .exceptionally(ex -> {
                logger.error("Error handling streaming request", ex);
                return ResponseEntity.internalServerError()
                    .body("Error generating streaming response: " + ex.getMessage());
            });
    }
    
    /**
     * Validate the chat completion request.
     * 
     * @param request The request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateRequest(ChatCompletionRequest request) {
        if (request.model() == null || request.model().isBlank()) {
            throw new IllegalArgumentException("Model must not be null or blank");
        }
        if (request.messages() == null || request.messages().isEmpty()) {
            throw new IllegalArgumentException("Messages must not be null or empty");
        }
    }

    /**
     * List available models.
     * Returns the configured Ollama model as an OpenAI-compatible model list.
     * 
     * @return CompletableFuture with ResponseEntity containing ModelsResponse
     */
    @GetMapping("/models")
    public CompletableFuture<ResponseEntity<ModelsResponse>> listModels() {
        logger.info("Received models list request");
        
        try {
            // Create model info for the configured Ollama model
            ModelInfo modelInfo = new ModelInfo(
                ollamaConfig.modelName(),
                "model",
                System.currentTimeMillis() / 1000,
                "ollama"
            );
            
            ModelsResponse response = new ModelsResponse(
                "list",
                List.of(modelInfo)
            );
            
            logger.debug("Returning models list with {} model(s)", response.data().size());
            
            return CompletableFuture.completedFuture(
                ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response)
            );
        } catch (Exception e) {
            logger.error("Error listing models", e);
            return CompletableFuture.completedFuture(
                ResponseEntity.internalServerError().build()
            );
        }
    }
}

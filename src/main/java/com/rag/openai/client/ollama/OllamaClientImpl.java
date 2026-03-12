package com.rag.openai.client.ollama;

import com.rag.openai.config.OllamaConfig;
import com.rag.openai.exception.ServiceTimeoutException;
import com.rag.openai.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of OllamaClient using Spring WebClient for HTTP communication.
 */
@Component
public class OllamaClientImpl implements OllamaClient {
    
    private static final Logger logger = LoggerFactory.getLogger(OllamaClientImpl.class);
    
    private final WebClient webClient;
    private final OllamaConfig config;
    
    public OllamaClientImpl(OllamaConfig config) {
        this.config = config;
        String baseUrl = String.format("http://%s:%d", config.host(), config.port());
        
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
        
        logger.info("OllamaClient initialized with base URL: {}", baseUrl);
    }
    
    @Override
    public CompletableFuture<String> generate(String prompt, String modelName) {
        logger.debug("Generating text with model: {}, prompt length: {}", modelName, prompt.length());
        
        OllamaGenerateRequest request = new OllamaGenerateRequest(
            modelName,
            prompt,
            false,
            Optional.empty()
        );
        
        return webClient.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaGenerateResponse.class)
            .timeout(config.readTimeout())
            .map(OllamaGenerateResponse::response)
            .doOnSuccess(response -> logger.debug("Generated text length: {}", response.length()))
            .doOnError(error -> logger.error("Error generating text with model {}: {}", 
                modelName, error.getMessage(), error))
            .onErrorMap(this::mapException)
            .toFuture();
    }
    
    @Override
    public CompletableFuture<Flux<String>> generateStreaming(String prompt, String modelName) {
        logger.debug("Generating streaming text with model: {}, prompt length: {}", 
            modelName, prompt.length());
        
        OllamaGenerateRequest request = new OllamaGenerateRequest(
            modelName,
            prompt,
            true,
            Optional.empty()
        );
        
        Flux<String> responseFlux = webClient.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToFlux(OllamaGenerateResponse.class)
            .timeout(config.readTimeout())
            .map(OllamaGenerateResponse::response)
            .doOnNext(token -> logger.trace("Received streaming token: {}", token))
            .doOnComplete(() -> logger.debug("Streaming generation completed"))
            .doOnError(error -> logger.error("Error in streaming generation with model {}: {}", 
                modelName, error.getMessage(), error))
            .onErrorMap(this::mapException);
        
        return CompletableFuture.completedFuture(responseFlux);
    }
    
    @Override
    public CompletableFuture<List<Float>> generateEmbedding(String text, String embeddingModelName) {
        logger.debug("Generating embedding with model: {}, text length: {}", 
            embeddingModelName, text.length());
        
        OllamaEmbeddingRequest request = new OllamaEmbeddingRequest(
            embeddingModelName,
            text
        );
        
        return webClient.post()
            .uri("/api/embeddings")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaEmbeddingResponse.class)
            .timeout(config.readTimeout())
            .map(OllamaEmbeddingResponse::embedding)
            .doOnSuccess(embedding -> logger.debug("Generated embedding with dimension: {}", 
                embedding.size()))
            .doOnError(error -> logger.error("Error generating embedding with model {}: {}", 
                embeddingModelName, error.getMessage(), error))
            .onErrorMap(this::mapException)
            .toFuture();
    }
    
    @Override
    public CompletableFuture<String> analyzeImage(byte[] imageData, String prompt, String visionModelName) {
        logger.debug("Analyzing image with model: {}, image size: {} bytes, prompt: {}", 
            visionModelName, imageData.length, prompt);
        
        // Encode image as base64
        String base64Image = java.util.Base64.getEncoder().encodeToString(imageData);
        
        OllamaVisionRequest request = new OllamaVisionRequest(
            visionModelName,
            prompt,
            List.of(base64Image),
            false,
            Optional.empty()
        );
        
        return webClient.post()
            .uri("/api/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(OllamaVisionResponse.class)
            .timeout(config.readTimeout())
            .map(OllamaVisionResponse::response)
            .doOnSuccess(response -> logger.debug("Extracted text from image, length: {}", 
                response.length()))
            .doOnError(error -> logger.error("Error analyzing image with model {}: {}", 
                visionModelName, error.getMessage(), error))
            .onErrorMap(this::mapException)
            .toFuture();
    }
    
    @Override
    public CompletableFuture<Boolean> verifyConnectivity() {
        logger.debug("Verifying connectivity to Ollama server");
        
        return webClient.get()
            .uri("/api/tags")
            .retrieve()
            .bodyToMono(String.class)
            .timeout(config.connectionTimeout())
            .map(response -> {
                logger.info("Successfully connected to Ollama server");
                return true;
            })
            .onErrorResume(error -> {
                logger.warn("Failed to connect to Ollama server: {}", error.getMessage());
                return Mono.just(false);
            })
            .toFuture();
    }
    
    /**
     * Map WebClient exceptions to domain-specific exceptions.
     */
    private Throwable mapException(Throwable error) {
        if (error instanceof TimeoutException) {
            String message = String.format("Ollama request timed out after %s", 
                config.readTimeout());
            logger.error(message, error);
            return new ServiceTimeoutException(message, error);
        }
        
        if (error instanceof WebClientRequestException) {
            String message = String.format("Failed to connect to Ollama server at %s:%d", 
                config.host(), config.port());
            logger.error(message, error);
            return new ServiceUnavailableException(message, error);
        }
        
        if (error instanceof WebClientResponseException webClientError) {
            String message = String.format("Ollama server returned error: %d - %s", 
                webClientError.getStatusCode().value(), 
                webClientError.getResponseBodyAsString());
            logger.error(message, error);
            return new ServiceUnavailableException(message, error);
        }
        
        logger.error("Unexpected error in Ollama client: {}", error.getMessage(), error);
        return error;
    }
}

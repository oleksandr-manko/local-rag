package com.rag.openai.client.ollama;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for interacting with Ollama server.
 * Provides methods for text generation, streaming, and embedding generation.
 */
public interface OllamaClient {
    
    /**
     * Generate text from a prompt using the specified model.
     * 
     * @param prompt The input prompt
     * @param modelName The model to use for generation
     * @return CompletableFuture containing the generated text
     */
    CompletableFuture<String> generate(String prompt, String modelName);
    
    /**
     * Generate text from a prompt with streaming response.
     * 
     * @param prompt The input prompt
     * @param modelName The model to use for generation
     * @return CompletableFuture containing a Flux of streaming tokens
     */
    CompletableFuture<Flux<String>> generateStreaming(String prompt, String modelName);
    
    /**
     * Generate an embedding vector for the given text.
     * 
     * @param text The text to generate an embedding for
     * @param embeddingModelName The embedding model to use
     * @return CompletableFuture containing the embedding vector
     */
    CompletableFuture<List<Float>> generateEmbedding(String text, String embeddingModelName);
    
    /**
     * Analyze an image using a vision-capable model to extract text.
     * 
     * @param imageData The image data as a byte array
     * @param prompt The prompt describing what to extract
     * @param visionModelName The vision model to use
     * @return CompletableFuture containing the extracted text
     */
    CompletableFuture<String> analyzeImage(byte[] imageData, String prompt, String visionModelName);
    
    /**
     * Verify connectivity to the Ollama server.
     * 
     * @return CompletableFuture containing true if connected, false otherwise
     */
    CompletableFuture<Boolean> verifyConnectivity();
}

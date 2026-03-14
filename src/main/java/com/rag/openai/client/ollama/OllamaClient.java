package com.rag.openai.client.ollama;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for interacting with Ollama server.
 * Provides methods for text generation, streaming, and embedding generation.
 * Model names are resolved from configuration internally.
 */
public interface OllamaClient {
    
    /**
     * Generate text from a prompt using the configured model.
     * 
     * @param prompt The input prompt
     * @return CompletableFuture containing the generated text
     */
    CompletableFuture<String> generate(String prompt);
    
    /**
     * Generate text from a prompt with streaming response using the configured model.
     * 
     * @param prompt The input prompt
     * @return CompletableFuture containing a Flux of streaming tokens
     */
    CompletableFuture<Flux<String>> generateStreaming(String prompt);
    
    /**
     * Generate an embedding vector for the given text using the configured embedding model.
     * 
     * @param text The text to generate an embedding for
     * @return CompletableFuture containing the embedding vector
     */
    CompletableFuture<List<Float>> generateEmbedding(String text);
    
    /**
     * Analyze an image using the configured vision model to extract text.
     * 
     * @param imageData The image data as a byte array
     * @param prompt The prompt describing what to extract
     * @return CompletableFuture containing the extracted text
     */
    CompletableFuture<String> analyzeImage(byte[] imageData, String prompt);
    
    /**
     * Verify connectivity to the Ollama server.
     * 
     * @return CompletableFuture containing true if connected, false otherwise
     */
    CompletableFuture<Boolean> verifyConnectivity();
}

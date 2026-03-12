package com.rag.openai.service;

import com.rag.openai.domain.dto.ChatCompletionChunk;
import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.ChatCompletionResponse;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for handling RAG query operations.
 * Orchestrates embedding generation, vector search, prompt augmentation, and response generation.
 */
public interface QueryHandler {
    
    /**
     * Handle a non-streaming chat completion request using RAG.
     * 
     * @param request The chat completion request
     * @return CompletableFuture containing the chat completion response
     */
    CompletableFuture<ChatCompletionResponse> handleQuery(ChatCompletionRequest request);
    
    /**
     * Handle a streaming chat completion request using RAG.
     * 
     * @param request The chat completion request
     * @return CompletableFuture containing a Flux of streaming chunks
     */
    CompletableFuture<Flux<ChatCompletionChunk>> handleStreamingQuery(ChatCompletionRequest request);
}

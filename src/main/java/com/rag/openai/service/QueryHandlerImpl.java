package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.config.RagConfig;
import com.rag.openai.domain.dto.*;
import com.rag.openai.domain.model.ScoredChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Implementation of QueryHandler that orchestrates RAG operations.
 * Uses functional composition with CompletableFuture for asynchronous processing.
 */
@Service
public class QueryHandlerImpl implements QueryHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(QueryHandlerImpl.class);
    
    private final OllamaClient ollamaClient;
    private final VectorStoreClient vectorStoreClient;
    private final RagConfig ragConfig;
    
    public QueryHandlerImpl(
        OllamaClient ollamaClient,
        VectorStoreClient vectorStoreClient,
        RagConfig ragConfig
    ) {
        this.ollamaClient = ollamaClient;
        this.vectorStoreClient = vectorStoreClient;
        this.ragConfig = ragConfig;
    }
    
    @Override
    public CompletableFuture<ChatCompletionResponse> handleQuery(ChatCompletionRequest request) {
        logger.debug("Handling non-streaming query for model: {}", request.model());
        
        String completionId = generateCompletionId();
        long createdTimestamp = System.currentTimeMillis() / 1000;
        
        return extractUserPrompt(request)
            .thenCompose(this::generateQueryEmbedding)
            .thenCompose(this::searchSimilarChunks)
            .thenCompose(chunks -> augmentPrompt(extractUserPrompt(request).join(), chunks))
            .thenCompose(augmentedPrompt -> generateResponse(augmentedPrompt))
            .thenApply(responseText -> formatResponse(
                completionId,
                createdTimestamp,
                request.model(),
                responseText
            ))
            .exceptionally(ex -> {
                logger.error("Error handling query", ex);
                throw new RuntimeException("Failed to process query", ex);
            });
    }
    
    @Override
    public CompletableFuture<Flux<ChatCompletionChunk>> handleStreamingQuery(ChatCompletionRequest request) {
        logger.debug("Handling streaming query for model: {}", request.model());
        
        String completionId = generateCompletionId();
        long createdTimestamp = System.currentTimeMillis() / 1000;
        
        return extractUserPrompt(request)
            .thenCompose(this::generateQueryEmbedding)
            .thenCompose(this::searchSimilarChunks)
            .thenCompose(chunks -> augmentPrompt(extractUserPrompt(request).join(), chunks))
            .thenCompose(augmentedPrompt -> generateStreamingResponse(augmentedPrompt))
            .thenApply(tokenFlux -> formatStreamingResponse(
                completionId,
                createdTimestamp,
                request.model(),
                tokenFlux
            ))
            .exceptionally(ex -> {
                logger.error("Error handling streaming query", ex);
                throw new RuntimeException("Failed to process streaming query", ex);
            });
    }
    
    /**
     * Extract the user prompt from the last user message in the request.
     */
    private CompletableFuture<String> extractUserPrompt(ChatCompletionRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<String> userPrompt = request.messages().stream()
                .filter(msg -> "user".equals(msg.role()))
                .reduce((first, second) -> second)
                .map(Message::content);
            
            return userPrompt.orElseThrow(() -> 
                new IllegalArgumentException("No user message found in request")
            );
        });
    }
    
    /**
     * Generate an embedding for the query using the embedding model.
     */
    private CompletableFuture<List<Float>> generateQueryEmbedding(String query) {
        logger.debug("Generating embedding for query");
        return ollamaClient.generateEmbedding(query);
    }
    
    /**
     * Search for similar chunks in the vector store.
     */
    private CompletableFuture<List<ScoredChunk>> searchSimilarChunks(List<Float> queryEmbedding) {
        logger.debug("Searching for top {} similar chunks", ragConfig.topKResults());
        return vectorStoreClient.searchSimilar(queryEmbedding, ragConfig.topKResults())
            .thenApply(chunks -> {
                logger.debug("Found {} similar chunks", chunks.size());
                return chunks;
            });
    }
    
    /**
     * Augment the user prompt with relevant context from retrieved chunks.
     * If no relevant chunks are found, return the original prompt.
     */
    private CompletableFuture<String> augmentPrompt(String originalPrompt, List<ScoredChunk> chunks) {
        return CompletableFuture.supplyAsync(() -> {
            if (chunks.isEmpty()) {
                logger.debug("No relevant chunks found, using original prompt");
                return originalPrompt;
            }
            
            // Filter chunks by similarity threshold
            List<ScoredChunk> relevantChunks = chunks.stream()
                .filter(chunk -> chunk.score() >= ragConfig.similarityThreshold())
                .toList();
            
            if (relevantChunks.isEmpty()) {
                logger.debug("No chunks above similarity threshold, using original prompt");
                return originalPrompt;
            }
            
            // Build context from relevant chunks
            String context = relevantChunks.stream()
                .map(scoredChunk -> scoredChunk.chunk().text())
                .collect(Collectors.joining(ragConfig.contextSeparator()));
            
            // Apply prompt template
            String augmentedPrompt = ragConfig.promptTemplate()
                .replace("{context}", context)
                .replace("{question}", originalPrompt);
            
            logger.debug("Augmented prompt with {} relevant chunks", relevantChunks.size());
            return augmentedPrompt;
        });
    }
    
    /**
     * Generate a response from Ollama using the augmented prompt.
     */
    private CompletableFuture<String> generateResponse(String prompt) {
        logger.debug("Generating response from Ollama");
        return ollamaClient.generate(prompt);
    }
    
    /**
     * Generate a streaming response from Ollama using the augmented prompt.
     */
    private CompletableFuture<Flux<String>> generateStreamingResponse(String prompt) {
        logger.debug("Generating streaming response from Ollama");
        return ollamaClient.generateStreaming(prompt);
    }
    
    /**
     * Format the response according to OpenAI specification.
     */
    private ChatCompletionResponse formatResponse(
        String id,
        long created,
        String model,
        String responseText
    ) {
        Message assistantMessage = new Message("assistant", responseText);
        Choice choice = new Choice(0, assistantMessage, "stop");
        
        // Estimate token counts (simplified)
        int promptTokens = estimateTokenCount(responseText);
        int completionTokens = estimateTokenCount(responseText);
        Usage usage = new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
        
        return new ChatCompletionResponse(
            id,
            "chat.completion",
            created,
            model,
            List.of(choice),
            usage
        );
    }
    
    /**
     * Format streaming response according to OpenAI specification.
     * Forwards tokens from Ollama to client as they arrive.
     * Handles streaming errors by logging and propagating the error.
     */
    private Flux<ChatCompletionChunk> formatStreamingResponse(
        String id,
        long created,
        String model,
        Flux<String> tokenFlux
    ) {
        AtomicInteger tokenCount = new AtomicInteger(0);
        
        return tokenFlux
            .doOnNext(token -> logger.trace("Forwarding streaming token: {}", token))
            .map(token -> {
                tokenCount.incrementAndGet();
                Delta delta = new Delta(
                    tokenCount.get() == 1 ? Optional.of("assistant") : Optional.empty(),
                    Optional.of(token)
                );
                ChunkChoice choice = new ChunkChoice(0, delta, null);
                return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(choice));
            })
            .concatWith(Flux.just(createFinalChunk(id, created, model)))
            .doOnComplete(() -> logger.debug("Streaming response completed for id: {}", id))
            .doOnError(error -> logger.error("Error during streaming for id: {}", id, error));
    }
    
    /**
     * Create the final chunk with finish_reason set to "stop".
     */
    private ChatCompletionChunk createFinalChunk(String id, long created, String model) {
        Delta emptyDelta = new Delta(Optional.empty(), Optional.empty());
        ChunkChoice finalChoice = new ChunkChoice(0, emptyDelta, "stop");
        return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(finalChoice));
    }
    
    /**
     * Generate a unique completion ID.
     */
    private String generateCompletionId() {
        return "chatcmpl-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }
    
    /**
     * Estimate token count (simplified approximation).
     * In production, use a proper tokenizer.
     */
    private int estimateTokenCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // Rough approximation: 1 token ≈ 4 characters
        return (int) Math.ceil(text.length() / 4.0);
    }
}

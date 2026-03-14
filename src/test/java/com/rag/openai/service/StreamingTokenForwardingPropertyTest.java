package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;

import com.rag.openai.config.RagConfig;
import com.rag.openai.domain.dto.*;
import com.rag.openai.domain.model.ScoredChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for streaming token forwarding in QueryHandler.
 * **Validates: Requirement 11.3**
 * 
 * Property 24: Streaming Token Forwarding
 * 
 * This test suite verifies that the QueryHandler correctly forwards streaming tokens
 * from Ollama to the client as they are received:
 * - Each token from Ollama is immediately forwarded as a ChatCompletionChunk
 * - Tokens are forwarded in the same order they are received
 * - The first chunk includes the role "assistant"
 * - Subsequent chunks contain only content deltas
 * - A final chunk with finish_reason="stop" is sent after all tokens
 * - Token count matches the number of tokens received from Ollama
 */
class StreamingTokenForwardingPropertyTest {

    @Property(tries = 100)
    @Label("When Ollama streams N tokens Then QueryHandler forwards N chunks plus final chunk")
    void allTokensAreForwardedAsChunks(
            @ForAll @IntRange(min = 1, max = 50) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of N tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "token" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler with mock dependencies
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        CompletableFuture<Flux<ChatCompletionChunk>> resultFuture = queryHandler.handleStreamingQuery(request);
        Flux<ChatCompletionChunk> chunkFlux = resultFuture.join();
        
        // Then: should receive N token chunks + 1 final chunk
        AtomicInteger chunkCount = new AtomicInteger(0);
        StepVerifier.create(chunkFlux)
                .expectNextCount(tokenCount + 1) // N tokens + final chunk
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        
        // Then: verify Ollama streaming was called
        verify(mockOllamaClient, times(1)).generateStreaming(anyString());
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then first chunk contains role assistant")
    void firstChunkContainsRole(
            @ForAll @IntRange(min = 1, max = 20) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "word" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: first chunk should contain role "assistant"
        StepVerifier.create(chunkFlux)
                .assertNext(firstChunk -> {
                    assertThat(firstChunk.choices()).isNotEmpty();
                    ChunkChoice firstChoice = firstChunk.choices().get(0);
                    assertThat(firstChoice.delta().role()).isPresent();
                    assertThat(firstChoice.delta().role().get()).isEqualTo("assistant");
                    assertThat(firstChoice.delta().content()).isPresent();
                })
                .expectNextCount(tokenCount) // remaining tokens + final chunk
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then subsequent chunks contain only content")
    void subsequentChunksContainOnlyContent(
            @ForAll @IntRange(min = 2, max = 20) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "chunk" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: chunks 2 through N should have no role, only content
        StepVerifier.create(chunkFlux)
                .expectNextCount(1) // skip first chunk
                .thenConsumeWhile(
                        chunk -> {
                            // Check all chunks except the final one
                            if (chunk.choices().get(0).finishReason() != null) {
                                return true; // final chunk, stop checking
                            }
                            // Subsequent token chunks should have no role
                            assertThat(chunk.choices().get(0).delta().role()).isEmpty();
                            assertThat(chunk.choices().get(0).delta().content()).isPresent();
                            return true;
                        }
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then final chunk has finish_reason stop")
    void finalChunkHasFinishReasonStop(
            @ForAll @IntRange(min = 1, max = 20) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "data" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: last chunk should have finish_reason="stop"
        StepVerifier.create(chunkFlux)
                .expectNextCount(tokenCount) // skip all token chunks
                .assertNext(finalChunk -> {
                    assertThat(finalChunk.choices()).isNotEmpty();
                    ChunkChoice finalChoice = finalChunk.choices().get(0);
                    assertThat(finalChoice.finishReason()).isEqualTo("stop");
                    assertThat(finalChoice.delta().role()).isEmpty();
                    assertThat(finalChoice.delta().content()).isEmpty();
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then tokens are forwarded in order")
    void tokensAreForwardedInOrder(
            @ForAll @IntRange(min = 3, max = 15) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of uniquely identifiable tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "TOKEN_" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: tokens should appear in the same order
        AtomicInteger tokenIndex = new AtomicInteger(0);
        StepVerifier.create(chunkFlux)
                .thenConsumeWhile(
                        chunk -> {
                            ChunkChoice choice = chunk.choices().get(0);
                            // Skip final chunk
                            if (choice.finishReason() != null) {
                                return true;
                            }
                            // Verify token order
                            if (choice.delta().content().isPresent()) {
                                String content = choice.delta().content().get();
                                int expectedIndex = tokenIndex.getAndIncrement();
                                assertThat(content).isEqualTo("TOKEN_" + expectedIndex);
                            }
                            return true;
                        }
                )
                .expectComplete()
                .verify(Duration.ofSeconds(5));
        
        // Then: all tokens should have been received
        assertThat(tokenIndex.get()).isEqualTo(tokenCount);
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then each chunk has same id and created timestamp")
    void allChunksHaveSameIdAndTimestamp(
            @ForAll @IntRange(min = 2, max = 10) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "part" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: all chunks should have the same id and created timestamp
        String[] capturedId = new String[1];
        long[] capturedCreated = new long[1];
        
        StepVerifier.create(chunkFlux)
                .assertNext(firstChunk -> {
                    capturedId[0] = firstChunk.id();
                    capturedCreated[0] = firstChunk.created();
                    assertThat(firstChunk.id()).isNotNull();
                    assertThat(firstChunk.created()).isGreaterThan(0);
                })
                .thenConsumeWhile(chunk -> {
                    assertThat(chunk.id()).isEqualTo(capturedId[0]);
                    assertThat(chunk.created()).isEqualTo(capturedCreated[0]);
                    assertThat(chunk.model()).isEqualTo(model);
                    assertThat(chunk.object()).isEqualTo("chat.completion.chunk");
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Property(tries = 50)
    @Label("When Ollama stream is empty Then only final chunk is sent")
    void emptyStreamSendsOnlyFinalChunk(
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns an empty stream
        Flux<String> emptyTokenFlux = Flux.empty();
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(emptyTokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: should receive only the final chunk
        StepVerifier.create(chunkFlux)
                .assertNext(finalChunk -> {
                    assertThat(finalChunk.choices()).isNotEmpty();
                    ChunkChoice choice = finalChunk.choices().get(0);
                    assertThat(choice.finishReason()).isEqualTo("stop");
                    assertThat(choice.delta().role()).isEmpty();
                    assertThat(choice.delta().content()).isEmpty();
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }

    @Property(tries = 100)
    @Label("When Ollama streams tokens Then each chunk has valid OpenAI format")
    void allChunksHaveValidOpenAIFormat(
            @ForAll @IntRange(min = 1, max = 20) int tokenCount,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 24: Streaming Token Forwarding
        
        // Given: mock dependencies
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        
        RagConfig ragConfig = new RagConfig(
                5, 0.7, "\n\n---\n\n",
                "Context: {context}\n\nQuestion: {question}"
        );
        
        // Given: Ollama returns a stream of tokens
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = "segment" + i;
        }
        Flux<String> tokenFlux = Flux.fromArray(tokens);
        
        when(mockOllamaClient.generateStreaming(anyString()))
                .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(List.of(0.1f, 0.2f, 0.3f)));
        
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        
        // Given: QueryHandler
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient, mockVectorStoreClient, ragConfig
        );
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(new Message("user", "test prompt")),
                true,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: handling streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: all chunks should have valid OpenAI format
        StepVerifier.create(chunkFlux)
                .thenConsumeWhile(chunk -> {
                    // Validate chunk structure
                    assertThat(chunk.id()).isNotNull().isNotEmpty();
                    assertThat(chunk.object()).isEqualTo("chat.completion.chunk");
                    assertThat(chunk.created()).isGreaterThan(0);
                    assertThat(chunk.model()).isEqualTo(model);
                    assertThat(chunk.choices()).isNotEmpty();
                    assertThat(chunk.choices().size()).isEqualTo(1);
                    
                    // Validate choice structure
                    ChunkChoice choice = chunk.choices().get(0);
                    assertThat(choice.index()).isEqualTo(0);
                    assertThat(choice.delta()).isNotNull();
                    
                    return true;
                })
                .expectComplete()
                .verify(Duration.ofSeconds(5));
    }
}

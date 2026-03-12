package com.rag.openai.api;

import com.rag.openai.config.OllamaConfig;
import com.rag.openai.domain.dto.*;
import com.rag.openai.service.QueryHandler;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for streaming mode selection in OpenAI API controller.
 * **Validates: Requirements 1.4, 1.5, 11.1**
 * 
 * Property 4: Streaming Mode Selection
 * 
 * This test suite verifies that the OpenAIApiController correctly selects between streaming
 * and non-streaming modes based on the request's stream parameter:
 * - When stream=true, uses handleStreamingQuery and returns Server-Sent Events
 * - When stream=false, uses handleQuery and returns JSON response
 * - Response content type matches the selected mode
 * - Both modes handle the same request structure correctly
 */
class StreamingModeSelectionPropertyTest {

    @Property(tries = 100)
    @Label("When request has stream=true Then controller uses streaming mode")
    void streamingRequestUsesStreamingMode(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 4: Streaming Mode Selection
        
        // Given: a request with stream=true
        ChatCompletionRequest request = new ChatCompletionRequest(
                model, messages, true, temperature, maxTokens
        );
        
        // Given: mock QueryHandler that returns streaming response
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        String completionId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 24);
        long created = System.currentTimeMillis() / 1000;
        
        Flux<ChatCompletionChunk> mockChunkFlux = Flux.just(
                createChunk(completionId, created, model, "Hello", true),
                createChunk(completionId, created, model, " world", false),
                createFinalChunk(completionId, created, model)
        );
        
        when(mockQueryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockChunkFlux));
        
        // Given: controller with mock dependencies
        OllamaConfig mockConfig = new OllamaConfig(
                "localhost", 11434, "llama3.2", "nomic-embed-text", "qwen3-vl:8b",
                Duration.ofSeconds(30), Duration.ofSeconds(120)
        );
        OpenAIApiController controller = new OpenAIApiController(mockQueryHandler, mockConfig);
        
        // When: calling chatCompletions endpoint
        CompletableFuture<ResponseEntity<?>> responseFuture = controller.chatCompletions(request);
        ResponseEntity<?> response = responseFuture.join();
        
        // Then: handleStreamingQuery should be called
        verify(mockQueryHandler, times(1)).handleStreamingQuery(any(ChatCompletionRequest.class));
        verify(mockQueryHandler, never()).handleQuery(any(ChatCompletionRequest.class));
        
        // Then: response should have streaming content type
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        
        // Then: response body should be a Flux of ServerSentEvents
        assertThat(response.getBody()).isInstanceOf(Flux.class);
    }

    @Property(tries = 100)
    @Label("When request has stream=false Then controller uses non-streaming mode")
    void nonStreamingRequestUsesNonStreamingMode(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 4: Streaming Mode Selection
        
        // Given: a request with stream=false
        ChatCompletionRequest request = new ChatCompletionRequest(
                model, messages, false, temperature, maxTokens
        );
        
        // Given: mock QueryHandler that returns non-streaming response
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        String completionId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 24);
        long created = System.currentTimeMillis() / 1000;
        
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
                completionId,
                "chat.completion",
                created,
                model,
                List.of(new Choice(0, new Message("assistant", "Hello world"), "stop")),
                new Usage(10, 20, 30)
        );
        
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // Given: controller with mock dependencies
        OllamaConfig mockConfig = new OllamaConfig(
                "localhost", 11434, "llama3.2", "nomic-embed-text", "qwen3-vl:8b",
                Duration.ofSeconds(30), Duration.ofSeconds(120)
        );
        OpenAIApiController controller = new OpenAIApiController(mockQueryHandler, mockConfig);
        
        // When: calling chatCompletions endpoint
        CompletableFuture<ResponseEntity<?>> responseFuture = controller.chatCompletions(request);
        ResponseEntity<?> response = responseFuture.join();
        
        // Then: handleQuery should be called
        verify(mockQueryHandler, times(1)).handleQuery(any(ChatCompletionRequest.class));
        verify(mockQueryHandler, never()).handleStreamingQuery(any(ChatCompletionRequest.class));
        
        // Then: response should have JSON content type
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        
        // Then: response body should be a ChatCompletionResponse
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        ChatCompletionResponse actualResponse = (ChatCompletionResponse) response.getBody();
        assertThat(actualResponse.id()).isEqualTo(completionId);
        assertThat(actualResponse.object()).isEqualTo("chat.completion");
    }

    @Property(tries = 50)
    @Label("When stream parameter changes Then mode selection changes accordingly")
    void streamParameterControlsModeSelection(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens,
            @ForAll boolean streamMode
    ) {
        // Feature: rag-openai-api-ollama, Property 4: Streaming Mode Selection
        
        // Given: a request with arbitrary stream parameter
        ChatCompletionRequest request = new ChatCompletionRequest(
                model, messages, streamMode, temperature, maxTokens
        );
        
        // Given: mock QueryHandler with both methods configured
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        String completionId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 24);
        long created = System.currentTimeMillis() / 1000;
        
        // Setup streaming response
        Flux<ChatCompletionChunk> mockChunkFlux = Flux.just(
                createChunk(completionId, created, model, "Test", true),
                createFinalChunk(completionId, created, model)
        );
        when(mockQueryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockChunkFlux));
        
        // Setup non-streaming response
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
                completionId,
                "chat.completion",
                created,
                model,
                List.of(new Choice(0, new Message("assistant", "Test"), "stop")),
                new Usage(5, 10, 15)
        );
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // Given: controller with mock dependencies
        OllamaConfig mockConfig = new OllamaConfig(
                "localhost", 11434, "llama3.2", "nomic-embed-text", "qwen3-vl:8b",
                Duration.ofSeconds(30), Duration.ofSeconds(120)
        );
        OpenAIApiController controller = new OpenAIApiController(mockQueryHandler, mockConfig);
        
        // When: calling chatCompletions endpoint
        CompletableFuture<ResponseEntity<?>> responseFuture = controller.chatCompletions(request);
        ResponseEntity<?> response = responseFuture.join();
        
        // Then: correct handler method should be called based on stream parameter
        if (streamMode) {
            verify(mockQueryHandler, times(1)).handleStreamingQuery(any(ChatCompletionRequest.class));
            verify(mockQueryHandler, never()).handleQuery(any(ChatCompletionRequest.class));
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
            assertThat(response.getBody()).isInstanceOf(Flux.class);
        } else {
            verify(mockQueryHandler, times(1)).handleQuery(any(ChatCompletionRequest.class));
            verify(mockQueryHandler, never()).handleStreamingQuery(any(ChatCompletionRequest.class));
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
            assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        }
        
        // Then: response should be successful
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Property(tries = 100)
    @Label("When streaming mode Then response contains SSE with [DONE] marker")
    void streamingModeIncludesDoneMarker(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 4: Streaming Mode Selection
        
        // Given: a streaming request
        ChatCompletionRequest request = new ChatCompletionRequest(
                model, messages, true, temperature, maxTokens
        );
        
        // Given: mock QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        String completionId = "chatcmpl-" + UUID.randomUUID().toString().substring(0, 24);
        long created = System.currentTimeMillis() / 1000;
        
        Flux<ChatCompletionChunk> mockChunkFlux = Flux.just(
                createChunk(completionId, created, model, "Token", true),
                createFinalChunk(completionId, created, model)
        );
        
        when(mockQueryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockChunkFlux));
        
        // Given: controller
        OllamaConfig mockConfig = new OllamaConfig(
                "localhost", 11434, "llama3.2", "nomic-embed-text", "qwen3-vl:8b",
                Duration.ofSeconds(30), Duration.ofSeconds(120)
        );
        OpenAIApiController controller = new OpenAIApiController(mockQueryHandler, mockConfig);
        
        // When: calling chatCompletions endpoint
        CompletableFuture<ResponseEntity<?>> responseFuture = controller.chatCompletions(request);
        ResponseEntity<?> response = responseFuture.join();
        
        // Then: response body should be a Flux
        assertThat(response.getBody()).isInstanceOf(Flux.class);
        
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<ChatCompletionChunk>> sseFlux = (Flux<ServerSentEvent<ChatCompletionChunk>>) response.getBody();
        
        // Then: flux should contain events including [DONE] marker
        List<ServerSentEvent<ChatCompletionChunk>> events = sseFlux.collectList().block();
        assertThat(events).isNotNull();
        assertThat(events).hasSizeGreaterThan(0);
        
        // Then: last event should have [DONE] comment
        ServerSentEvent<ChatCompletionChunk> lastEvent = events.get(events.size() - 1);
        assertThat(lastEvent.comment()).isEqualTo("[DONE]");
    }

    // ==================== Helper Methods ====================

    /**
     * Create a chat completion chunk for testing.
     */
    private ChatCompletionChunk createChunk(String id, long created, String model, String content, boolean includeRole) {
        Delta delta = new Delta(
                includeRole ? Optional.of("assistant") : Optional.empty(),
                Optional.of(content)
        );
        ChunkChoice choice = new ChunkChoice(0, delta, null);
        return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(choice));
    }

    /**
     * Create a final chunk with finish_reason.
     */
    private ChatCompletionChunk createFinalChunk(String id, long created, String model) {
        Delta emptyDelta = new Delta(Optional.empty(), Optional.empty());
        ChunkChoice finalChoice = new ChunkChoice(0, emptyDelta, "stop");
        return new ChatCompletionChunk(id, "chat.completion.chunk", created, model, List.of(finalChoice));
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<List<Message>> messages() {
        return Arbitraries.of("system", "user", "assistant")
                .flatMap(role -> 
                    Arbitraries.strings()
                        .alpha()
                        .ofMinLength(1)
                        .ofMaxLength(100)
                        .map(content -> new Message(role, content))
                )
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);
    }

    @Provide
    Arbitrary<Optional<Double>> optionalTemperature() {
        return Arbitraries.oneOf(
                Arbitraries.just(Optional.empty()),
                Arbitraries.doubles()
                        .between(0.0, 2.0)
                        .map(Optional::of)
        );
    }

    @Provide
    Arbitrary<Optional<Integer>> optionalMaxTokens() {
        return Arbitraries.oneOf(
                Arbitraries.just(Optional.empty()),
                Arbitraries.integers()
                        .between(1, 10000)
                        .map(Optional::of)
        );
    }
}

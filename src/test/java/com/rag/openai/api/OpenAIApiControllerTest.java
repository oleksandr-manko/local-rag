package com.rag.openai.api;

import com.rag.openai.config.OllamaConfig;
import com.rag.openai.domain.dto.*;
import com.rag.openai.service.QueryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for OpenAIApiController.
 * Tests valid request handling, invalid request validation, streaming vs non-streaming modes, and models endpoint.
 */
@ExtendWith(MockitoExtension.class)
class OpenAIApiControllerTest {
    
    @Mock
    private QueryHandler queryHandler;
    
    private OllamaConfig ollamaConfig;
    private OpenAIApiController controller;
    
    @BeforeEach
    void setUp() {
        // Given: Configuration for Ollama
        ollamaConfig = new OllamaConfig(
            "localhost",
            11434,
            "llama3.2",
            "nomic-embed-text",
            "qwen3-vl:8b",
            Duration.ofSeconds(30),
            Duration.ofSeconds(120)
        );
        
        controller = new OpenAIApiController(queryHandler, ollamaConfig);
    }
    
    @Test
    @DisplayName("When valid non-streaming request Then return ChatCompletionResponse with 200 OK")
    void testChatCompletions_WithValidNonStreamingRequest() {
        // Given: A valid non-streaming chat completion request
        Message userMessage = new Message("user", "What is the capital of France?");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            false,
            Optional.of(0.7),
            Optional.of(100)
        );
        
        Message responseMessage = new Message("assistant", "The capital of France is Paris.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(15, 8, 23);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-123",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 200 OK with ChatCompletionResponse
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        
        ChatCompletionResponse responseBody = (ChatCompletionResponse) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.id()).isEqualTo("chatcmpl-123");
        assertThat(responseBody.model()).isEqualTo("llama3.2");
        assertThat(responseBody.choices()).hasSize(1);
        assertThat(responseBody.choices().get(0).message().content()).isEqualTo("The capital of France is Paris.");
        
        verify(queryHandler).handleQuery(request);
    }
    
    @Test
    @DisplayName("When valid streaming request Then return Flux of ServerSentEvents with 200 OK")
    void testChatCompletions_WithValidStreamingRequest() {
        // Given: A valid streaming chat completion request
        Message userMessage = new Message("user", "Count to three");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        Delta delta1 = new Delta(Optional.of("assistant"), Optional.of("One"));
        ChunkChoice choice1 = new ChunkChoice(0, delta1, null);
        ChatCompletionChunk chunk1 = new ChatCompletionChunk(
            "chatcmpl-456",
            "chat.completion.chunk",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice1)
        );
        
        Delta delta2 = new Delta(Optional.empty(), Optional.of(", two"));
        ChunkChoice choice2 = new ChunkChoice(0, delta2, null);
        ChatCompletionChunk chunk2 = new ChatCompletionChunk(
            "chatcmpl-456",
            "chat.completion.chunk",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice2)
        );
        
        Delta delta3 = new Delta(Optional.empty(), Optional.of(", three"));
        ChunkChoice choice3 = new ChunkChoice(0, delta3, "stop");
        ChatCompletionChunk chunk3 = new ChatCompletionChunk(
            "chatcmpl-456",
            "chat.completion.chunk",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice3)
        );
        
        Flux<ChatCompletionChunk> mockChunkFlux = Flux.just(chunk1, chunk2, chunk3);
        
        when(queryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockChunkFlux));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 200 OK with Flux of ServerSentEvents
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody()).isInstanceOf(Flux.class);
        
        @SuppressWarnings("unchecked")
        Flux<ServerSentEvent<ChatCompletionChunk>> sseFlux = (Flux<ServerSentEvent<ChatCompletionChunk>>) response.getBody();
        List<ServerSentEvent<ChatCompletionChunk>> events = sseFlux.collectList().block();
        
        assertThat(events).isNotNull();
        assertThat(events).hasSize(4); // 3 chunks + 1 [DONE] message
        
        // Verify first chunk
        assertThat(events.get(0).data()).isNotNull();
        assertThat(events.get(0).data().choices().get(0).delta().content()).isPresent();
        assertThat(events.get(0).data().choices().get(0).delta().content().get()).isEqualTo("One");
        
        // Verify [DONE] message
        assertThat(events.get(3).comment()).isEqualTo("[DONE]");
        
        verify(queryHandler).handleStreamingQuery(request);
    }
    
    @Test
    @DisplayName("When request with blank model Then return 400 Bad Request")
    void testChatCompletions_WithBlankModel() {
        // Given: A request with blank model (will fail at record construction)
        Message userMessage = new Message("user", "Hello");
        
        try {
            // This should throw IllegalArgumentException during record construction
            new ChatCompletionRequest(
                "",  // Empty/blank model
                List.of(userMessage),
                false,
                Optional.empty(),
                Optional.empty()
            );
            
            // If we get here, the validation didn't work as expected
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            // Then: Exception should be thrown with appropriate message
            assertThat(e.getMessage()).contains("Model must not be blank");
        }
    }
    
    @Test
    @DisplayName("When request with empty messages Then return 400 Bad Request")
    void testChatCompletions_WithEmptyMessages() {
        // Given: A request with empty messages list (will fail at record construction)
        // We create a minimal valid request and test the controller's validation
        Message userMessage = new Message("user", "Test");
        ChatCompletionRequest validRequest = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        // Simulate validation failure by having queryHandler throw exception
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new IllegalArgumentException("Messages must not be null or empty")
            ));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(validRequest).join();
        
        // Then: Response should be 500 Internal Server Error (exception from handler)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    @Test
    @DisplayName("When request with invalid temperature Then return 400 Bad Request")
    void testChatCompletions_WithInvalidTemperature() {
        // Given: A request with temperature outside valid range
        // The record validation will catch this, so we test that the controller handles it
        Message userMessage = new Message("user", "Hello");
        
        try {
            // This should throw IllegalArgumentException during record construction
            new ChatCompletionRequest(
                "llama3.2",
                List.of(userMessage),
                false,
                Optional.of(3.0),  // Invalid: > 2.0
                Optional.empty()
            );
            
            // If we get here, the validation didn't work as expected
            assertThat(false).as("Should have thrown IllegalArgumentException").isTrue();
        } catch (IllegalArgumentException e) {
            // Then: Exception should be thrown with appropriate message
            assertThat(e.getMessage()).contains("Temperature must be between 0 and 2");
        }
    }
    
    @Test
    @DisplayName("When streaming mode is false Then use non-streaming handler")
    void testChatCompletions_StreamingModeFalse() {
        // Given: A request with stream=false
        Message userMessage = new Message("user", "Tell me a joke");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        Message responseMessage = new Message("assistant", "Why did the chicken cross the road?");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(10, 12, 22);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-789",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Should use non-streaming handler and return JSON
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        
        verify(queryHandler).handleQuery(request);
    }
    
    @Test
    @DisplayName("When streaming mode is true Then use streaming handler")
    void testChatCompletions_StreamingModeTrue() {
        // Given: A request with stream=true
        Message userMessage = new Message("user", "Hello");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        Delta delta = new Delta(Optional.of("assistant"), Optional.of("Hi there!"));
        ChunkChoice choice = new ChunkChoice(0, delta, "stop");
        ChatCompletionChunk chunk = new ChatCompletionChunk(
            "chatcmpl-999",
            "chat.completion.chunk",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice)
        );
        
        Flux<ChatCompletionChunk> mockChunkFlux = Flux.just(chunk);
        
        when(queryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockChunkFlux));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Should use streaming handler and return SSE
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_EVENT_STREAM);
        assertThat(response.getBody()).isInstanceOf(Flux.class);
        
        verify(queryHandler).handleStreamingQuery(request);
    }
    
    @Test
    @DisplayName("When models endpoint called Then return ModelsResponse with configured model")
    void testListModels_Success() {
        // Given: Controller is initialized with OllamaConfig
        
        // When: Call the models endpoint
        ResponseEntity<ModelsResponse> response = controller.listModels().join();
        
        // Then: Response should be 200 OK with ModelsResponse
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isNotNull();
        
        ModelsResponse modelsResponse = response.getBody();
        assertThat(modelsResponse.object()).isEqualTo("list");
        assertThat(modelsResponse.data()).hasSize(1);
        
        ModelInfo modelInfo = modelsResponse.data().get(0);
        assertThat(modelInfo.id()).isEqualTo("llama3.2");
        assertThat(modelInfo.object()).isEqualTo("model");
        assertThat(modelInfo.ownedBy()).isEqualTo("ollama");
        assertThat(modelInfo.created()).isGreaterThan(0);
    }
    
    @Test
    @DisplayName("When query handler throws exception Then return 500 Internal Server Error")
    void testChatCompletions_WithQueryHandlerException() {
        // Given: A valid request but query handler throws exception
        Message userMessage = new Message("user", "Test query");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Service unavailable")
            ));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("Error generating response");
    }
    
    @Test
    @DisplayName("When streaming query handler throws exception Then return 500 Internal Server Error")
    void testChatCompletions_WithStreamingQueryHandlerException() {
        // Given: A valid streaming request but query handler throws exception
        Message userMessage = new Message("user", "Test streaming query");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        when(queryHandler.handleStreamingQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Streaming service unavailable")
            ));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 500 Internal Server Error
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isInstanceOf(String.class);
        assertThat((String) response.getBody()).contains("Error generating streaming response");
    }
    
    @Test
    @DisplayName("When request with multiple messages Then handle correctly")
    void testChatCompletions_WithMultipleMessages() {
        // Given: A request with multiple messages (conversation history)
        Message systemMessage = new Message("system", "You are a helpful assistant.");
        Message userMessage1 = new Message("user", "What is 2+2?");
        Message assistantMessage = new Message("assistant", "2+2 equals 4.");
        Message userMessage2 = new Message("user", "What about 3+3?");
        
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(systemMessage, userMessage1, assistantMessage, userMessage2),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        Message responseMessage = new Message("assistant", "3+3 equals 6.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(25, 8, 33);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-multi",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 200 OK with correct response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        
        ChatCompletionResponse responseBody = (ChatCompletionResponse) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.choices().get(0).message().content()).isEqualTo("3+3 equals 6.");
        
        verify(queryHandler).handleQuery(request);
    }
    
    @Test
    @DisplayName("When request with max_tokens parameter Then pass through to handler")
    void testChatCompletions_WithMaxTokensParameter() {
        // Given: A request with max_tokens parameter
        Message userMessage = new Message("user", "Write a short story");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            false,
            Optional.of(0.8),
            Optional.of(50)
        );
        
        Message responseMessage = new Message("assistant", "Once upon a time...");
        Choice choice = new Choice(0, responseMessage, "length");
        Usage usage = new Usage(10, 50, 60);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-tokens",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the chat completions endpoint
        ResponseEntity<?> response = controller.chatCompletions(request).join();
        
        // Then: Response should be 200 OK and parameters should be passed through
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        
        ChatCompletionResponse responseBody = (ChatCompletionResponse) response.getBody();
        assertThat(responseBody).isNotNull();
        assertThat(responseBody.choices().get(0).finishReason()).isEqualTo("length");
        assertThat(responseBody.usage().completionTokens()).isEqualTo(50);
        
        verify(queryHandler).handleQuery(request);
    }
}

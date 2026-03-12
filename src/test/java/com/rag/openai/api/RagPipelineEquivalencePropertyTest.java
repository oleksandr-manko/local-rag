package com.rag.openai.api;

import com.rag.openai.config.OllamaConfig;
import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.ChatCompletionResponse;
import com.rag.openai.domain.dto.Choice;
import com.rag.openai.domain.dto.Message;
import com.rag.openai.domain.dto.Usage;
import com.rag.openai.service.QueryHandler;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for RAG pipeline equivalence between TestApiController and OpenAIApiController.
 * **Validates: Requirements 25.3, 25.4**
 * 
 * Property 27: RAG Pipeline Equivalence
 * 
 * This test suite verifies that both the test endpoint and OpenAI endpoint use the same RAG pipeline:
 * - Both endpoints delegate to the same QueryHandler (Requirement 25.3)
 * - Both endpoints use the same RAG pipeline: embedding generation, vector search, 
 *   prompt augmentation, and response generation (Requirement 25.4)
 * - The only difference is the input/output format (plain text vs OpenAI JSON)
 * - The underlying RAG processing is identical for both endpoints
 */
class RagPipelineEquivalencePropertyTest {

    // ==================== QueryHandler Delegation Tests ====================

    @Property(tries = 100)
    @Label("When both endpoints receive same prompt Then both delegate to QueryHandler")
    void bothEndpointsDelegateToQueryHandler(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: mock OllamaConfig for OpenAI controller
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending same prompt to test endpoint
        testController.testQuery(prompt).join();
        
        // When: sending same prompt to OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: QueryHandler is called exactly twice (once per endpoint)
        verify(mockQueryHandler, times(2)).handleQuery(any(ChatCompletionRequest.class));
    }

    @Property(tries = 100)
    @Label("When both endpoints receive same prompt Then both pass user message with same content to QueryHandler")
    void bothEndpointsPassSamePromptContentToQueryHandler(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: mock OllamaConfig for OpenAI controller
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending same prompt to test endpoint
        testController.testQuery(prompt).join();
        
        // When: sending same prompt to OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: both calls pass the same prompt content to QueryHandler
        verify(mockQueryHandler, times(2)).handleQuery(argThat(request -> {
            // Verify the prompt content matches
            assertThat(request.messages()).isNotEmpty();
            Message lastMessage = request.messages().get(request.messages().size() - 1);
            assertThat(lastMessage.role()).isEqualTo("user");
            assertThat(lastMessage.content()).isEqualTo(prompt);
            return true;
        }));
    }

    // ==================== RAG Pipeline Equivalence Tests ====================

    @Property(tries = 100)
    @Label("When both endpoints process same prompt Then QueryHandler receives equivalent ChatCompletionRequest")
    void bothEndpointsProduceEquivalentChatCompletionRequests(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock that captures requests
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending same prompt to test endpoint
        testController.testQuery(prompt).join();
        
        // When: sending same prompt to OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: both requests have the same essential structure for RAG processing
        // (The RAG pipeline only cares about the user message content, not the model name or streaming flag)
        verify(mockQueryHandler, times(2)).handleQuery(argThat(request -> {
            // Verify messages structure is equivalent
            assertThat(request.messages()).hasSize(1);
            Message message = request.messages().get(0);
            assertThat(message.role()).isEqualTo("user");
            assertThat(message.content()).isEqualTo(prompt);
            
            // Verify non-streaming mode (test endpoint always uses non-streaming)
            // Note: OpenAI endpoint can use streaming, but for this test we use non-streaming
            assertThat(request.stream()).isFalse();
            
            return true;
        }));
    }

    @Property(tries = 100)
    @Label("When both endpoints process same prompt Then both receive same response from QueryHandler")
    void bothEndpointsReceiveSameResponseFromQueryHandler(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String expectedResponse
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a specific response
        ChatCompletionResponse mockResponse = createMockResponse(expectedResponse);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending same prompt to test endpoint
        ResponseEntity<String> testResponse = testController.testQuery(prompt).join();
        
        // When: sending same prompt to OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        ResponseEntity<?> openAIResponse = 
                openAIController.chatCompletions(openAIRequest).join();
        
        // Then: both endpoints receive the same underlying response from QueryHandler
        // Test endpoint extracts plain text
        assertThat(testResponse.getBody()).isEqualTo(expectedResponse);
        
        // OpenAI endpoint returns full ChatCompletionResponse
        assertThat(openAIResponse.getBody()).isNotNull();
        assertThat(openAIResponse.getBody()).isInstanceOf(ChatCompletionResponse.class);
        ChatCompletionResponse openAIBody = (ChatCompletionResponse) openAIResponse.getBody();
        assertThat(openAIBody.choices()).isNotEmpty();
        assertThat(openAIBody.choices().get(0).message().content())
                .isEqualTo(expectedResponse);
    }

    // ==================== RAG Pipeline Component Equivalence Tests ====================

    @Property(tries = 50)
    @Label("When both endpoints process prompt Then same embedding generation occurs in QueryHandler")
    void bothEndpointsTriggerSameEmbeddingGeneration(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: processing through test endpoint
        testController.testQuery(prompt).join();
        
        // When: processing through OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: QueryHandler.handleQuery is called with the same prompt content
        // This ensures the same embedding generation, vector search, and prompt augmentation
        // occurs in the RAG pipeline for both endpoints
        verify(mockQueryHandler, times(2)).handleQuery(argThat(request -> {
            String userPrompt = request.messages().stream()
                    .filter(msg -> "user".equals(msg.role()))
                    .map(Message::content)
                    .findFirst()
                    .orElse("");
            return prompt.equals(userPrompt);
        }));
    }

    @Property(tries = 50)
    @Label("When both endpoints process prompt Then same vector search occurs in QueryHandler")
    void bothEndpointsTriggerSameVectorSearch(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: processing through test endpoint
        testController.testQuery(prompt).join();
        
        // When: processing through OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: QueryHandler is called twice with the same prompt
        // This ensures the same vector search (top-K retrieval) occurs for both endpoints
        verify(mockQueryHandler, times(2)).handleQuery(argThat(request -> {
            // Both requests should have the same user message content
            // which triggers the same vector search in the RAG pipeline
            return request.messages().stream()
                    .anyMatch(msg -> "user".equals(msg.role()) && prompt.equals(msg.content()));
        }));
    }

    @Property(tries = 50)
    @Label("When both endpoints process prompt Then same prompt augmentation occurs in QueryHandler")
    void bothEndpointsTriggerSamePromptAugmentation(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: processing through test endpoint
        testController.testQuery(prompt).join();
        
        // When: processing through OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        openAIController.chatCompletions(openAIRequest).join();
        
        // Then: QueryHandler is called twice with identical user prompts
        // This ensures the same prompt augmentation (context injection) occurs
        verify(mockQueryHandler, times(2)).handleQuery(argThat(request -> {
            // Verify the user message content is identical
            // which ensures the same context retrieval and augmentation
            List<Message> userMessages = request.messages().stream()
                    .filter(msg -> "user".equals(msg.role()))
                    .toList();
            return userMessages.size() == 1 && prompt.equals(userMessages.get(0).content());
        }));
    }

    // ==================== Error Handling Equivalence Tests ====================

    @Property(tries = 50)
    @Label("When QueryHandler fails Then both endpoints handle error appropriately")
    void bothEndpointsHandleQueryHandlerFailures(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 27: RAG Pipeline Equivalence
        
        // Given: shared QueryHandler mock that fails
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        RuntimeException testException = new RuntimeException("RAG pipeline error");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(testException));
        
        // Given: both controllers using the same QueryHandler
        TestApiController testController = new TestApiController(mockQueryHandler);
        OllamaConfig mockOllamaConfig = createMockOllamaConfig();
        OpenAIApiController openAIController = new OpenAIApiController(mockQueryHandler, mockOllamaConfig);
        
        // When: processing through test endpoint
        ResponseEntity<String> testResponse = testController.testQuery(prompt).join();
        
        // When: processing through OpenAI endpoint
        ChatCompletionRequest openAIRequest = createOpenAIRequest(prompt);
        ResponseEntity<?> openAIResponse = openAIController.chatCompletions(openAIRequest).join();
        
        // Then: both endpoints return error responses (though in different formats)
        assertThat(testResponse.getStatusCode().is5xxServerError()).isTrue();
        assertThat(openAIResponse.getStatusCode().is5xxServerError()).isTrue();
        
        // Then: both endpoints called the same QueryHandler
        verify(mockQueryHandler, times(2)).handleQuery(any(ChatCompletionRequest.class));
    }

    // ==================== Helper Methods ====================

    /**
     * Create a mock ChatCompletionResponse with the given content.
     */
    private ChatCompletionResponse createMockResponse(String content) {
        Message assistantMessage = new Message("assistant", content);
        Choice choice = new Choice(0, assistantMessage, "stop");
        Usage usage = new Usage(10, 20, 30);
        
        return new ChatCompletionResponse(
                "chatcmpl-123",
                "chat.completion",
                System.currentTimeMillis() / 1000,
                "default",
                List.of(choice),
                usage
        );
    }

    /**
     * Create a ChatCompletionRequest for the OpenAI endpoint.
     */
    private ChatCompletionRequest createOpenAIRequest(String prompt) {
        Message userMessage = new Message("user", prompt);
        return new ChatCompletionRequest(
                "default",
                List.of(userMessage),
                false,  // Non-streaming for equivalence testing
                Optional.empty(),
                Optional.empty()
        );
    }
    
    /**
     * Create a mock OllamaConfig for testing.
     */
    private OllamaConfig createMockOllamaConfig() {
        return new OllamaConfig(
                "localhost",
                11434,
                "llama3.2",
                "nomic-embed-text",
                "qwen3-vl:8b",
                Duration.ofSeconds(30),
                Duration.ofSeconds(120)
        );
    }
}

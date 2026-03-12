package com.rag.openai.api;

import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.ChatCompletionResponse;
import com.rag.openai.domain.dto.Choice;
import com.rag.openai.domain.dto.Message;
import com.rag.openai.domain.dto.Usage;
import com.rag.openai.service.QueryHandler;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for plain text request handling in TestApiController.
 * **Validates: Requirements 25.2, 25.6**
 * 
 * Property 26: Plain Text Request Handling
 * 
 * This test suite verifies that the TestApiController correctly handles plain text requests:
 * - Accepts plain text prompts in the request body (Requirement 25.2)
 * - Uses Content-Type text/plain for both request and response (Requirement 25.6)
 * - Properly converts plain text to ChatCompletionRequest format
 * - Extracts plain text from ChatCompletionResponse format
 * - Handles null and empty prompts appropriately
 */
class PlainTextRequestHandlingPropertyTest {

    // ==================== Plain Text Acceptance Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController receives non-empty plain text prompt Then accepts and processes it")
    void controllerAcceptsPlainTextPrompt(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String plainTextPrompt
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(plainTextPrompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: controller accepts the plain text prompt
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        
        // Then: QueryHandler is called with converted ChatCompletionRequest
        verify(mockQueryHandler, times(1)).handleQuery(any(ChatCompletionRequest.class));
    }

    @Property(tries = 100)
    @Label("When TestApiController receives plain text prompt Then converts to ChatCompletionRequest with user message")
    void controllerConvertsPlainTextToChatCompletionRequest(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String plainTextPrompt
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        controller.testQuery(plainTextPrompt).join();
        
        // Then: QueryHandler receives ChatCompletionRequest with correct structure
        verify(mockQueryHandler).handleQuery(argThat(request -> {
            // Verify messages list contains exactly one user message
            assertThat(request.messages()).hasSize(1);
            Message message = request.messages().get(0);
            assertThat(message.role()).isEqualTo("user");
            assertThat(message.content()).isEqualTo(plainTextPrompt);
            
            // Verify non-streaming mode
            assertThat(request.stream()).isFalse();
            
            return true;
        }));
    }

    // ==================== Content-Type Validation Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController returns response Then uses Content-Type text/plain")
    void controllerReturnsTextPlainContentType(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String plainTextPrompt
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns a successful response
        ChatCompletionResponse mockResponse = createMockResponse("Test response");
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(plainTextPrompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: response Content-Type is text/plain (Requirement 25.6)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Property(tries = 100)
    @Label("When TestApiController returns error response Then uses Content-Type text/plain")
    void controllerReturnsTextPlainContentTypeForErrors(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String plainTextPrompt
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws an exception
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Service unavailable")));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(plainTextPrompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: error response Content-Type is text/plain (Requirement 25.6)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
    }

    // ==================== Plain Text Response Extraction Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController receives ChatCompletionResponse Then extracts plain text content")
    void controllerExtractsPlainTextFromResponse(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String plainTextPrompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String expectedResponse
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with specific content
        ChatCompletionResponse mockResponse = createMockResponse(expectedResponse);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(plainTextPrompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: response body contains only the plain text content (no JSON formatting)
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        
        // Then: response is plain text, not a JSON object with OpenAI structure
        // (We verify by checking it equals the expected response exactly,
        // without additional JSON wrapping like {"choices": [...]})
    }

    // ==================== Null and Empty Prompt Handling Tests ====================

    @Property(tries = 50)
    @Label("When TestApiController receives null prompt Then returns bad request with text/plain error")
    void controllerRejectsNullPrompt() {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // When: sending null prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(null);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns bad request status
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        
        // Then: response Content-Type is text/plain
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).contains("Error");
        assertThat(response.getBody()).contains("must not be null or empty");
        
        // Then: QueryHandler is never called
        verify(mockQueryHandler, never()).handleQuery(any());
    }

    @Property(tries = 50)
    @Label("When TestApiController receives blank prompt Then returns bad request with text/plain error")
    void controllerRejectsBlankPrompt(
            @ForAll("blankStrings") String blankPrompt
    ) {
        // Feature: rag-openai-api-ollama, Property 26: Plain Text Request Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // When: sending blank prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(blankPrompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns bad request status
        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        
        // Then: response Content-Type is text/plain
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).contains("Error");
        assertThat(response.getBody()).contains("must not be null or empty");
        
        // Then: QueryHandler is never called
        verify(mockQueryHandler, never()).handleQuery(any());
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

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t\n  ", "     ");
    }
}

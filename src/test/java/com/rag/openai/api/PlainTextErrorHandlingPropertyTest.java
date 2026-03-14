package com.rag.openai.api;

import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.exception.ServiceTimeoutException;
import com.rag.openai.exception.ServiceUnavailableException;
import com.rag.openai.service.QueryHandler;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for plain text error handling in TestApiController.
 * **Validates: Requirement 25.7**
 * 
 * Property 29: Plain Text Error Handling
 * 
 * This test suite verifies that the TestApiController handles errors correctly:
 * - Returns error messages as plain text without JSON formatting (Requirement 25.7)
 * - Uses appropriate HTTP status codes for different error types (Requirement 25.7)
 * - Uses Content-Type text/plain for error responses (Requirement 25.6, 25.7)
 * - Handles service unavailable errors with 500 status
 * - Handles timeout errors with 500 status
 * - Handles generic exceptions with 500 status
 * - Error messages are human-readable and informative
 */
class PlainTextErrorHandlingPropertyTest {

    // ==================== Service Unavailable Error Tests ====================

    @Property(tries = 100)
    @Label("When QueryHandler throws ServiceUnavailableException Then returns plain text error with 500 status")
    void serviceUnavailableReturnsPlainTextError(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll("safeErrorMessages") String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws ServiceUnavailableException
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new ServiceUnavailableException(errorMessage)));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns 500 Internal Server Error status (Requirement 25.7)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Then: response Content-Type is text/plain (Requirement 25.7)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains(errorMessage);
        
        // Then: response body does NOT contain JSON structure patterns
        assertThat(response.getBody()).doesNotContain("\"error\":");
        assertThat(response.getBody()).doesNotContain("\"message\":");
        assertThat(response.getBody()).doesNotContain("\"type\":");
    }

    @Property(tries = 100)
    @Label("When QueryHandler throws ServiceTimeoutException Then returns plain text error with 500 status")
    void serviceTimeoutReturnsPlainTextError(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws ServiceTimeoutException
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new ServiceTimeoutException(errorMessage)));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns 500 Internal Server Error status (Requirement 25.7)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Then: response Content-Type is text/plain (Requirement 25.7)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains(errorMessage);
        
        // Then: response body does NOT contain JSON formatting
        assertThat(response.getBody()).doesNotContain("{");
        assertThat(response.getBody()).doesNotContain("}");
    }

    // ==================== Generic Exception Error Tests ====================

    @Property(tries = 100)
    @Label("When QueryHandler throws RuntimeException Then returns plain text error with 500 status")
    void runtimeExceptionReturnsPlainTextError(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws generic RuntimeException
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException(errorMessage)));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns 500 Internal Server Error status (Requirement 25.7)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Then: response Content-Type is text/plain (Requirement 25.7)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains(errorMessage);
    }

    @Property(tries = 100)
    @Label("When QueryHandler throws CompletionException Then returns plain text error with 500 status")
    void completionExceptionReturnsPlainTextError(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws CompletionException wrapping another exception
        RuntimeException cause = new RuntimeException(errorMessage);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new CompletionException(cause)));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: returns 500 Internal Server Error status (Requirement 25.7)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Then: response Content-Type is text/plain (Requirement 25.7)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response body contains error message as plain text
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("Error processing request");
        
        // Then: error message includes the cause message
        assertThat(response.getBody()).contains(errorMessage);
    }

    // ==================== Error Message Format Tests ====================

    @Property(tries = 100)
    @Label("When error occurs Then error message is human-readable plain text")
    void errorMessageIsHumanReadable(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll("commonErrorMessages") String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws exception with specific error message
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException(errorMessage)));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: error message is plain text without technical formatting
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).isInstanceOf(String.class);
        
        // Then: error message starts with "Error" prefix for clarity
        assertThat(response.getBody()).startsWith("Error");
        
        // Then: error message contains the original error information
        assertThat(response.getBody()).contains(errorMessage);
        
        // Then: error message does NOT contain stack traces or technical details
        assertThat(response.getBody()).doesNotContain("at com.rag.openai");
        assertThat(response.getBody()).doesNotContain("Exception in thread");
        assertThat(response.getBody()).doesNotContain(".java:");
    }

    @Property(tries = 100)
    @Label("When error occurs Then error response does not contain JSON structure")
    void errorResponseIsNotJson(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll("safeErrorMessages") String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws exception
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException(errorMessage)));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: error response does NOT contain JSON structure (Requirement 25.7)
        assertThat(response.getBody()).doesNotContain("{");
        assertThat(response.getBody()).doesNotContain("}");
        assertThat(response.getBody()).doesNotContain("[");
        assertThat(response.getBody()).doesNotContain("]");
        
        // Then: error response does NOT contain JSON field names
        assertThat(response.getBody()).doesNotContain("\"error\":");
        assertThat(response.getBody()).doesNotContain("\"message\":");
        assertThat(response.getBody()).doesNotContain("\"status\":");
        assertThat(response.getBody()).doesNotContain("\"timestamp\":");
    }

    // ==================== HTTP Status Code Tests ====================

    @Property(tries = 100)
    @Label("When different errors occur Then appropriate HTTP status codes are returned")
    void appropriateHttpStatusCodesForErrors(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll("errorTypes") Exception exception
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws specific exception type
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(exception));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: returns appropriate HTTP status code (Requirement 25.7)
        // All errors return 500 for simplicity in test endpoint
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // Then: response is always plain text regardless of error type
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
    }

    // ==================== Error Consistency Tests ====================

    @Property(tries = 100)
    @Label("When multiple errors occur Then all error responses are consistently plain text")
    void allErrorResponsesAreConsistentlyPlainText(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt1,
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt2,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String error1,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String error2
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws different exceptions for different requests
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException(error1)))
                .thenReturn(CompletableFuture.failedFuture(new ServiceUnavailableException(error2)));
        
        // When: sending first prompt
        ResponseEntity<String> response1 = controller.testQuery(prompt1).join();
        
        // When: sending second prompt
        ResponseEntity<String> response2 = controller.testQuery(prompt2).join();
        
        // Then: both error responses are plain text
        assertThat(response1.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response2.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: both error responses contain error messages
        assertThat(response1.getBody()).contains("Error");
        assertThat(response2.getBody()).contains("Error");
        
        // Then: both error responses are 500 status
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Property(tries = 50)
    @Label("When error message contains special characters Then plain text format preserves them")
    void errorMessagePreservesSpecialCharacters(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt,
            @ForAll("errorMessagesWithSpecialCharacters") String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws exception with special characters in message
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException(errorMessage)));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: error message preserves special characters
        assertThat(response.getBody()).contains(errorMessage);
        
        // Then: special characters are not escaped or encoded
        if (errorMessage.contains("\"")) {
            assertThat(response.getBody()).contains("\"");
            assertThat(response.getBody()).doesNotContain("\\\"");
        }
    }

    // ==================== Comparison with OpenAI Error Format Tests ====================

    @Property(tries = 50)
    @Label("When comparing with OpenAI error format Then test endpoint error is simpler")
    void testEndpointErrorIsSimpler(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt,
            @ForAll @NotBlank @StringLength(min = 10, max = 200) String errorMessage
    ) {
        // Feature: rag-openai-api-ollama, Property 29: Plain Text Error Handling
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws exception
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException(errorMessage)));
        
        // When: getting error response from test endpoint
        ResponseEntity<String> testResponse = controller.testQuery(prompt).join();
        
        // Then: test endpoint error is plain text
        assertThat(testResponse.getBody()).isNotNull();
        assertThat(testResponse.getBody()).contains("Error");
        assertThat(testResponse.getBody()).contains(errorMessage);
        
        // Then: test endpoint error is simpler than OpenAI format
        // OpenAI error format would include: {"error": {"message": "...", "type": "...", "code": "..."}}
        // Test endpoint format is just: "Error processing request: ..."
        assertThat(testResponse.getBody()).doesNotContain("\"error\"");
        assertThat(testResponse.getBody()).doesNotContain("\"type\"");
        assertThat(testResponse.getBody()).doesNotContain("\"code\"");
        
        // Then: OpenAI error would be JSON, test endpoint error is plain text
        String openAIErrorJson = String.format(
            "{\"error\":{\"message\":\"%s\",\"type\":\"server_error\",\"code\":\"internal_error\"}}",
            errorMessage
        );
        assertThat(testResponse.getBody().length()).isLessThan(openAIErrorJson.length());
    }

    // ==================== Helper Methods ====================

    // (No helper methods needed for this test class)

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> safeErrorMessages() {
        return Arbitraries.strings()
            .alpha()
            .numeric()
            .whitespace()
            .withChars(' ', '.', ',', '!', '?', '-', '_', ':', ';')
            .ofMinLength(10)
            .ofMaxLength(200);
    }

    @Provide
    Arbitrary<String> commonErrorMessages() {
        return Arbitraries.of(
            "Service unavailable",
            "Connection timeout",
            "Failed to connect to Ollama",
            "Failed to connect to Qdrant",
            "Embedding generation failed",
            "Vector search failed",
            "No response from LLM",
            "Request processing timeout",
            "Internal server error",
            "Database connection failed"
        );
    }

    @Provide
    Arbitrary<Exception> errorTypes() {
        return Arbitraries.of(
            new RuntimeException("Generic error"),
            new ServiceUnavailableException("Service unavailable"),
            new ServiceTimeoutException("Request timeout"),
            new IllegalArgumentException("Invalid argument"),
            new IllegalStateException("Invalid state"),
            new NullPointerException("Null pointer"),
            new CompletionException(new RuntimeException("Completion failed"))
        );
    }

    @Provide
    Arbitrary<String> errorMessagesWithSpecialCharacters() {
        return Arbitraries.of(
            "Error with \"quotes\" in message",
            "Error with 'single quotes'",
            "Error with <angle> brackets",
            "Error with & ampersand",
            "Error with backslash \\",
            "Error with forward slash /",
            "Error with colon: and semicolon;",
            "Error with [square] brackets",
            "Error with {curly} braces",
            "Error with @ # $ % symbols"
        );
    }
}

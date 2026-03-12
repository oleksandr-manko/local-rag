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
 * Property-based tests for plain text response format in TestApiController.
 * **Validates: Requirements 25.5, 25.6**
 * 
 * Property 28: Plain Text Response Format
 * 
 * This test suite verifies that the TestApiController returns responses in plain text format:
 * - Returns only the generated text response without OpenAI JSON formatting (Requirement 25.5)
 * - Uses Content-Type text/plain for responses (Requirement 25.6)
 * - Response body contains only the assistant message content
 * - No JSON structure like {"choices": [...], "usage": {...}} in the response
 * - Response is directly usable as plain text without parsing
 */
class PlainTextResponseFormatPropertyTest {

    // ==================== Plain Text Response Format Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController returns response Then response body is plain text without JSON formatting")
    void responseBodyIsPlainTextWithoutJsonFormatting(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String generatedText
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with specific generated text
        ChatCompletionResponse mockResponse = createMockResponse(generatedText);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: response body contains only the plain text (Requirement 25.5)
        assertThat(response.getBody()).isEqualTo(generatedText);
        
        // Then: response body does NOT contain JSON structure
        assertThat(response.getBody()).doesNotContain("\"choices\"");
        assertThat(response.getBody()).doesNotContain("\"message\"");
        assertThat(response.getBody()).doesNotContain("\"content\"");
        assertThat(response.getBody()).doesNotContain("\"usage\"");
        assertThat(response.getBody()).doesNotContain("\"model\"");
        assertThat(response.getBody()).doesNotContain("{");
        assertThat(response.getBody()).doesNotContain("}");
        assertThat(response.getBody()).doesNotContain("[");
        assertThat(response.getBody()).doesNotContain("]");
    }

    @Property(tries = 100)
    @Label("When TestApiController returns response Then Content-Type is text/plain")
    void responseContentTypeIsTextPlain(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String generatedText
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with specific generated text
        ChatCompletionResponse mockResponse = createMockResponse(generatedText);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: response Content-Type is text/plain (Requirement 25.6)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: response Content-Type is NOT application/json
        assertThat(response.getHeaders().getContentType())
                .isNotEqualTo(MediaType.APPLICATION_JSON);
    }

    @Property(tries = 100)
    @Label("When TestApiController extracts response Then extracts only assistant message content")
    void extractsOnlyAssistantMessageContent(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String assistantContent
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with assistant message
        ChatCompletionResponse mockResponse = createMockResponse(assistantContent);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        CompletableFuture<ResponseEntity<String>> responseFuture = 
                controller.testQuery(prompt);
        ResponseEntity<String> response = responseFuture.join();
        
        // Then: response body equals exactly the assistant message content
        assertThat(response.getBody()).isEqualTo(assistantContent);
        
        // Then: response does not include metadata like model, id, created timestamp
        assertThat(response.getBody()).doesNotContain("chatcmpl-");
        assertThat(response.getBody()).doesNotContain("chat.completion");
    }

    // ==================== Response Format Consistency Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController processes multiple requests Then all responses are plain text")
    void allResponsesArePlainText(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt1,
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt2,
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String response1,
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String response2
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns different responses for different prompts
        ChatCompletionResponse mockResponse1 = createMockResponse(response1);
        ChatCompletionResponse mockResponse2 = createMockResponse(response2);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse1))
                .thenReturn(CompletableFuture.completedFuture(mockResponse2));
        
        // When: sending first prompt
        ResponseEntity<String> result1 = controller.testQuery(prompt1).join();
        
        // When: sending second prompt
        ResponseEntity<String> result2 = controller.testQuery(prompt2).join();
        
        // Then: both responses are plain text
        assertThat(result1.getBody()).isEqualTo(response1);
        assertThat(result2.getBody()).isEqualTo(response2);
        
        // Then: both responses have text/plain Content-Type
        assertThat(result1.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(result2.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }

    @Property(tries = 100)
    @Label("When response contains special characters Then plain text format is preserved")
    void plainTextFormatPreservesSpecialCharacters(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt,
            @ForAll("responsesWithSpecialCharacters") String responseWithSpecialChars
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with special characters
        ChatCompletionResponse mockResponse = createMockResponse(responseWithSpecialChars);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: response body preserves special characters exactly
        assertThat(response.getBody()).isEqualTo(responseWithSpecialChars);
        
        // Then: special characters are not escaped or encoded
        // (In JSON, quotes would be escaped as \", but in plain text they remain as ")
        if (responseWithSpecialChars.contains("\"")) {
            assertThat(response.getBody()).contains("\"");
            assertThat(response.getBody()).doesNotContain("\\\"");
        }
    }

    // ==================== Response Usability Tests ====================

    @Property(tries = 100)
    @Label("When TestApiController returns response Then response is directly usable without parsing")
    void responseIsDirectlyUsableWithoutParsing(
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 1000) String generatedText
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with specific generated text
        ChatCompletionResponse mockResponse = createMockResponse(generatedText);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: response body can be used directly as a String
        String responseText = response.getBody();
        assertThat(responseText).isNotNull();
        assertThat(responseText).isEqualTo(generatedText);
        
        // Then: no JSON parsing is required to extract the content
        // (If this were JSON, we'd need to parse it and navigate to choices[0].message.content)
        // With plain text, the response body IS the content
        assertThat(responseText.length()).isEqualTo(generatedText.length());
    }

    @Property(tries = 50)
    @Label("When response contains newlines and whitespace Then plain text format preserves formatting")
    void plainTextFormatPreservesWhitespace(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt,
            @ForAll("responsesWithWhitespace") String responseWithWhitespace
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns response with newlines and whitespace
        ChatCompletionResponse mockResponse = createMockResponse(responseWithWhitespace);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: response body preserves newlines and whitespace exactly
        assertThat(response.getBody()).isEqualTo(responseWithWhitespace);
        
        // Then: whitespace is not normalized or escaped
        if (responseWithWhitespace.contains("\n")) {
            assertThat(response.getBody()).contains("\n");
            assertThat(response.getBody()).doesNotContain("\\n");
        }
    }

    // ==================== Error Response Format Tests ====================

    @Property(tries = 50)
    @Label("When error occurs Then error response is also plain text with text/plain Content-Type")
    void errorResponseIsAlsoPlainText(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler that fails
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler throws an exception
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(
                        new RuntimeException("Service unavailable")));
        
        // When: sending plain text prompt to controller
        ResponseEntity<String> response = controller.testQuery(prompt).join();
        
        // Then: error response Content-Type is text/plain (Requirement 25.6)
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.TEXT_PLAIN);
        
        // Then: error response body is plain text without JSON formatting
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).doesNotContain("{");
        assertThat(response.getBody()).doesNotContain("}");
        assertThat(response.getBody()).contains("Error");
    }

    // ==================== Comparison with OpenAI Format Tests ====================

    @Property(tries = 50)
    @Label("When comparing with OpenAI format Then test endpoint response is simpler and more direct")
    void testEndpointResponseIsSimpler(
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String prompt,
            @ForAll @NotBlank @StringLength(min = 1, max = 500) String generatedText
    ) {
        // Feature: rag-openai-api-ollama, Property 28: Plain Text Response Format
        
        // Given: a TestApiController with mocked QueryHandler
        QueryHandler mockQueryHandler = mock(QueryHandler.class);
        TestApiController controller = new TestApiController(mockQueryHandler);
        
        // Given: QueryHandler returns OpenAI-formatted response
        ChatCompletionResponse openAIResponse = createMockResponse(generatedText);
        when(mockQueryHandler.handleQuery(any(ChatCompletionRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(openAIResponse));
        
        // When: getting response from test endpoint
        ResponseEntity<String> testResponse = controller.testQuery(prompt).join();
        
        // Then: test endpoint response is just the text content
        assertThat(testResponse.getBody()).isEqualTo(generatedText);
        
        // Then: test endpoint response is much simpler than OpenAI format
        // OpenAI format would include: id, object, created, model, choices[], usage{}
        // Test endpoint format is just: the generated text
        assertThat(testResponse.getBody().length()).isEqualTo(generatedText.length());
        
        // Then: OpenAI response would be much longer due to metadata
        String openAIJson = String.format(
            "{\"id\":\"chatcmpl-123\",\"object\":\"chat.completion\",\"created\":%d," +
            "\"model\":\"default\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\"," +
            "\"content\":\"%s\"},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":10," +
            "\"completion_tokens\":20,\"total_tokens\":30}}",
            System.currentTimeMillis() / 1000,
            generatedText
        );
        assertThat(testResponse.getBody().length()).isLessThan(openAIJson.length());
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
    Arbitrary<String> responsesWithSpecialCharacters() {
        return Arbitraries.of(
            "Response with \"quotes\" inside",
            "Response with 'single quotes'",
            "Response with <html> tags",
            "Response with & ampersand",
            "Response with backslash \\",
            "Response with forward slash /",
            "Response with colon: and semicolon;",
            "Response with brackets [like this]",
            "Response with braces {like this}",
            "Response with @ # $ % symbols"
        );
    }

    @Provide
    Arbitrary<String> responsesWithWhitespace() {
        return Arbitraries.of(
            "Line 1\nLine 2\nLine 3",
            "Paragraph 1\n\nParagraph 2",
            "Text with\ttabs\tinside",
            "Text with    multiple    spaces",
            "  Leading and trailing spaces  ",
            "\nLeading newline",
            "Trailing newline\n",
            "Mixed\n\twhitespace\n  formatting"
        );
    }
}

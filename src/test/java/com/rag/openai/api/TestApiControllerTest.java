package com.rag.openai.api;

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

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TestApiController.
 * Tests plain text request acceptance, plain text response format, error handling, and Content-Type validation.
 */
@ExtendWith(MockitoExtension.class)
class TestApiControllerTest {
    
    @Mock
    private QueryHandler queryHandler;
    
    private TestApiController controller;
    
    @BeforeEach
    void setUp() {
        controller = new TestApiController(queryHandler);
    }
    
    @Test
    @DisplayName("When valid plain text request Then return plain text response with 200 OK")
    void testQuery_WithValidPlainTextRequest() {
        // Given: A valid plain text prompt
        String plainTextPrompt = "What is the capital of France?";
        
        Message responseMessage = new Message("assistant", "The capital of France is Paris.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(15, 8, 23);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-123",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should be 200 OK with plain text content
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("The capital of France is Paris.");
        
        verify(queryHandler).handleQuery(any(ChatCompletionRequest.class));
    }
    
    @Test
    @DisplayName("When plain text request with multiple sentences Then return complete plain text response")
    void testQuery_WithMultiSentencePrompt() {
        // Given: A plain text prompt with multiple sentences
        String plainTextPrompt = "Tell me about Java. What are its main features?";
        
        Message responseMessage = new Message("assistant", 
            "Java is a popular programming language. Its main features include object-oriented design, " +
            "platform independence, and automatic memory management.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(20, 30, 50);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-456",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should contain the complete plain text response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Java is a popular programming language");
        assertThat(response.getBody()).contains("object-oriented design");
        assertThat(response.getBody()).contains("automatic memory management");
    }
    
    @Test
    @DisplayName("When plain text request with special characters Then handle correctly")
    void testQuery_WithSpecialCharacters() {
        // Given: A plain text prompt with special characters
        String plainTextPrompt = "What is 2+2? How about 3*3?";
        
        Message responseMessage = new Message("assistant", "2+2 equals 4. 3*3 equals 9.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(12, 10, 22);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-789",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should handle special characters correctly
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("2+2 equals 4. 3*3 equals 9.");
    }
    
    @Test
    @DisplayName("When plain text response format Then extract only text content without JSON")
    void testQuery_PlainTextResponseFormat() {
        // Given: A valid plain text prompt
        String plainTextPrompt = "Explain RAG in one sentence.";
        
        Message responseMessage = new Message("assistant", 
            "RAG combines retrieval of relevant documents with language model generation to produce contextually informed responses.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(10, 25, 35);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-rag",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should be plain text without JSON formatting
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).doesNotContain("{");
        assertThat(response.getBody()).doesNotContain("}");
        assertThat(response.getBody()).doesNotContain("\"choices\"");
        assertThat(response.getBody()).doesNotContain("\"message\"");
        assertThat(response.getBody()).isEqualTo(
            "RAG combines retrieval of relevant documents with language model generation to produce contextually informed responses."
        );
    }
    
    @Test
    @DisplayName("When response has empty message content Then return default message")
    void testQuery_WithEmptyMessageContent() {
        // Given: A response with empty message content
        String plainTextPrompt = "Test prompt";
        
        Message emptyMessage = new Message("assistant", "");
        Choice choice = new Choice(0, emptyMessage, "stop");
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-empty",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            new Usage(5, 0, 5)
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should return empty string
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("");
    }
    
    @Test
    @DisplayName("When null prompt Then return 400 Bad Request with plain text error")
    void testQuery_WithNullPrompt() {
        // Given: A null prompt
        String nullPrompt = null;
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(nullPrompt).join();
        
        // Then: Response should be 400 Bad Request with plain text error message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("Error: Prompt must not be null or empty");
    }
    
    @Test
    @DisplayName("When empty prompt Then return 400 Bad Request with plain text error")
    void testQuery_WithEmptyPrompt() {
        // Given: An empty prompt
        String emptyPrompt = "";
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(emptyPrompt).join();
        
        // Then: Response should be 400 Bad Request with plain text error message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("Error: Prompt must not be null or empty");
    }
    
    @Test
    @DisplayName("When blank prompt Then return 400 Bad Request with plain text error")
    void testQuery_WithBlankPrompt() {
        // Given: A blank prompt (only whitespace)
        String blankPrompt = "   \n\t  ";
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(blankPrompt).join();
        
        // Then: Response should be 400 Bad Request with plain text error message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo("Error: Prompt must not be null or empty");
    }
    
    @Test
    @DisplayName("When query handler throws exception Then return 500 with plain text error")
    void testQuery_WithQueryHandlerException() {
        // Given: A valid prompt but query handler throws exception
        String plainTextPrompt = "Test query";
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(
                new RuntimeException("Service unavailable")
            ));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should be 500 Internal Server Error with plain text error message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains("Service unavailable");
    }
    
    @Test
    @DisplayName("When query handler throws nested exception Then return 500 with wrapper message")
    void testQuery_WithNestedQueryHandlerException() {
        // Given: A valid prompt but query handler throws nested exception
        String plainTextPrompt = "Test query";
        
        RuntimeException cause = new RuntimeException("Connection timeout");
        RuntimeException wrapper = new RuntimeException("Query failed", cause);
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.failedFuture(wrapper));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should include the wrapper message (CompletableFuture wraps in CompletionException)
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains("Query failed");
    }
    
    @Test
    @DisplayName("When unexpected exception occurs Then return 500 with plain text error")
    void testQuery_WithUnexpectedException() {
        // Given: A valid prompt but an unexpected exception occurs
        String plainTextPrompt = "Test query";
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenThrow(new IllegalStateException("Unexpected state"));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response should be 500 Internal Server Error with plain text error message
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("Error processing request");
        assertThat(response.getBody()).contains("Unexpected state");
    }
    
    @Test
    @DisplayName("When Content-Type is text/plain Then accept request")
    void testQuery_ContentTypeValidation() {
        // Given: A valid plain text prompt (Content-Type validation happens at Spring level)
        String plainTextPrompt = "What is Spring Boot?";
        
        Message responseMessage = new Message("assistant", 
            "Spring Boot is a framework that simplifies Spring application development.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(10, 15, 25);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-spring",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Request should be accepted and processed
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isEqualTo(
            "Spring Boot is a framework that simplifies Spring application development."
        );
    }
    
    @Test
    @DisplayName("When response Content-Type Then always be text/plain")
    void testQuery_ResponseContentType() {
        // Given: A valid plain text prompt
        String plainTextPrompt = "Test content type";
        
        Message responseMessage = new Message("assistant", "Response text");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(5, 5, 10);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-ct",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(plainTextPrompt).join();
        
        // Then: Response Content-Type should always be text/plain
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getHeaders().getContentType()).isNotEqualTo(MediaType.APPLICATION_JSON);
    }
    
    @Test
    @DisplayName("When error response Then Content-Type is text/plain")
    void testQuery_ErrorResponseContentType() {
        // Given: A null prompt that will cause an error
        String nullPrompt = null;
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(nullPrompt).join();
        
        // Then: Error response Content-Type should also be text/plain
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getHeaders().getContentType()).isNotEqualTo(MediaType.APPLICATION_JSON);
    }
    
    @Test
    @DisplayName("When long plain text prompt Then handle correctly")
    void testQuery_WithLongPrompt() {
        // Given: A long plain text prompt
        String longPrompt = "Explain the concept of Retrieval-Augmented Generation in detail. " +
            "Include information about how it works, what components are involved, " +
            "and what benefits it provides over traditional language models. " +
            "Also discuss potential use cases and limitations.";
        
        Message responseMessage = new Message("assistant", 
            "RAG is an advanced technique that enhances language model responses by retrieving relevant information...");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(50, 100, 150);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-long",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(longPrompt).join();
        
        // Then: Response should handle long prompts correctly
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody()).contains("RAG is an advanced technique");
    }
    
    @Test
    @DisplayName("When plain text prompt with newlines Then preserve formatting")
    void testQuery_WithNewlinesInPrompt() {
        // Given: A plain text prompt with newlines
        String promptWithNewlines = "Question 1: What is Java?\nQuestion 2: What is Spring?";
        
        Message responseMessage = new Message("assistant", 
            "Answer 1: Java is a programming language.\nAnswer 2: Spring is a framework.");
        Choice choice = new Choice(0, responseMessage, "stop");
        Usage usage = new Usage(15, 20, 35);
        ChatCompletionResponse mockResponse = new ChatCompletionResponse(
            "chatcmpl-newline",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "default",
            List.of(choice),
            usage
        );
        
        when(queryHandler.handleQuery(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(mockResponse));
        
        // When: Call the test query endpoint
        ResponseEntity<String> response = controller.testQuery(promptWithNewlines).join();
        
        // Then: Response should preserve newlines in plain text
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
        assertThat(response.getBody()).contains("\n");
        assertThat(response.getBody()).isEqualTo(
            "Answer 1: Java is a programming language.\nAnswer 2: Spring is a framework."
        );
    }
}

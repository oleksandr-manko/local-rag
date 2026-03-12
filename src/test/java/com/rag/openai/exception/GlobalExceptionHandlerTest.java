package com.rag.openai.exception;

import com.rag.openai.domain.dto.ErrorDetail;
import com.rag.openai.domain.dto.OpenAIError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for GlobalExceptionHandler.
 * Tests error response formatting for each exception type and OpenAI error format compliance.
 */
class GlobalExceptionHandlerTest {
    
    private GlobalExceptionHandler exceptionHandler;
    
    @BeforeEach
    void setUp() {
        // Given: A GlobalExceptionHandler instance
        exceptionHandler = new GlobalExceptionHandler();
    }
    
    @Test
    @DisplayName("When ValidationException is thrown Then return 400 with OpenAI error format")
    void testHandleValidationException() {
        // Given: A ValidationException with a specific message
        String errorMessage = "Invalid request: missing required field 'model'";
        ValidationException exception = new ValidationException(errorMessage);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleValidationException(exception);
        
        // Then: Response should be 400 Bad Request with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("invalid_request_error");
        assertThat(errorResponse.error().code()).isEqualTo("validation_failed");
    }
    
    @Test
    @DisplayName("When ServiceUnavailableException is thrown Then return 503 with OpenAI error format")
    void testHandleServiceUnavailableException() {
        // Given: A ServiceUnavailableException with a specific message
        String errorMessage = "Ollama service is unavailable";
        ServiceUnavailableException exception = new ServiceUnavailableException(errorMessage);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleServiceUnavailableException(exception);
        
        // Then: Response should be 503 Service Unavailable with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("service_unavailable_error");
        assertThat(errorResponse.error().code()).isEqualTo("service_unavailable");
    }
    
    @Test
    @DisplayName("When ServiceTimeoutException is thrown Then return 504 with OpenAI error format")
    void testHandleServiceTimeoutException() {
        // Given: A ServiceTimeoutException with a specific message
        String errorMessage = "Request to Ollama timed out after 30 seconds";
        ServiceTimeoutException exception = new ServiceTimeoutException(errorMessage);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleServiceTimeoutException(exception);
        
        // Then: Response should be 504 Gateway Timeout with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("timeout_error");
        assertThat(errorResponse.error().code()).isEqualTo("service_timeout");
    }
    
    @Test
    @DisplayName("When generic Exception is thrown Then return 500 with OpenAI error format")
    void testHandleGenericException() {
        // Given: A generic Exception with a specific message
        String errorMessage = "Unexpected null pointer";
        Exception exception = new NullPointerException(errorMessage);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleGenericException(exception);
        
        // Then: Response should be 500 Internal Server Error with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).startsWith("An unexpected error occurred:");
        assertThat(errorResponse.error().message()).contains(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("internal_error");
        assertThat(errorResponse.error().code()).isEqualTo("internal_server_error");
    }
    
    @Test
    @DisplayName("When ValidationException with cause is thrown Then return 400 with OpenAI error format")
    void testHandleValidationExceptionWithCause() {
        // Given: A ValidationException with a cause
        String errorMessage = "Invalid JSON format";
        Throwable cause = new IllegalArgumentException("Malformed JSON");
        ValidationException exception = new ValidationException(errorMessage, cause);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleValidationException(exception);
        
        // Then: Response should be 400 Bad Request with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("invalid_request_error");
        assertThat(errorResponse.error().code()).isEqualTo("validation_failed");
    }
    
    @Test
    @DisplayName("When ServiceUnavailableException with cause is thrown Then return 503 with OpenAI error format")
    void testHandleServiceUnavailableExceptionWithCause() {
        // Given: A ServiceUnavailableException with a cause
        String errorMessage = "Cannot connect to Qdrant";
        Throwable cause = new java.net.ConnectException("Connection refused");
        ServiceUnavailableException exception = new ServiceUnavailableException(errorMessage, cause);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleServiceUnavailableException(exception);
        
        // Then: Response should be 503 Service Unavailable with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("service_unavailable_error");
        assertThat(errorResponse.error().code()).isEqualTo("service_unavailable");
    }
    
    @Test
    @DisplayName("When ServiceTimeoutException with cause is thrown Then return 504 with OpenAI error format")
    void testHandleServiceTimeoutExceptionWithCause() {
        // Given: A ServiceTimeoutException with a cause
        String errorMessage = "Embedding generation timed out";
        Throwable cause = new java.util.concurrent.TimeoutException("Operation timed out");
        ServiceTimeoutException exception = new ServiceTimeoutException(errorMessage, cause);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleServiceTimeoutException(exception);
        
        // Then: Response should be 504 Gateway Timeout with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).isEqualTo(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("timeout_error");
        assertThat(errorResponse.error().code()).isEqualTo("service_timeout");
    }
    
    @Test
    @DisplayName("When RuntimeException is thrown Then return 500 with OpenAI error format")
    void testHandleRuntimeException() {
        // Given: A RuntimeException
        String errorMessage = "Unexpected runtime error";
        RuntimeException exception = new RuntimeException(errorMessage);
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleGenericException(exception);
        
        // Then: Response should be 500 Internal Server Error with OpenAI error format
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        assertThat(errorResponse.error()).isNotNull();
        assertThat(errorResponse.error().message()).contains(errorMessage);
        assertThat(errorResponse.error().type()).isEqualTo("internal_error");
        assertThat(errorResponse.error().code()).isEqualTo("internal_server_error");
    }
    
    @Test
    @DisplayName("When error response is created Then it complies with OpenAI error format structure")
    void testOpenAIErrorFormatCompliance() {
        // Given: A ValidationException
        ValidationException exception = new ValidationException("Test validation error");
        
        // When: Handle the exception
        ResponseEntity<OpenAIError> response = exceptionHandler.handleValidationException(exception);
        
        // Then: Response body should comply with OpenAI error format structure
        assertThat(response.getBody()).isNotNull();
        
        OpenAIError errorResponse = response.getBody();
        
        // Verify top-level structure has 'error' field
        assertThat(errorResponse.error()).isNotNull();
        
        // Verify ErrorDetail has required fields
        ErrorDetail errorDetail = errorResponse.error();
        assertThat(errorDetail.message()).isNotNull().isNotEmpty();
        assertThat(errorDetail.type()).isNotNull().isNotEmpty();
        assertThat(errorDetail.code()).isNotNull();
        
        // Verify error detail structure matches OpenAI specification
        assertThat(errorDetail).hasNoNullFieldsOrProperties();
    }
}

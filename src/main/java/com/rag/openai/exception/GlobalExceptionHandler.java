package com.rag.openai.exception;

import com.rag.openai.domain.dto.ErrorDetail;
import com.rag.openai.domain.dto.OpenAIError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Global exception handler for the RAG application.
 * Handles all exceptions and returns OpenAI-compatible error responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    /**
     * Handles validation exceptions.
     * Returns 400 Bad Request with OpenAI error format.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<OpenAIError> handleValidationException(ValidationException ex) {
        logger.error("Validation error: {}", ex.getMessage(), ex);
        
        var errorDetail = new ErrorDetail(
            ex.getMessage(),
            "invalid_request_error",
            "validation_failed"
        );
        
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(new OpenAIError(errorDetail));
    }
    
    /**
     * Handles service unavailable exceptions.
     * Returns 503 Service Unavailable with OpenAI error format.
     */
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<OpenAIError> handleServiceUnavailableException(ServiceUnavailableException ex) {
        logger.error("Service unavailable: {}", ex.getMessage(), ex);
        
        var errorDetail = new ErrorDetail(
            ex.getMessage(),
            "service_unavailable_error",
            "service_unavailable"
        );
        
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new OpenAIError(errorDetail));
    }
    
    /**
     * Handles service timeout exceptions.
     * Returns 504 Gateway Timeout with OpenAI error format.
     */
    @ExceptionHandler(ServiceTimeoutException.class)
    public ResponseEntity<OpenAIError> handleServiceTimeoutException(ServiceTimeoutException ex) {
        logger.error("Service timeout: {}", ex.getMessage(), ex);
        
        var errorDetail = new ErrorDetail(
            ex.getMessage(),
            "timeout_error",
            "service_timeout"
        );
        
        return ResponseEntity
            .status(HttpStatus.GATEWAY_TIMEOUT)
            .body(new OpenAIError(errorDetail));
    }
    
    /**
     * Handles all other exceptions.
     * Returns 500 Internal Server Error with OpenAI error format.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<OpenAIError> handleGenericException(Exception ex) {
        logger.error("Unexpected error: {}", ex.getMessage(), ex);
        
        var errorDetail = new ErrorDetail(
            "An unexpected error occurred: " + ex.getMessage(),
            "internal_error",
            "internal_server_error"
        );
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(new OpenAIError(errorDetail));
    }
}

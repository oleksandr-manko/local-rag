package com.rag.openai.exception;

/**
 * Exception thrown when an external service request times out.
 */
public class ServiceTimeoutException extends RuntimeException {
    
    public ServiceTimeoutException(String message) {
        super(message);
    }
    
    public ServiceTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

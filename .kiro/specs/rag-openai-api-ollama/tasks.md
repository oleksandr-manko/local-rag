# Implementation Plan: RAG OpenAI API with Ollama

## Overview

This implementation plan breaks down the RAG application into discrete coding tasks. The application will be built using Java 25, Spring Boot 4, and Gradle 9.2, following functional programming principles. The implementation follows a bottom-up approach, starting with core utilities and data models, then building service layers, and finally wiring everything together with API endpoints and scheduled jobs.

## Tasks

- [x] 1. Set up project structure and build configuration
  - Create Gradle project with Java 25 and Spring Boot 4
  - Configure build.gradle with all required dependencies (Spring Boot Web, WebFlux, PDFBox, Qdrant client, Redis client, SpringDoc OpenAPI)
  - Set up Gradle wrapper (gradlew, gradlew.bat, gradle-wrapper.jar, gradle-wrapper.properties with Gradle 9.2)
  - Set up application.yaml with all configuration properties including OpenAPI configuration
  - Create package structure following layered architecture
  - _Requirements: 17.1, 17.2, 17.3, 17.4, 17.5, 17.6, 17.7, 17.8, 24.1, 24.2, 24.3, 24.6_

- [x] 2. Implement core data models and DTOs
  - [x] 2.1 Create OpenAI API data models
    - Implement ChatCompletionRequest, Message, ChatCompletionResponse, Choice, Usage records
    - Implement ChatCompletionChunk, ChunkChoice, Delta records for streaming
    - Implement ModelsResponse and ModelInfo records
    - Implement OpenAIError and ErrorDetail records
    - Add validation logic in compact constructors
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.6_
  
  - [x] 2.2 Create domain models
    - Implement DocumentMetadata, TextChunk, EmbeddingRecord, ScoredChunk records
    - Implement ProcessingResult and FileHashRecord records
    - Ensure all models are immutable using Java records
    - _Requirements: 6.4, 16.4_
  
  - [x] 2.3 Create Ollama API models
    - Implement OllamaGenerateRequest, OllamaOptions, OllamaGenerateResponse records
    - Implement OllamaEmbeddingRequest and OllamaEmbeddingResponse records
    - _Requirements: 2.5, 2.6_
  
  - [x] 2.4 Create Qdrant models
    - Implement QdrantPoint, QdrantSearchRequest, QdrantSearchResult records
    - _Requirements: 7.5_

- [x] 3. Implement configuration classes
  - [x] 3.1 Create configuration record classes
    - Implement OllamaConfig with validation in compact constructor
    - Implement QdrantConfig with validation in compact constructor
    - Implement RedisConfig with validation in compact constructor
    - Implement DocumentsConfig with validation in compact constructor
    - Implement ProcessingConfig with validation in compact constructor
    - Implement RagConfig with validation in compact constructor
    - Add @ConfigurationProperties annotations
    - _Requirements: 2.2, 2.3, 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.8, 3.9, 15.1, 15.2, 15.5, 15.6, 20.1, 20.2, 23.10_
  
  - [x] 3.2 Write property test for configuration validation
    - **Property 6: Configuration Validation**
    - **Validates: Requirements 3.8, 15.5**

- [x] 4. Implement Redis client for hash storage
  - [x] 4.1 Create RedisClient interface and implementation
    - Implement getFileHash method returning CompletableFuture<Optional<String>>
    - Implement storeFileHash method returning CompletableFuture<Void>
    - Implement getAllFileHashes method returning CompletableFuture<Map<Path, String>>
    - Implement deleteFileHash method returning CompletableFuture<Void>
    - Implement verifyConnectivity method for health checks
    - Use Lettuce Redis client with connection pooling
    - Handle connection errors gracefully with logging
    - _Requirements: 20.3, 20.4, 20.5, 20.6, 20.7, 20.8_
  
  - [x] 4.2 Write unit tests for RedisClient
    - Test hash storage and retrieval
    - Test connection error handling
    - Test Optional.empty() return on missing keys
    - _Requirements: 20.3, 20.4, 20.5, 20.6_

- [x] 5. Implement Ollama client
  - [x] 5.1 Create OllamaClient interface and implementation
    - Implement generate method returning CompletableFuture<String>
    - Implement generateStreaming method returning CompletableFuture<Flux<String>>
    - Implement generateEmbedding method returning CompletableFuture<List<Float>>
    - Implement analyzeImage method for vision models returning CompletableFuture<String>
    - Implement verifyConnectivity method for health checks
    - Use WebClient for HTTP communication
    - Configure connection and read timeouts from OllamaConfig
    - Handle connection errors with ServiceUnavailableException
    - Handle timeout errors with ServiceTimeoutException
    - Log all errors with context
    - Encode images as base64 for vision API requests
    - _Requirements: 2.1, 2.4, 2.5, 2.6, 2.7, 2.8_
  
  - [x] 5.2 Write property test for Ollama request format round trip
    - **Property 5: Ollama Request Format Round Trip**
    - **Validates: Requirements 2.5, 2.6**
  
  - [x] 5.3 Write unit tests for OllamaClient
    - Test request formatting with examples
    - Test response parsing with examples
    - Test vision model image analysis with examples
    - Test base64 image encoding with examples
    - Test connection error handling
    - Test timeout handling
    - _Requirements: 2.5, 2.6, 2.8_

- [x] 6. Implement Vector Store client for Qdrant
  - [x] 6.1 Create VectorStoreClient interface and implementation
    - Implement ensureCollectionExists method returning CompletableFuture<Void>
    - Implement storeEmbeddings method with batch support returning CompletableFuture<Void>
    - Implement searchSimilar method returning CompletableFuture<List<ScoredChunk>>
    - Implement deleteEmbeddingsByFilename method returning CompletableFuture<Void>
    - Implement verifyConnectivity method for health checks
    - Use Qdrant gRPC client
    - Implement exponential backoff retry logic for transient failures
    - Use OllamaClient to generate embeddings
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 22.2, 22.3_
  
  - [x] 6.2 Write property test for embedding generation
    - **Property 14: Embedding Generation**
    - **Validates: Requirements 7.4**
  
  - [x] 6.3 Write property test for embedding storage round trip
    - **Property 15: Embedding Storage Round Trip**
    - **Validates: Requirements 7.5**
  
  - [x] 6.4 Write property test for batch insertion equivalence
    - **Property 16: Batch Insertion Equivalence**
    - **Validates: Requirements 7.6**
  
  - [x] 6.5 Write unit tests for VectorStoreClient
    - Test collection creation
    - Test embedding storage with examples
    - Test similarity search with examples
    - Test deletion by filename
    - Test retry logic on connection errors
    - _Requirements: 7.1, 7.2, 7.3, 7.5, 7.6, 7.7_

- [x] 7. Implement Chunking Service
  - [x] 7.1 Create ChunkingService interface and implementation
    - Implement chunkText method as pure function
    - Split text into fixed-size chunks with configured overlap
    - Preserve word boundaries (split at whitespace/punctuation)
    - Attach metadata (filename, chunk index, start/end positions) to each chunk
    - Handle edge case: text shorter than chunk size
    - Use Stream API for functional chunk generation
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_
  
  - [x] 7.2 Write property test for chunk size compliance
    - **Property 10: Chunk Size Compliance**
    - **Validates: Requirements 6.1**
  
  - [x] 7.3 Write property test for chunk overlap compliance
    - **Property 11: Chunk Overlap Compliance**
    - **Validates: Requirements 6.2**
  
  - [x] 7.4 Write property test for word boundary preservation
    - **Property 12: Word Boundary Preservation**
    - **Validates: Requirements 6.3**
  
  - [x] 7.5 Write property test for chunk metadata completeness
    - **Property 13: Chunk Metadata Completeness**
    - **Validates: Requirements 6.4**
  
  - [x] 7.6 Write unit tests for ChunkingService
    - Test fixed-size chunking with examples
    - Test overlap application with examples
    - Test short text handling (edge case)
    - Test empty text handling (edge case)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [-] 8. Implement Document Processor
  - [x] 8.1 Create DocumentProcessor interface and implementation
    - Implement processDocuments method returning CompletableFuture<ProcessingResult>
    - Implement extractTextFromPdf method using Apache PDFBox returning CompletableFuture<Optional<String>>
    - Implement extractTextFromImage method using Ollama vision models returning CompletableFuture<Optional<String>>
    - Implement computeFileHash method using SHA-256 returning CompletableFuture<String>
    - Implement shouldProcessFile method checking hash against RedisClient
    - Scan folder for files with supported extensions using Stream API
    - Use CompletableFuture.allOf for parallel file processing
    - Handle file read errors gracefully (log and return Optional.empty())
    - Coordinate deletion of old embeddings via VectorStoreClient for modified files
    - Update file hashes in RedisClient after successful processing
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 5.1, 5.2, 5.3, 5.4, 19.1, 19.2, 19.3, 19.4, 19.5, 19.7, 21.1, 21.2, 21.3, 21.4, 21.5, 21.6, 22.1, 22.4, 22.5, 22.6_
  
  - [x] 8.2 Write property test for document discovery
    - **Property 7: Document Discovery**
    - **Validates: Requirements 4.1, 5.1**
  
  - [x] 8.3 Write property test for PDF text extraction
    - **Property 8: PDF Text Extraction**
    - **Validates: Requirements 4.2**
  
  - [x] 8.4 Write property test for image text extraction
    - **Property 9: Image Text Extraction**
    - **Validates: Requirements 5.2**
  
  - [x] 8.5 Write unit tests for DocumentProcessor
    - Test PDF extraction with sample files
    - Test image vision model extraction with sample files
    - Test file discovery with examples
    - Test corrupted file handling (edge case)
    - Test empty file handling (edge case)
    - Test hash computation
    - Test skip logic for unchanged files
    - _Requirements: 4.1, 4.2, 4.4, 5.1, 5.2, 5.3, 19.1, 19.4, 19.5, 21.3_

- [x] 9. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 10. Implement Query Handler for RAG operations
  - [x] 10.1 Create QueryHandler interface and implementation
    - Implement handleQuery method returning CompletableFuture<ChatCompletionResponse>
    - Implement handleStreamingQuery method returning CompletableFuture<Flux<ChatCompletionChunk>>
    - Extract user prompt from last user message in request
    - Generate query embedding via VectorStoreClient
    - Search for top-K similar chunks from Qdrant
    - Construct augmented prompt using RagConfig template
    - Handle case with no relevant chunks (use original prompt)
    - Send augmented prompt to OllamaClient
    - Format response according to OpenAI specification
    - Use functional composition with CompletableFuture.thenCompose
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9_
  
  - [x] 10.2 Write property test for prompt extraction
    - **Property 20: Prompt Extraction**
    - **Validates: Requirements 10.1**
  
  - [x] 10.3 Write property test for query embedding generation
    - **Property 21: Query Embedding Generation**
    - **Validates: Requirements 10.2**
  
  - [x] 10.4 Write property test for top-K retrieval
    - **Property 22: Top-K Retrieval**
    - **Validates: Requirements 10.3**
  
  - [x] 10.5 Write property test for prompt augmentation structure
    - **Property 23: Prompt Augmentation Structure**
    - **Validates: Requirements 10.4, 10.5**
  
  - [x] 10.6 Write unit tests for QueryHandler
    - Test prompt extraction with examples
    - Test prompt augmentation with examples
    - Test no results found handling (edge case)
    - Test response formatting with examples
    - _Requirements: 10.1, 10.4, 10.5, 10.7, 10.9_

- [x] 11. Implement OpenAI API Controller
  - [x] 11.1 Create OpenAIApiController REST controller
    - Implement POST /v1/chat/completions endpoint
    - Validate incoming ChatCompletionRequest
    - Delegate to QueryHandler based on stream parameter
    - Return ResponseEntity with OpenAI-formatted response
    - Handle streaming responses with server-sent events
    - Handle non-streaming responses with single JSON object
    - Implement GET /v1/models endpoint returning available models
    - Add @RestController and @RequestMapping annotations
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7_
  
  - [x] 11.2 Write property test for request validation
    - **Property 1: Request Validation**
    - **Validates: Requirements 1.2, 1.6**
  
  - [x] 11.3 Write property test for response format compliance
    - **Property 2: Response Format Compliance**
    - **Validates: Requirements 1.3, 10.7**
  
  - [x] 11.4 Write property test for streaming response format compliance
    - **Property 3: Streaming Response Format Compliance**
    - **Validates: Requirements 1.4, 11.4**
  
  - [x] 11.5 Write property test for streaming mode selection
    - **Property 4: Streaming Mode Selection**
    - **Validates: Requirements 1.4, 1.5, 11.1**
  
  - [x] 11.6 Write unit tests for OpenAIApiController
    - Test valid request handling with examples
    - Test invalid request validation with examples
    - Test streaming vs non-streaming mode with examples
    - Test models endpoint with example
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 1.7_

- [x] 11a. Implement Simple Test API Controller
  - [x] 11a.1 Create TestApiController REST controller
    - Implement POST /api/test/query endpoint accepting plain text
    - Set Content-Type to text/plain for request and response
    - Delegate to QueryHandler using the same RAG pipeline as OpenAI endpoint
    - Extract plain text response from OpenAI-formatted response
    - Return plain text response without JSON formatting
    - Handle errors with plain text error messages and appropriate HTTP status codes
    - Add OpenAPI annotations (@Tag, @Operation, @ApiResponses, @RequestBody, @Content, @Schema, @ExampleObject)
    - _Requirements: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7_
  
  - [x] 11a.2 Write property test for plain text request handling
    - **Property 26: Plain Text Request Handling**
    - **Validates: Requirements 25.2, 25.6**
  
  - [x] 11a.3 Write property test for RAG pipeline equivalence
    - **Property 27: RAG Pipeline Equivalence**
    - **Validates: Requirements 25.3, 25.4**
  
  - [x] 11a.4 Write property test for plain text response format
    - **Property 28: Plain Text Response Format**
    - **Validates: Requirements 25.5, 25.6**
  
  - [x] 11a.5 Write property test for plain text error handling
    - **Property 29: Plain Text Error Handling**
    - **Validates: Requirements 25.7**
  
  - [x] 11a.6 Write unit tests for TestApiController
    - Test plain text request acceptance with examples
    - Test plain text response format with examples
    - Test error handling with plain text with examples
    - Test Content-Type header validation with examples
    - _Requirements: 25.1, 25.2, 25.5, 25.6, 25.7_

- [x] 12. Implement global exception handler
  - [x] 12.1 Create GlobalExceptionHandler with @ControllerAdvice
    - Add @ExceptionHandler for ValidationException returning 400 with OpenAI error format
    - Add @ExceptionHandler for ServiceUnavailableException returning 503 with OpenAI error format
    - Add @ExceptionHandler for ServiceTimeoutException returning 504 with OpenAI error format
    - Add @ExceptionHandler for generic Exception returning 500 with OpenAI error format
    - Log all exceptions with full context
    - _Requirements: 1.6, 12.1, 12.2, 12.3_
  
  - [x] 12.2 Write unit tests for GlobalExceptionHandler
    - Test error response formatting for each exception type
    - Test OpenAI error format compliance
    - _Requirements: 1.6_

- [x] 13. Implement Processing Job
  - [x] 13.1 Create ProcessingJob component with scheduling
    - Implement executeScheduledProcessing method with @Scheduled annotation
    - Implement triggerProcessing method returning CompletableFuture<ProcessingResult>
    - Implement isProcessingInProgress method using atomic state
    - Use AtomicBoolean to prevent concurrent execution
    - Coordinate document processing via DocumentProcessor
    - Log processing metrics (start time, end time, document counts)
    - Handle errors and continue on next scheduled execution
    - _Requirements: 8.1, 8.2, 8.5, 8.6, 9.2, 9.3, 9.4, 9.5_
  
  - [x] 13.2 Write property test for incremental processing
    - **Property 17: Incremental Processing**
    - **Validates: Requirements 8.3, 8.4**
  
  - [x] 13.3 Write property test for modified file detection
    - **Property 18: Modified File Detection**
    - **Validates: Requirements 8.4**
  
  - [x] 13.4 Write property test for complete folder processing
    - **Property 19: Complete Folder Processing**
    - **Validates: Requirements 8.2**
  
  - [x] 13.5 Write property test for concurrent processing prevention
    - **Property 25: Concurrent Processing Prevention**
    - **Validates: Requirements 9.5**
  
  - [x] 13.6 Write unit tests for ProcessingJob
    - Test scheduled execution with mocked scheduler
    - Test on-demand trigger with example
    - Test concurrent execution prevention (edge case)
    - Test error recovery (edge case)
    - _Requirements: 8.1, 8.2, 8.5, 9.2, 9.3, 9.5_

- [-] 14. Implement processing trigger endpoint
  - [x] 14.1 Create ProcessingController REST controller
    - Implement POST /api/processing/trigger endpoint
    - Check if processing is already running via ProcessingJob.isProcessingInProgress()
    - Return 409 Conflict if processing is in progress
    - Trigger processing via ProcessingJob.triggerProcessing()
    - Return processing status and document count in response
    - Add OpenAPI annotations (@Tag, @Operation, @ApiResponses)
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_
  
  - [x] 14.2 Write unit tests for ProcessingController
    - Test successful trigger with example
    - Test conflict when processing is running
    - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5_

- [x] 14a. Configure OpenAPI and Swagger UI
  - [x] 14a.1 Create OpenAPIConfiguration class
    - Configure OpenAPI bean with API info (title, version, description, contact)
    - Configure server URLs for local development
    - Configure external documentation links
    - Create GroupedOpenApi bean for public endpoints (/v1/**, /api/**)
    - Exclude actuator endpoints from API documentation
    - _Requirements: 26.1, 26.2, 26.3, 26.4, 26.5, 26.7, 26.8_
  
  - [x] 14a.2 Write property test for OpenAPI documentation completeness
    - **Property 30: OpenAPI Documentation Completeness**
    - **Validates: Requirements 26.4, 26.7**
  
  - [x] 14a.3 Write integration tests for Swagger UI
    - Test Swagger UI accessibility at /swagger-ui.html
    - Test OpenAPI spec generation at /api-docs
    - Test endpoint documentation completeness
    - Test example request/response payloads
    - _Requirements: 26.5, 26.6, 26.7_

- [-] 15. Implement health checks and monitoring
  - [x] 15.1 Create custom health indicators
    - Implement OllamaHealthIndicator checking connectivity via OllamaClient.verifyConnectivity()
    - Implement QdrantHealthIndicator checking connectivity via VectorStoreClient.verifyConnectivity()
    - Implement RedisHealthIndicator checking connectivity via RedisClient.verifyConnectivity()
    - Implement ProcessingJobHealthIndicator checking job status
    - Extend AbstractHealthIndicator for each indicator
    - Return UP when service is reachable, DOWN otherwise
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_
  
  - [x] 15.2 Write unit tests for health indicators
    - Test UP status when services are healthy
    - Test DOWN status when services are unhealthy
    - _Requirements: 13.1, 13.2, 13.3, 13.4, 13.5, 13.6_

- [x] 16. Implement graceful startup and shutdown
  - [x] 16.1 Create ApplicationStartupListener
    - Implement ApplicationReadyEvent listener
    - Verify connectivity to Ollama, Qdrant, and Redis on startup
    - Log startup status for each service
    - Fail startup if critical services are unavailable
    - _Requirements: 14.1, 14.2_
  
  - [x] 16.2 Configure graceful shutdown in application.yaml
    - Set spring.lifecycle.timeout-per-shutdown-phase to 30s
    - Set server.shutdown to graceful
    - Ensure ProcessingJob respects shutdown with 60s timeout
    - _Requirements: 14.3, 14.4, 14.5, 14.6_
  
  - [x] 16.3 Write integration tests for startup and shutdown
    - Test successful startup with all services available
    - Test startup failure when critical service is unavailable
    - Test graceful shutdown with in-flight requests
    - _Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_

- [x] 17. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 18. Implement streaming support
  - [x] 18.1 Add streaming token forwarding in QueryHandler
    - Implement streaming response handling using Flux
    - Forward tokens from Ollama to client as they arrive
    - Format each token as OpenAI streaming chunk
    - Send final [DONE] message when streaming completes
    - Handle streaming errors with error event and stream closure
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 11.6_
  
  - [x] 18.2 Write property test for streaming token forwarding
    - **Property 24: Streaming Token Forwarding**
    - **Validates: Requirements 11.3**
  
  - [x] 18.3 Write unit tests for streaming support
    - Test token forwarding with examples
    - Test completion message
    - Test error handling during streaming
    - _Requirements: 11.2, 11.3, 11.4, 11.5, 11.6_

- [x] 19. Implement logging configuration
  - [x] 19.1 Configure logging in application.yaml
    - Set log levels for root, application, and external libraries
    - Configure console and file appenders
    - Set log file rotation (max size 10MB, max history 30 days)
    - _Requirements: 12.7_
  
  - [x] 19.2 Add structured logging throughout application
    - Log all incoming API requests with timestamp and endpoint
    - Log all errors with stack traces and context
    - Log external service connectivity issues
    - Log document processing progress
    - Log vector storage operations
    - Use appropriate log levels (DEBUG, INFO, WARN, ERROR)
    - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5, 12.6_

- [x] 20. Create Docker deployment configuration
  - [x] 20.1 Create Dockerfile
    - Use eclipse-temurin:25-jdk-alpine as builder stage
    - Copy Gradle wrapper and build files (gradlew, gradlew.bat, gradle/, build.gradle, settings.gradle)
    - Make gradlew executable with chmod +x
    - Build application with ./gradlew build using Gradle wrapper
    - Use eclipse-temurin:25-jre-alpine as runtime stage
    - Copy built JAR from builder stage
    - Create documents directory
    - Expose port 8080
    - Add health check using wget
    - Set ENTRYPOINT to run the JAR
    - _Requirements: 23.1, 23.2, 23.3, 24.4, 24.5_
  
  - [x] 20.2 Create docker-compose.yml
    - Define Qdrant service with health check
    - Define Redis service with health check
    - Define rag-app service with build context
    - Configure volume mount for documents folder
    - Configure network settings with host.docker.internal for Ollama access
    - Set environment variables for all connection properties
    - Configure service dependencies (rag-app depends on Qdrant and Redis)
    - Add health checks for all services
    - Define volumes for Qdrant and Redis data persistence
    - _Requirements: 23.4, 23.5, 23.6, 23.7, 23.8, 23.9, 23.10_

- [x] 21. Create README with deployment instructions
  - [x] 21.1 Write README.md
    - Document prerequisites (Java 25, Gradle 9.2, Docker, Ollama)
    - Document Gradle wrapper usage (./gradlew build, ./gradlew test, ./gradlew bootRun)
    - Document configuration properties
    - Document Docker deployment commands
    - Document API endpoints and usage examples (OpenAI endpoint, test endpoint, processing trigger)
    - Document Swagger UI access at /swagger-ui.html
    - Document health check endpoints
    - Document troubleshooting tips
    - _Requirements: 24.7, 26.5_

- [x] 22. Final integration testing
  - [x] 22.1 Write end-to-end integration test
    - Test complete RAG flow from document processing to query response
    - Test streaming flow with SSE connection
    - Test simple test endpoint with plain text request/response
    - Test health check flow
    - Test Swagger UI accessibility
    - _Requirements: 1.1, 1.3, 1.4, 8.1, 10.1, 10.7, 13.1, 25.1, 26.5_

- [x] 23. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties across randomized inputs
- Unit tests validate specific examples and edge cases
- The implementation follows functional programming principles using Java 25 features
- All external service calls use CompletableFuture for asynchronous operations
- All data models are immutable using Java records
- Stream API is used throughout for collection processing

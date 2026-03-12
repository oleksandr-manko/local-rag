# Requirements Document

## Introduction

This document specifies the requirements for a Retrieval-Augmented Generation (RAG) application that provides OpenAI-compatible API endpoints while using local Ollama LLM models. The system processes documents from a folder, stores them as vector embeddings in Qdrant, and augments user prompts with relevant context before generating responses.

## Glossary

- **RAG_Application**: The Spring Boot application that orchestrates document processing, vector storage, and prompt augmentation
- **OpenAI_API_Adapter**: The component that exposes OpenAI-compatible REST endpoints
- **Ollama_Client**: The component that communicates with the local Ollama server
- **Document_Processor**: The component that converts images and PDFs to text
- **Chunking_Service**: The component that splits text into fixed-size chunks
- **Vector_Store_Client**: The component that interacts with Qdrant vector database
- **Processing_Job**: The scheduled job that processes documents and stores embeddings
- **Query_Handler**: The component that handles incoming chat requests and performs RAG operations
- **Configuration**: The application.yaml file containing all configurable parameters
- **OpenWebUI**: The external client application that connects to the RAG_Application
- **Qdrant**: The vector database used for storing and retrieving document embeddings
- **Ollama**: The local LLM server that provides language model inference
- **Chunk**: A fixed-size segment of text extracted from a document
- **Embedding**: A vector representation of a text chunk
- **Augmented_Prompt**: The original user prompt enhanced with relevant context from retrieved chunks
- **Hash_Store**: The Redis database that stores file hashes and processing metadata
- **File_Hash**: A SHA-256 hash value computed from file contents to detect changes
- **Redis_Client**: The component that interacts with Redis for storing and retrieving file hashes

## Requirements

### Requirement 1: OpenAI API Compatibility

**User Story:** As an OpenWebUI user, I want to connect to the RAG_Application using OpenAI API endpoints, so that I can interact with it as a standard chatbot without changing my client configuration.

#### Acceptance Criteria

1. THE OpenAI_API_Adapter SHALL expose a POST endpoint at `/v1/chat/completions` that accepts OpenAI chat completion request format
2. WHEN a chat completion request is received, THE OpenAI_API_Adapter SHALL validate the request structure
3. THE OpenAI_API_Adapter SHALL return responses in OpenAI chat completion response format
4. THE OpenAI_API_Adapter SHALL support streaming responses when the `stream` parameter is true
5. THE OpenAI_API_Adapter SHALL support non-streaming responses when the `stream` parameter is false
6. WHEN an invalid request is received, THE OpenAI_API_Adapter SHALL return an error response in OpenAI error format
7. THE OpenAI_API_Adapter SHALL expose a GET endpoint at `/v1/models` that returns available model information

### Requirement 2: Ollama Integration

**User Story:** As a system administrator, I want the RAG_Application to use local Ollama models, so that I can leverage locally installed LLMs without external API dependencies.

#### Acceptance Criteria

1. THE Ollama_Client SHALL connect to the Ollama server using the host and port specified in Configuration
2. THE Configuration SHALL include `ollama.host` property with default value `127.0.0.1`
3. THE Configuration SHALL include `ollama.port` property with default value `11434`
4. WHEN the RAG_Application starts, THE Ollama_Client SHALL verify connectivity to the Ollama server
5. THE Ollama_Client SHALL send prompts to Ollama using the Ollama API format
6. THE Ollama_Client SHALL receive and parse responses from Ollama
7. THE Ollama_Client SHALL support both streaming and non-streaming responses from Ollama
8. IF the Ollama server is unreachable, THEN THE Ollama_Client SHALL log an error and return a service unavailable response

### Requirement 3: Document Processing Configuration

**User Story:** As a system administrator, I want to configure document processing parameters, so that I can control where documents are stored and how often they are processed.

#### Acceptance Criteria

1. THE Configuration SHALL include `documents.input-folder` property specifying the folder path to monitor
2. THE Configuration SHALL include `processing.schedule` property specifying the cron expression for job execution
3. THE Configuration SHALL include `processing.chunk-size` property specifying the fixed chunk size in characters
4. THE Configuration SHALL include `processing.chunk-overlap` property specifying the overlap between chunks in characters
5. THE Configuration SHALL include `qdrant.host` property specifying the Qdrant server host
6. THE Configuration SHALL include `qdrant.port` property specifying the Qdrant server port
7. THE Configuration SHALL include `qdrant.collection-name` property specifying the collection name for storing embeddings
8. WHEN the RAG_Application starts, THE RAG_Application SHALL validate all required configuration properties are present
9. IF required configuration properties are missing, THEN THE RAG_Application SHALL fail to start with a descriptive error message

### Requirement 4: PDF Document Processing

**User Story:** As a content manager, I want the system to process PDF files, so that their content can be used for RAG operations.

#### Acceptance Criteria

1. THE Document_Processor SHALL scan the configured input folder for PDF files
2. WHEN a PDF file is found, THE Document_Processor SHALL extract text content from all pages
3. THE Document_Processor SHALL preserve text order and structure during extraction
4. IF a PDF file cannot be read, THEN THE Document_Processor SHALL log an error and continue processing other files
5. THE Document_Processor SHALL pass extracted text to the Chunking_Service

### Requirement 5: Image Document Processing

**User Story:** As a content manager, I want the system to process image files, so that text within images can be used for RAG operations.

#### Acceptance Criteria

1. THE Document_Processor SHALL scan the configured input folder for image files with extensions jpg, jpeg, png, and tiff
2. WHEN an image file is found, THE Document_Processor SHALL use Ollama vision-capable models to extract text content
3. IF an image file cannot be read, THEN THE Document_Processor SHALL log an error and continue processing other files
4. THE Document_Processor SHALL pass extracted text to the Chunking_Service

### Requirement 6: Text Chunking

**User Story:** As a system architect, I want documents to be split into fixed-size chunks, so that embeddings can represent manageable text segments for retrieval.

#### Acceptance Criteria

1. THE Chunking_Service SHALL split text into chunks using the fixed size specified in Configuration
2. THE Chunking_Service SHALL apply the overlap size specified in Configuration between consecutive chunks
3. THE Chunking_Service SHALL preserve word boundaries when splitting text
4. THE Chunking_Service SHALL include document metadata with each chunk including source filename and chunk index
5. WHEN text is shorter than the chunk size, THE Chunking_Service SHALL create a single chunk containing the entire text
6. THE Chunking_Service SHALL pass chunks to the Vector_Store_Client for embedding generation and storage

### Requirement 7: Vector Storage in Qdrant

**User Story:** As a system architect, I want document chunks to be stored as vector embeddings in Qdrant, so that semantic similarity search can be performed during RAG operations.

#### Acceptance Criteria

1. THE Vector_Store_Client SHALL connect to Qdrant using the host and port specified in Configuration
2. WHEN the RAG_Application starts, THE Vector_Store_Client SHALL verify the configured collection exists in Qdrant
3. IF the collection does not exist, THEN THE Vector_Store_Client SHALL create it with appropriate vector dimensions
4. THE Vector_Store_Client SHALL generate embeddings for each chunk using the Ollama embedding model
5. THE Vector_Store_Client SHALL store embeddings in Qdrant with associated metadata including source filename and chunk text
6. THE Vector_Store_Client SHALL support batch insertion of multiple embeddings
7. IF Qdrant is unreachable, THEN THE Vector_Store_Client SHALL log an error and retry with exponential backoff

### Requirement 8: Scheduled Document Processing Job

**User Story:** As a system administrator, I want documents to be processed automatically on a schedule, so that new documents are indexed without manual intervention.

#### Acceptance Criteria

1. THE Processing_Job SHALL execute according to the cron schedule specified in Configuration
2. WHEN the Processing_Job executes, THE Processing_Job SHALL process all documents in the configured input folder
3. THE Processing_Job SHALL track which documents have been processed to avoid reprocessing unchanged files
4. THE Processing_Job SHALL process only new or modified documents since the last execution
5. THE Processing_Job SHALL log the start time, end time, and number of documents processed
6. IF the Processing_Job fails, THEN THE Processing_Job SHALL log the error and continue on the next scheduled execution

### Requirement 9: On-Demand Document Processing

**User Story:** As a system administrator, I want to trigger document processing on-demand, so that I can immediately index new documents without waiting for the scheduled job.

#### Acceptance Criteria

1. THE RAG_Application SHALL expose a POST endpoint at `/api/processing/trigger` for on-demand processing
2. WHEN the trigger endpoint is called, THE Processing_Job SHALL execute immediately
3. THE trigger endpoint SHALL return a response indicating whether processing started successfully
4. THE trigger endpoint SHALL include processing status and document count in the response
5. IF processing is already running, THEN THE trigger endpoint SHALL return a conflict status indicating processing is in progress

### Requirement 10: RAG Query Processing

**User Story:** As an OpenWebUI user, I want my prompts to be augmented with relevant document context, so that I receive more accurate and contextual responses.

#### Acceptance Criteria

1. WHEN a chat completion request is received, THE Query_Handler SHALL extract the user prompt from the request
2. THE Query_Handler SHALL generate an embedding for the user prompt using the Ollama embedding model
3. THE Query_Handler SHALL query Qdrant for the top 5 most similar chunks based on vector similarity
4. THE Query_Handler SHALL construct an augmented prompt by prepending retrieved chunks to the original user prompt
5. THE Query_Handler SHALL format the augmented prompt with clear separation between context and user question
6. THE Query_Handler SHALL send the augmented prompt to Ollama via the Ollama_Client
7. WHEN Ollama returns a response, THE Query_Handler SHALL format it according to OpenAI response format
8. THE Query_Handler SHALL return the formatted response to the client
9. IF no relevant chunks are found, THEN THE Query_Handler SHALL send the original prompt to Ollama without augmentation

### Requirement 11: Streaming Response Support

**User Story:** As an OpenWebUI user, I want to receive streaming responses, so that I can see the response being generated in real-time.

#### Acceptance Criteria

1. WHEN a chat completion request has `stream` set to true, THE Query_Handler SHALL enable streaming mode
2. THE Query_Handler SHALL establish a server-sent events connection with the client
3. THE Query_Handler SHALL forward streaming tokens from Ollama to the client as they are received
4. THE Query_Handler SHALL format each streaming chunk according to OpenAI streaming format
5. WHEN streaming completes, THE Query_Handler SHALL send a final `[DONE]` message
6. IF an error occurs during streaming, THEN THE Query_Handler SHALL send an error event and close the stream

### Requirement 12: Error Handling and Logging

**User Story:** As a system administrator, I want comprehensive error handling and logging, so that I can diagnose and resolve issues quickly.

#### Acceptance Criteria

1. THE RAG_Application SHALL log all incoming API requests with timestamp and endpoint
2. THE RAG_Application SHALL log all errors with stack traces and contextual information
3. WHEN an external service is unavailable, THE RAG_Application SHALL log the service name and connection details
4. THE RAG_Application SHALL log document processing progress including files processed and chunks created
5. THE RAG_Application SHALL log vector storage operations including number of embeddings stored
6. THE RAG_Application SHALL use appropriate log levels: DEBUG for detailed information, INFO for normal operations, WARN for recoverable issues, ERROR for failures
7. THE Configuration SHALL include logging configuration for log levels and output destinations

### Requirement 13: Application Health and Monitoring

**User Story:** As a system administrator, I want to monitor the application health, so that I can ensure all components are functioning correctly.

#### Acceptance Criteria

1. THE RAG_Application SHALL expose a GET endpoint at `/actuator/health` that returns overall application health status
2. THE health endpoint SHALL include health status for Ollama connectivity
3. THE health endpoint SHALL include health status for Qdrant connectivity
4. THE health endpoint SHALL include health status for document processing job
5. WHEN all components are healthy, THE health endpoint SHALL return HTTP status 200
6. IF any component is unhealthy, THEN THE health endpoint SHALL return HTTP status 503 with details about failing components

### Requirement 14: Graceful Startup and Shutdown

**User Story:** As a system administrator, I want the application to start and stop gracefully, so that no data is lost and resources are properly released.

#### Acceptance Criteria

1. WHEN the RAG_Application starts, THE RAG_Application SHALL verify connectivity to all external services before accepting requests
2. THE RAG_Application SHALL initialize the scheduled Processing_Job after successful startup
3. WHEN the RAG_Application receives a shutdown signal, THE RAG_Application SHALL stop accepting new requests
4. THE RAG_Application SHALL complete in-flight requests before shutting down with a timeout of 30 seconds
5. THE RAG_Application SHALL close all connections to external services during shutdown
6. IF the Processing_Job is running during shutdown, THEN THE RAG_Application SHALL wait for it to complete or timeout after 60 seconds

### Requirement 15: Ollama Model Configuration

**User Story:** As a system administrator, I want to configure which Ollama model to use, so that I can select the most appropriate model for my use case without modifying code.

#### Acceptance Criteria

1. THE Configuration SHALL include `ollama.model-name` property specifying the Ollama model to use for text generation
2. THE Configuration SHALL include `ollama.embedding-model-name` property specifying the Ollama model to use for generating embeddings
3. THE Configuration SHALL include `ollama.vision-model-name` property specifying the Ollama vision model to use for image text extraction with default value `qwen3-vl:8b`
4. THE Ollama_Client SHALL use the model name specified in Configuration when sending generation requests
5. THE Vector_Store_Client SHALL use the embedding model name specified in Configuration when generating embeddings
6. THE Document_Processor SHALL use the vision model name specified in Configuration when extracting text from images
7. WHEN the RAG_Application starts, THE RAG_Application SHALL validate that the configured model names are not empty
8. IF model name configuration properties are missing, THEN THE RAG_Application SHALL fail to start with a descriptive error message

### Requirement 16: Functional Programming Style

**User Story:** As a developer, I want the codebase to follow functional programming principles, so that the code is more maintainable, testable, and easier to reason about.

#### Acceptance Criteria

1. THE RAG_Application SHALL use Java Stream API for collection processing operations
2. THE RAG_Application SHALL use CompletableFuture for asynchronous operations including external service calls
3. THE RAG_Application SHALL use functional interfaces and lambda expressions instead of anonymous classes
4. THE RAG_Application SHALL use immutable data structures for data transfer objects and domain models
5. THE RAG_Application SHALL avoid mutable state in service classes where possible
6. THE RAG_Application SHALL use method references where applicable to improve code readability
7. THE RAG_Application SHALL compose operations using functional composition patterns

### Requirement 17: Technology Stack Compliance

**User Story:** As a system architect, I want the application to use modern, supported technology versions, so that the application benefits from the latest features, performance improvements, and security updates.

#### Acceptance Criteria

1. THE RAG_Application SHALL use Java 25 as the runtime and compilation target
2. THE RAG_Application SHALL use Spring Boot 4 and its ecosystem libraries
3. THE RAG_Application SHALL use Gradle 9.2 as the build tool
4. THE Configuration SHALL specify Java 25 compatibility in the build configuration
5. THE Configuration SHALL specify Spring Boot 4 version in the dependency management
6. THE Configuration SHALL specify Gradle 9.2 in the Gradle wrapper configuration
7. WHEN the RAG_Application is built, THE build process SHALL verify Java 25 compatibility
8. WHEN the RAG_Application is built, THE build process SHALL resolve all dependencies compatible with Spring Boot 4

### Requirement 18: Testing Standards and Conventions

**User Story:** As a developer, I want all tests to follow consistent naming and structure conventions, so that tests are readable, maintainable, and clearly communicate their intent.

#### Acceptance Criteria

1. THE RAG_Application SHALL include @DisplayName annotation on all test methods with descriptions in When-Then style format
2. THE @DisplayName annotation SHALL follow the pattern "When [condition or action] Then [expected outcome]"
3. THE RAG_Application SHALL structure each test method into three distinct sections marked with comments
4. THE first section SHALL be marked with "// Given" comment and contain test setup and arrangement code
5. THE second section SHALL be marked with "// When" comment and contain the action or operation being tested
6. THE third section SHALL be marked with "// Then" comment and contain assertions and verification code
7. THE RAG_Application SHALL maintain this structure consistently across all unit tests, integration tests, and functional tests

### Requirement 19: File Hash-Based Change Detection

**User Story:** As a system administrator, I want the application to detect document changes using file hashes, so that document processing is accurate and not affected by timestamp inconsistencies.

#### Acceptance Criteria

1. THE Document_Processor SHALL compute a SHA-256 hash for each document file before processing
2. THE Document_Processor SHALL use the file hash to uniquely identify document content
3. WHEN a document is processed, THE Document_Processor SHALL store the file hash in the Hash_Store
4. THE Document_Processor SHALL compare the computed hash with the stored hash to detect changes
5. WHEN a file hash matches the stored hash, THE Document_Processor SHALL skip processing that file
6. WHEN a file hash differs from the stored hash, THE Document_Processor SHALL treat the file as modified
7. THE Document_Processor SHALL compute hashes using the entire file contents not just metadata

### Requirement 20: Redis Integration for Hash Storage

**User Story:** As a system architect, I want to use Redis for storing file hashes, so that the application has fast access to processing metadata without requiring a heavyweight database.

#### Acceptance Criteria

1. THE Configuration SHALL include `redis.host` property specifying the Redis server host
2. THE Configuration SHALL include `redis.port` property specifying the Redis server port with default value 6379
3. THE Redis_Client SHALL connect to Redis using the host and port specified in Configuration
4. WHEN the RAG_Application starts, THE Redis_Client SHALL verify connectivity to the Redis server
5. THE Redis_Client SHALL store file hashes using the file path as the key and hash value as the value
6. THE Redis_Client SHALL support retrieving stored hashes by file path
7. THE Redis_Client SHALL support updating hash values when files are reprocessed
8. IF Redis is unreachable, THEN THE Redis_Client SHALL log an error and the Processing_Job SHALL process all files as if they were new

### Requirement 21: Skip Already Processed Files

**User Story:** As a system administrator, I want the application to skip files that have already been processed, so that processing time and resources are not wasted on unchanged documents.

#### Acceptance Criteria

1. WHEN the Processing_Job executes, THE Processing_Job SHALL retrieve stored hashes for all files from the Hash_Store
2. THE Processing_Job SHALL compute the current hash for each file in the input folder
3. WHEN a file hash matches the stored hash, THE Processing_Job SHALL skip that file and log it as already processed
4. WHEN a file hash does not match the stored hash, THE Processing_Job SHALL process that file
5. WHEN a file is not found in the Hash_Store, THE Processing_Job SHALL process that file as new
6. THE Processing_Job SHALL log the count of skipped files and processed files separately
7. THE Processing_Job SHALL complete successfully even if all files are skipped

### Requirement 22: Update Vector DB on File Changes

**User Story:** As a content manager, I want modified documents to update their embeddings in the vector database, so that search results always reflect the current document content.

#### Acceptance Criteria

1. WHEN a file hash differs from the stored hash, THE Document_Processor SHALL identify the file as modified
2. THE Vector_Store_Client SHALL delete all existing embeddings associated with the modified file from Qdrant
3. THE Vector_Store_Client SHALL use the source filename metadata to identify embeddings to delete
4. WHEN old embeddings are deleted, THE Document_Processor SHALL process the modified file to generate new chunks
5. THE Vector_Store_Client SHALL store the new embeddings in Qdrant with updated metadata
6. THE Redis_Client SHALL update the stored hash with the new hash value after successful processing
7. IF deletion of old embeddings fails, THEN THE Processing_Job SHALL log an error and skip processing that file

### Requirement 23: Docker Deployment Configuration

**User Story:** As a system administrator, I want to deploy the application using Docker containers, so that I can easily manage dependencies and ensure consistent deployment across environments.

#### Acceptance Criteria

1. THE RAG_Application SHALL include a Dockerfile that builds the Spring Boot application into a container image
2. THE Dockerfile SHALL use a Java 25 base image
3. THE Dockerfile SHALL expose the application port for external access
4. THE RAG_Application SHALL include a docker-compose.yml file that orchestrates all services
5. THE docker-compose.yml SHALL define services for the Spring application, Qdrant, and Redis
6. THE docker-compose.yml SHALL configure a volume mount for the documents folder from the host machine to a container path
7. THE docker-compose.yml SHALL configure network settings to allow the Spring container to access Ollama on the host using host.docker.internal
8. THE docker-compose.yml SHALL define health checks for all services
9. THE docker-compose.yml SHALL configure service dependencies to ensure Qdrant and Redis start before the Spring application
10. THE Configuration SHALL support environment variable overrides for all connection properties including Ollama host, Qdrant host, and Redis host

### Requirement 24: Gradle Wrapper for Build Automation

**User Story:** As a developer, I want the application to use Gradle wrapper, so that I can build the project with a consistent Gradle version without requiring a global Gradle installation.

#### Acceptance Criteria

1. THE RAG_Application SHALL include Gradle wrapper scripts gradlew for Unix-based systems and gradlew.bat for Windows
2. THE RAG_Application SHALL include gradle-wrapper.jar and gradle-wrapper.properties in the gradle/wrapper directory
3. THE gradle-wrapper.properties SHALL specify Gradle version 9.2
4. WHEN a developer runs gradlew build, THE Gradle wrapper SHALL download the specified Gradle version if not already cached
5. THE Gradle wrapper SHALL execute build tasks using the specified Gradle version
6. THE RAG_Application SHALL include the Gradle wrapper files in version control
7. THE build documentation SHALL instruct developers to use gradlew instead of a global gradle command

### Requirement 25: Simple Test Endpoint

**User Story:** As a developer, I want a simple test endpoint that accepts plain text and returns plain text, so that I can quickly test the RAG functionality without formatting OpenAI-style requests.

#### Acceptance Criteria

1. THE RAG_Application SHALL expose a POST endpoint at `/api/test/query` for simple testing
2. THE test endpoint SHALL accept plain text in the request body as the prompt
3. WHEN a request is received, THE test endpoint SHALL pass the plain text prompt to the Query_Handler
4. THE Query_Handler SHALL process the prompt using the same RAG pipeline as the OpenAI endpoint
5. THE test endpoint SHALL return only the generated text response as plain text without OpenAI formatting
6. THE test endpoint SHALL use Content-Type text/plain for both request and response
7. IF an error occurs during processing, THEN THE test endpoint SHALL return an error message as plain text with appropriate HTTP status code

### Requirement 26: OpenAPI Specification and Swagger UI

**User Story:** As a developer, I want OpenAPI documentation for the API endpoints, so that I can understand the API contract and test endpoints interactively.

#### Acceptance Criteria

1. THE RAG_Application SHALL include OpenAPI 3.0 specification for all public API endpoints
2. THE OpenAPI specification SHALL document the `/api/test/query` endpoint with request body schema, response schema, and example values
3. THE OpenAPI specification SHALL document the `/api/processing/trigger` endpoint with response schema and status codes
4. THE OpenAPI specification SHALL include descriptions for all endpoints explaining their purpose and behavior
5. THE RAG_Application SHALL expose Swagger UI at `/swagger-ui.html` for interactive API documentation
6. THE Swagger UI SHALL allow developers to execute API requests directly from the browser
7. THE OpenAPI specification SHALL include example request and response payloads for each endpoint
8. THE RAG_Application SHALL generate the OpenAPI specification automatically from code annotations

## Future Enhancements

The following capabilities are identified for future implementation:

- **Semantic Chunking**: Replace fixed-size chunking with semantic-aware chunking that preserves meaning boundaries
- **Multiple Embedding Models**: Support for different embedding models beyond Ollama's default
- **Query Result Caching**: Cache frequently asked questions to reduce latency
- **Multi-tenancy**: Support for multiple isolated document collections
- **Authentication and Authorization**: Secure API endpoints with token-based authentication

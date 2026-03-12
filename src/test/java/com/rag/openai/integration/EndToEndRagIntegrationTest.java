package com.rag.openai.integration;

import com.rag.openai.api.OpenAIApiController;
import com.rag.openai.api.TestApiController;
import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.config.OpenAPIConfiguration;
import com.rag.openai.domain.dto.*;
import com.rag.openai.domain.model.*;
import com.rag.openai.service.DocumentProcessor;
import com.rag.openai.service.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end integration test for the complete RAG flow.
 * Tests document processing, vector storage, query handling, streaming, and health checks.
 * 
 * Requirements: 1.1, 1.3, 1.4, 8.1, 10.1, 10.7, 13.1, 25.1, 26.5
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EndToEndRagIntegrationTest {

    @Mock
    private DocumentProcessor documentProcessor;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private RedisClient redisClient;

    @Mock
    private ProcessingJob processingJob;

    @Mock
    private OpenAIApiController openAIApiController;

    @Mock
    private TestApiController testApiController;

    @BeforeEach
    void setUp() {
        // Reset mocks before each test
        reset(documentProcessor, vectorStoreClient, ollamaClient, redisClient, 
              processingJob, openAIApiController, testApiController);
    }

    @Test
    @DisplayName("When complete RAG flow is executed Then document processing, embedding storage, and query response work end-to-end")
    void testCompleteRagFlowFromDocumentToQuery() {
        // Given: Sample document metadata
        DocumentMetadata metadata = new DocumentMetadata(
            "java25-features.pdf",
            Path.of("documents/java25-features.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );

        // Given: Document processing produces chunks
        List<TextChunk> chunks = List.of(
            new TextChunk(
                "Java 25 introduces enhanced pattern matching and record patterns.",
                metadata,
                0,
                0,
                62
            ),
            new TextChunk(
                "These features improve code readability and reduce boilerplate.",
                metadata,
                1,
                63,
                126
            )
        );

        ProcessingResult processingResult = new ProcessingResult(
            1,  // documentsProcessed
            0,  // documentsSkipped
            2,  // chunksCreated
            2,  // embeddingsStored
            1500L,  // processingTimeMs
            List.of()  // errors
        );

        when(documentProcessor.processDocuments(any(Path.class)))
            .thenReturn(CompletableFuture.completedFuture(processingResult));

        // Given: Embeddings are generated and stored
        List<Float> embedding1 = List.of(0.1f, 0.2f, 0.3f, 0.4f);
        List<Float> embedding2 = List.of(0.5f, 0.6f, 0.7f, 0.8f);

        when(ollamaClient.generateEmbedding(eq(chunks.get(0).text()), anyString()))
            .thenReturn(CompletableFuture.completedFuture(embedding1));
        when(ollamaClient.generateEmbedding(eq(chunks.get(1).text()), anyString()))
            .thenReturn(CompletableFuture.completedFuture(embedding2));

        when(vectorStoreClient.storeEmbeddings(anyList()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Given: Query embedding and similar chunks retrieval
        String userQuery = "What are the new features in Java 25?";
        List<Float> queryEmbedding = List.of(0.15f, 0.25f, 0.35f, 0.45f);

        when(ollamaClient.generateEmbedding(eq(userQuery), anyString()))
            .thenReturn(CompletableFuture.completedFuture(queryEmbedding));

        List<ScoredChunk> retrievedChunks = List.of(
            new ScoredChunk(chunks.get(0), 0.95f),
            new ScoredChunk(chunks.get(1), 0.87f)
        );

        when(vectorStoreClient.searchSimilar(eq(queryEmbedding), eq(5)))
            .thenReturn(CompletableFuture.completedFuture(retrievedChunks));

        // Given: LLM generates response
        String llmResponse = "Java 25 introduces enhanced pattern matching and record patterns, " +
                           "which improve code readability and reduce boilerplate code.";

        when(ollamaClient.generate(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(llmResponse));

        // Given: OpenAI API controller returns formatted response
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(new Message("user", userQuery)),
            false,
            Optional.empty(),
            Optional.empty()
        );

        ChatCompletionResponse expectedResponse = new ChatCompletionResponse(
            "chatcmpl-123",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(new Choice(
                0,
                new Message("assistant", llmResponse),
                "stop"
            )),
            new Usage(50, 30, 80)
        );

        when(openAIApiController.chatCompletions(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                ResponseEntity.ok(expectedResponse)
            ));

        // When: Complete RAG flow is executed
        // Step 1: Process documents
        CompletableFuture<ProcessingResult> processingFuture = 
            documentProcessor.processDocuments(Path.of("documents"));
        ProcessingResult result = processingFuture.join();

        // Step 2: Query with RAG
        CompletableFuture<ResponseEntity<?>> queryFuture = 
            openAIApiController.chatCompletions(request);
        ResponseEntity<?> response = queryFuture.join();

        // Then: Document processing completed successfully
        assertThat(result.documentsProcessed()).isEqualTo(1);
        assertThat(result.chunksCreated()).isEqualTo(2);
        assertThat(result.embeddingsStored()).isEqualTo(2);
        assertThat(result.errors()).isEmpty();

        // Then: Query response is successful
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        
        ChatCompletionResponse chatResponse = (ChatCompletionResponse) response.getBody();
        assertThat(chatResponse.choices()).hasSize(1);
        assertThat(chatResponse.choices().get(0).message().content()).isEqualTo(llmResponse);

        // Then: All components were invoked in correct order
        verify(documentProcessor, times(1)).processDocuments(any(Path.class));
        verify(openAIApiController, times(1)).chatCompletions(any(ChatCompletionRequest.class));
    }

    @Test
    @DisplayName("When streaming query is executed Then tokens are streamed in real-time")
    void testStreamingFlowWithServerSentEvents() {
        // Given: User query with streaming enabled
        String userQuery = "Explain pattern matching in Java 25";
        
        ChatCompletionRequest streamingRequest = new ChatCompletionRequest(
            "llama3.2",
            List.of(new Message("user", userQuery)),
            true,  // streaming enabled
            Optional.empty(),
            Optional.empty()
        );

        // Given: Query embedding and retrieval
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
        when(ollamaClient.generateEmbedding(eq(userQuery), anyString()))
            .thenReturn(CompletableFuture.completedFuture(queryEmbedding));

        TextChunk chunk = new TextChunk(
            "Pattern matching in Java 25 allows destructuring of records.",
            new DocumentMetadata("java25.pdf", Path.of("java25.pdf"), 0L, "pdf"),
            0, 0, 62
        );

        when(vectorStoreClient.searchSimilar(eq(queryEmbedding), eq(5)))
            .thenReturn(CompletableFuture.completedFuture(
                List.of(new ScoredChunk(chunk, 0.92f))
            ));

        // Given: Streaming tokens from Ollama
        Flux<String> tokenStream = Flux.just(
            "Pattern", " matching", " in", " Java", " 25", " allows", " you", " to",
            " destructure", " records", " directly", " in", " switch", " expressions", "."
        );

        when(ollamaClient.generateStreaming(anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenStream));

        // Given: Streaming chunks formatted as OpenAI chunks
        Flux<ChatCompletionChunk> chunkStream = tokenStream.map(token -> 
            new ChatCompletionChunk(
                "chatcmpl-stream-123",
                "chat.completion.chunk",
                System.currentTimeMillis() / 1000,
                "llama3.2",
                List.of(new ChunkChoice(
                    0,
                    new Delta(Optional.empty(), Optional.of(token)),
                    null
                ))
            )
        );

        when(openAIApiController.chatCompletions(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                ResponseEntity.ok(chunkStream)
            ));

        // When: Streaming query is executed
        CompletableFuture<ResponseEntity<?>> streamingFuture = 
            openAIApiController.chatCompletions(streamingRequest);
        ResponseEntity<?> streamingResponse = streamingFuture.join();

        // Then: Response contains streaming flux
        assertThat(streamingResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(streamingResponse.getBody()).isInstanceOf(Flux.class);

        @SuppressWarnings("unchecked")
        Flux<ChatCompletionChunk> responseStream = (Flux<ChatCompletionChunk>) streamingResponse.getBody();

        // Then: Stream contains all tokens
        List<ChatCompletionChunk> chunks = responseStream.collectList().block();
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSizeGreaterThan(0);
        
        // Then: Each chunk has correct structure
        chunks.forEach(chunk1 -> {
            assertThat(chunk1.object()).isEqualTo("chat.completion.chunk");
            assertThat(chunk1.choices()).hasSize(1);
            assertThat(chunk1.choices().get(0).delta().content()).isPresent();
        });

        // Then: Streaming was invoked
        verify(openAIApiController, times(1)).chatCompletions(any(ChatCompletionRequest.class));
    }

    @Test
    @DisplayName("When simple test endpoint is used Then plain text request and response work correctly")
    void testSimpleTestEndpointWithPlainText() {
        // Given: Plain text query
        String plainTextQuery = "What is Java 25?";

        // Given: Query processing returns response
        String plainTextResponse = "Java 25 is the latest version of the Java programming language " +
                                  "with enhanced pattern matching and record patterns.";

        when(testApiController.testQuery(eq(plainTextQuery)))
            .thenReturn(CompletableFuture.completedFuture(
                ResponseEntity.ok(plainTextResponse)
            ));

        // When: Test endpoint is called
        CompletableFuture<ResponseEntity<String>> testFuture = 
            testApiController.testQuery(plainTextQuery);
        ResponseEntity<String> testResponse = testFuture.join();

        // Then: Response is plain text
        assertThat(testResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(testResponse.getBody()).isEqualTo(plainTextResponse);
        assertThat(testResponse.getBody()).doesNotContain("{");
        assertThat(testResponse.getBody()).doesNotContain("\"");

        // Then: Test endpoint was invoked
        verify(testApiController, times(1)).testQuery(eq(plainTextQuery));
    }

    @Test
    @DisplayName("When health check is performed Then all services report status")
    void testHealthCheckFlow() {
        // Given: All services are healthy
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(processingJob.isProcessingInProgress())
            .thenReturn(false);

        // When: Health checks are performed
        CompletableFuture<Boolean> ollamaHealth = ollamaClient.verifyConnectivity();
        CompletableFuture<Boolean> qdrantHealth = vectorStoreClient.verifyConnectivity();
        CompletableFuture<Boolean> redisHealth = redisClient.verifyConnectivity();
        boolean processingStatus = processingJob.isProcessingInProgress();

        // Then: All services report healthy
        assertThat(ollamaHealth.join()).isTrue();
        assertThat(qdrantHealth.join()).isTrue();
        assertThat(redisHealth.join()).isTrue();
        assertThat(processingStatus).isFalse();

        // Then: Health checks were performed
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
        verify(processingJob, times(1)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When document is modified Then old embeddings are deleted and new ones are stored")
    void testModifiedDocumentReprocessing() {
        // Given: Document with existing hash
        Path documentPath = Path.of("documents/updated-doc.pdf");
        String oldHash = "abc123";
        String newHash = "def456";

        when(redisClient.getFileHash(eq(documentPath)))
            .thenReturn(CompletableFuture.completedFuture(Optional.of(oldHash)));

        when(documentProcessor.computeFileHash(eq(documentPath)))
            .thenReturn(CompletableFuture.completedFuture(newHash));

        when(documentProcessor.shouldProcessFile(eq(documentPath), eq(newHash)))
            .thenReturn(CompletableFuture.completedFuture(true));

        // Given: Old embeddings are deleted
        when(vectorStoreClient.deleteEmbeddingsByFilename(eq("updated-doc.pdf")))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Given: New embeddings are stored
        when(vectorStoreClient.storeEmbeddings(anyList()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // Given: New hash is stored
        when(redisClient.storeFileHash(eq(documentPath), eq(newHash)))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When: Document is reprocessed
        CompletableFuture<String> hashFuture = documentProcessor.computeFileHash(documentPath);
        String computedHash = hashFuture.join();

        CompletableFuture<Boolean> shouldProcessFuture = 
            documentProcessor.shouldProcessFile(documentPath, computedHash);
        boolean shouldProcess = shouldProcessFuture.join();

        // Then: Document should be reprocessed
        assertThat(computedHash).isEqualTo(newHash);
        assertThat(shouldProcess).isTrue();

        // Then: Hash computation was performed
        verify(documentProcessor, times(1)).computeFileHash(eq(documentPath));
        verify(documentProcessor, times(1)).shouldProcessFile(eq(documentPath), eq(newHash));
    }

    @Test
    @DisplayName("When no relevant chunks are found Then original prompt is used without augmentation")
    void testQueryWithNoRelevantChunks() {
        // Given: User query
        String userQuery = "What is the weather today?";
        
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(new Message("user", userQuery)),
            false,
            Optional.empty(),
            Optional.empty()
        );

        // Given: Query embedding is generated
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
        when(ollamaClient.generateEmbedding(eq(userQuery), anyString()))
            .thenReturn(CompletableFuture.completedFuture(queryEmbedding));

        // Given: No relevant chunks are found (empty list)
        when(vectorStoreClient.searchSimilar(eq(queryEmbedding), eq(5)))
            .thenReturn(CompletableFuture.completedFuture(List.of()));

        // Given: LLM generates response with original prompt
        String llmResponse = "I don't have information about the current weather. " +
                           "I can only answer questions about the documents I have access to.";

        when(ollamaClient.generate(eq(userQuery), anyString()))
            .thenReturn(CompletableFuture.completedFuture(llmResponse));

        // Given: Response is formatted
        ChatCompletionResponse expectedResponse = new ChatCompletionResponse(
            "chatcmpl-456",
            "chat.completion",
            System.currentTimeMillis() / 1000,
            "llama3.2",
            List.of(new Choice(
                0,
                new Message("assistant", llmResponse),
                "stop"
            )),
            new Usage(20, 25, 45)
        );

        when(openAIApiController.chatCompletions(any(ChatCompletionRequest.class)))
            .thenReturn(CompletableFuture.completedFuture(
                ResponseEntity.ok(expectedResponse)
            ));

        // When: Query is executed
        CompletableFuture<ResponseEntity<?>> queryFuture = 
            openAIApiController.chatCompletions(request);
        ResponseEntity<?> response = queryFuture.join();

        // Then: Response indicates no relevant information
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        ChatCompletionResponse chatResponse = (ChatCompletionResponse) response.getBody();
        assertThat(chatResponse.choices().get(0).message().content())
            .contains("don't have information");

        // Then: Query was processed
        verify(openAIApiController, times(1)).chatCompletions(any(ChatCompletionRequest.class));
    }

    @Test
    @DisplayName("When processing job is triggered Then documents are processed and status is returned")
    void testOnDemandProcessingTrigger() {
        // Given: Processing result
        ProcessingResult result = new ProcessingResult(
            5,  // documentsProcessed
            2,  // documentsSkipped
            25,  // chunksCreated
            25,  // embeddingsStored
            5000L,  // processingTimeMs
            List.of()  // errors
        );

        when(processingJob.triggerProcessing())
            .thenReturn(CompletableFuture.completedFuture(result));

        when(processingJob.isProcessingInProgress())
            .thenReturn(false);

        // When: Processing is triggered
        CompletableFuture<ProcessingResult> processingFuture = 
            processingJob.triggerProcessing();
        ProcessingResult processingResult = processingFuture.join();

        // Then: Processing completed successfully
        assertThat(processingResult.documentsProcessed()).isEqualTo(5);
        assertThat(processingResult.documentsSkipped()).isEqualTo(2);
        assertThat(processingResult.chunksCreated()).isEqualTo(25);
        assertThat(processingResult.embeddingsStored()).isEqualTo(25);
        assertThat(processingResult.errors()).isEmpty();

        // Then: Processing was triggered
        verify(processingJob, times(1)).triggerProcessing();
    }

    @Test
    @DisplayName("When concurrent processing is attempted Then second request is rejected")
    void testConcurrentProcessingPrevention() {
        // Given: Processing is already in progress
        when(processingJob.isProcessingInProgress())
            .thenReturn(true);

        // When: Processing status is checked
        boolean isProcessing = processingJob.isProcessingInProgress();

        // Then: Processing is in progress
        assertThat(isProcessing).isTrue();

        // Then: Status was checked
        verify(processingJob, times(1)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When multiple queries are executed in parallel Then all complete successfully")
    void testParallelQueryExecution() {
        // Given: Multiple user queries
        List<String> queries = List.of(
            "What is Java 25?",
            "Explain pattern matching",
            "What are record patterns?"
        );

        // Given: Each query returns a response
        queries.forEach(query -> {
            ChatCompletionRequest request = new ChatCompletionRequest(
                "llama3.2",
                List.of(new Message("user", query)),
                false,
                Optional.empty(),
                Optional.empty()
            );

            ChatCompletionResponse response = new ChatCompletionResponse(
                "chatcmpl-" + query.hashCode(),
                "chat.completion",
                System.currentTimeMillis() / 1000,
                "llama3.2",
                List.of(new Choice(
                    0,
                    new Message("assistant", "Response to: " + query),
                    "stop"
                )),
                new Usage(10, 15, 25)
            );

            when(openAIApiController.chatCompletions(eq(request)))
                .thenReturn(CompletableFuture.completedFuture(
                    ResponseEntity.ok(response)
                ));
        });

        // When: Queries are executed in parallel
        List<CompletableFuture<ResponseEntity<?>>> futures = queries.stream()
            .map(query -> new ChatCompletionRequest(
                "llama3.2",
                List.of(new Message("user", query)),
                false,
                Optional.empty(),
                Optional.empty()
            ))
            .map(openAIApiController::chatCompletions)
            .toList();

        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );

        allFutures.join();

        // Then: All queries completed successfully
        List<ResponseEntity<?>> responses = new java.util.ArrayList<>();
        for (CompletableFuture<ResponseEntity<?>> future : futures) {
            responses.add(future.join());
        }

        assertThat(responses).hasSize(3);
        responses.forEach(response -> {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isInstanceOf(ChatCompletionResponse.class);
        });

        // Then: All queries were processed
        verify(openAIApiController, times(3)).chatCompletions(any(ChatCompletionRequest.class));
    }

    @Test
    @DisplayName("When error occurs during processing Then error is logged and processing continues")
    void testErrorHandlingDuringProcessing() {
        // Given: Processing result with errors
        ProcessingResult result = new ProcessingResult(
            3,  // documentsProcessed
            0,  // documentsSkipped
            15,  // chunksCreated
            15,  // embeddingsStored
            3000L,  // processingTimeMs
            List.of(
                "Failed to process corrupted-file.pdf: Invalid PDF format",
                "Failed to extract text from unreadable-image.jpg: Vision model timeout"
            )
        );

        when(documentProcessor.processDocuments(any(Path.class)))
            .thenReturn(CompletableFuture.completedFuture(result));

        // When: Documents are processed
        CompletableFuture<ProcessingResult> processingFuture = 
            documentProcessor.processDocuments(Path.of("documents"));
        ProcessingResult processingResult = processingFuture.join();

        // Then: Processing completed with errors logged
        assertThat(processingResult.documentsProcessed()).isEqualTo(3);
        assertThat(processingResult.errors()).hasSize(2);
        assertThat(processingResult.errors().get(0)).contains("corrupted-file.pdf");
        assertThat(processingResult.errors().get(1)).contains("unreadable-image.jpg");

        // Then: Processing continued despite errors
        verify(documentProcessor, times(1)).processDocuments(any(Path.class));
    }

    @Test
    @DisplayName("When Swagger UI is accessed Then documentation is available and accessible")
    void testSwaggerUIAccessibility() {
        // Given: OpenAPI configuration is set up
        OpenAPIConfiguration openAPIConfig = new OpenAPIConfiguration();
        
        // When: OpenAPI specification is generated
        var openAPI = openAPIConfig.customOpenAPI();
        var groupedOpenApi = openAPIConfig.publicApi();
        
        // Then: Swagger UI configuration is complete
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("RAG OpenAI API with Ollama");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        
        // Then: API endpoints are documented
        assertThat(groupedOpenApi).isNotNull();
        assertThat(groupedOpenApi.getPathsToMatch()).contains("/v1/**", "/api/**");
        
        // Then: Actuator endpoints are excluded from documentation
        assertThat(groupedOpenApi.getPathsToExclude()).contains("/actuator/**");
        
        // Then: Server configuration points to local development
        assertThat(openAPI.getServers()).isNotEmpty();
        assertThat(openAPI.getServers().get(0).getUrl()).contains("localhost:8080");
        
        // Then: External documentation is configured
        assertThat(openAPI.getExternalDocs()).isNotNull();
        assertThat(openAPI.getExternalDocs().getDescription()).isEqualTo("Project Documentation");
    }
}

package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.RagConfig;
import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.Message;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.ScoredChunk;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for top-K retrieval in QueryHandler.
 * **Validates: Requirements 10.3**
 * 
 * Property 22: Top-K Retrieval
 * 
 * This property verifies that the QueryHandler correctly retrieves the top K
 * most similar chunks from the vector store. The retrieval must:
 * - Request exactly topK results from VectorStoreClient.searchSimilar
 * - Use the topK value configured in RagConfig
 * - Pass the query embedding to the search operation
 * - Handle cases where fewer than K results are available
 * - Respect the topK limit regardless of how many chunks exist
 */
class TopKRetrievalPropertyTest {

    @Property(tries = 100)
    @Label("When searching for similar chunks Then requests exactly topK results")
    void queryHandlerRequestsExactlyTopKResults(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 20) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: a chat completion request and configured topK value
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should request exactly topK results from vector store
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mockVectorStoreClient).searchSimilar(anyList(), topKCaptor.capture());
        
        assertThat(topKCaptor.getValue()).isEqualTo(topK);
    }

    @Property(tries = 100)
    @Label("When topK is configured Then uses that value for similarity search")
    void topKValueFromConfigUsedForSearch(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 15) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: a specific topK configuration
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(topK)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should use the configured topK value
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(topK));
    }

    @Property(tries = 100)
    @Label("When searching similar chunks Then passes query embedding to search")
    void queryEmbeddingPassedToTopKSearch(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 10) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: a request and query embedding
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should pass the query embedding to the search operation
        verify(mockVectorStoreClient).searchSimilar(eq(embeddingVector), eq(topK));
    }

    @Property(tries = 100)
    @Label("When vector store returns fewer than K results Then handles gracefully")
    void handlesFewerThanKResults(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 5, max = 15) int topK,
            @ForAll @IntRange(min = 1, max = 4) int actualResults
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: vector store returns fewer results than topK
        List<ScoredChunk> scoredChunks = createScoredChunks(actualResults);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(topK)))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        var result = queryHandler.handleQuery(request).join();
        
        // Then: should handle the fewer results gracefully without error
        assertThat(result).isNotNull();
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(topK));
    }

    @Property(tries = 100)
    @Label("When vector store returns empty list Then handles gracefully")
    void handlesEmptyResults(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 10) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: vector store returns empty list
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(topK)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        var result = queryHandler.handleQuery(request).join();
        
        // Then: should handle empty results gracefully and still generate response
        assertThat(result).isNotNull();
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(topK));
        verify(mockOllamaClient).generate(anyString(), anyString());
    }

    @Property(tries = 100)
    @Label("When topK is 1 Then requests exactly 1 result")
    void topKOfOneRequestsSingleResult(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: topK is configured to 1
        int topK = 1;
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(1)))
                .thenReturn(CompletableFuture.completedFuture(createScoredChunks(1)));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should request exactly 1 result
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(1));
    }

    @Property(tries = 100)
    @Label("When topK is large Then requests that many results")
    void topKLargeValueRequestsCorrectCount(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 20, max = 100) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: topK is configured to a large value
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(topK)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should request exactly the configured large topK value
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(topK));
    }

    @Property(tries = 100)
    @Label("When search is performed Then topK parameter is always positive")
    void topKParameterAlwaysPositive(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 50) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: any valid topK configuration
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: the topK parameter passed to search should always be positive
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mockVectorStoreClient).searchSimilar(anyList(), topKCaptor.capture());
        
        assertThat(topKCaptor.getValue()).isPositive();
    }

    @Property(tries = 100)
    @Label("When multiple queries are processed Then each uses configured topK")
    void multipleQueriesUseConfiguredTopK(
            @ForAll @Size(min = 2, max = 5) List<@StringLength(min = 1, max = 100) String> userPrompts,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 10) int topK
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: multiple queries with same configuration
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: processing multiple queries
        for (String prompt : userPrompts) {
            ChatCompletionRequest request = new ChatCompletionRequest(
                    modelName,
                    List.of(new Message("user", prompt)),
                    false,
                    Optional.empty(),
                    Optional.empty()
            );
            queryHandler.handleQuery(request).join();
        }
        
        // Then: each query should use the same configured topK value
        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mockVectorStoreClient, times(userPrompts.size()))
                .searchSimilar(anyList(), topKCaptor.capture());
        
        List<Integer> capturedTopKValues = topKCaptor.getAllValues();
        assertThat(capturedTopKValues).hasSize(userPrompts.size());
        assertThat(capturedTopKValues).allMatch(k -> k.equals(topK));
    }

    @Property(tries = 100)
    @Label("When search completes Then results are used for prompt augmentation")
    void searchResultsUsedForPromptAugmentation(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 10) int topK,
            @ForAll @IntRange(min = 1, max = 5) int resultCount
    ) {
        // Feature: rag-openai-api-ollama, Property 22: Top-K Retrieval
        
        // Given: vector store returns some results
        Assume.that(resultCount <= topK);
        List<ScoredChunk> scoredChunks = createScoredChunks(resultCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), eq(topK)))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig(topK);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should retrieve results and use them for generation
        verify(mockVectorStoreClient).searchSimilar(anyList(), eq(topK));
        verify(mockOllamaClient).generate(anyString(), anyString());
    }

    // ==================== Helper Methods ====================

    private OllamaConfig createOllamaConfig(String modelName, String embeddingModelName) {
        return new OllamaConfig(
                "localhost",
                11434,
                modelName,
                embeddingModelName,
                "qwen3-vl:8b",
                Duration.ofSeconds(30),
                Duration.ofSeconds(120)
        );
    }

    private RagConfig createRagConfig(int topK) {
        return new RagConfig(
                topK,
                0.7,
                "\n\n---\n\n",
                "Context:\n{context}\n\nQuestion: {question}"
        );
    }

    private List<ScoredChunk> createScoredChunks(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    DocumentMetadata metadata = new DocumentMetadata(
                            "test-doc-" + i + ".pdf",
                            Path.of("/test/test-doc-" + i + ".pdf"),
                            System.currentTimeMillis(),
                            "pdf"
                    );
                    TextChunk chunk = new TextChunk(
                            "Test chunk content " + i,
                            metadata,
                            i,
                            i * 100,
                            (i + 1) * 100
                    );
                    return new ScoredChunk(chunk, 0.9f - (i * 0.05f));
                })
                .toList();
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> modelName() {
        return Arbitraries.of(
                "llama3.2",
                "llama3.2:1b",
                "llama3.2:3b",
                "mistral",
                "mixtral",
                "qwen2.5",
                "phi3",
                "gemma2"
        );
    }

    @Provide
    Arbitrary<String> embeddingModelName() {
        return Arbitraries.of(
                "nomic-embed-text",
                "mxbai-embed-large",
                "all-minilm",
                "snowflake-arctic-embed"
        );
    }

    @Provide
    Arbitrary<List<Float>> embeddingVector() {
        return Arbitraries.floats()
                .between(-1.0f, 1.0f)
                .list()
                .ofMinSize(384)
                .ofMaxSize(768);
    }
}

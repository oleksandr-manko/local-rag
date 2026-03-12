package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.RagConfig;
import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.Message;
import com.rag.openai.domain.model.ScoredChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for query embedding generation in QueryHandler.
 * **Validates: Requirements 10.2**
 * 
 * Property 21: Query Embedding Generation
 * 
 * This property verifies that the QueryHandler correctly generates embeddings
 * for user prompts using the Ollama embedding model. The generation must:
 * - Extract the user prompt from the request
 * - Call OllamaClient.generateEmbedding with the extracted prompt
 * - Use the configured embedding model name from OllamaConfig
 * - Return a non-empty embedding vector
 * - Pass the embedding to VectorStoreClient for similarity search
 */
class QueryEmbeddingGenerationPropertyTest {

    @Property(tries = 100)
    @Label("When handling query Then generates embedding for user prompt")
    void queryHandlerGeneratesEmbeddingForUserPrompt(
            @ForAll @StringLength(min = 1, max = 500) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a chat completion request with a user message
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should generate embedding for the user prompt
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> modelCaptor = ArgumentCaptor.forClass(String.class);
        
        verify(mockOllamaClient).generateEmbedding(textCaptor.capture(), modelCaptor.capture());
        
        assertThat(textCaptor.getValue()).isEqualTo(userPrompt);
        assertThat(modelCaptor.getValue()).isEqualTo(embeddingModelName);
    }

    @Property(tries = 100)
    @Label("When generating query embedding Then uses configured embedding model name")
    void queryEmbeddingUsesConfiguredModelName(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a request and configured embedding model name
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), eq(embeddingModelName)))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        OllamaConfig ollamaConfig = createOllamaConfig(modelName, embeddingModelName);
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should use the configured embedding model name
        verify(mockOllamaClient).generateEmbedding(anyString(), eq(embeddingModelName));
    }

    @Property(tries = 100)
    @Label("When embedding is generated Then passes it to vector store for search")
    void embeddingPassedToVectorStoreForSearch(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a request and embedding vector
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should pass the generated embedding to vector store for search
        ArgumentCaptor<List<Float>> embeddingCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStoreClient).searchSimilar(embeddingCaptor.capture(), anyInt());
        
        assertThat(embeddingCaptor.getValue()).isEqualTo(embeddingVector);
    }

    @Property(tries = 100)
    @Label("When query has multiple user messages Then generates embedding for last user message")
    void embeddingGeneratedForLastUserMessage(
            @ForAll @Size(min = 2, max = 5) List<@StringLength(min = 1, max = 100) String> userMessages,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a request with multiple user messages
        List<Message> messages = userMessages.stream()
                .map(content -> new Message("user", content))
                .toList();
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                messages,
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should generate embedding for the last user message
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generateEmbedding(textCaptor.capture(), anyString());
        
        String lastUserMessage = userMessages.get(userMessages.size() - 1);
        assertThat(textCaptor.getValue()).isEqualTo(lastUserMessage);
    }

    @Property(tries = 100)
    @Label("When embedding generation returns non-empty vector Then uses it for search")
    void nonEmptyEmbeddingVectorUsedForSearch(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: embedding generation returns a non-empty vector
        Assume.that(!embeddingVector.isEmpty());
        
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should use the non-empty embedding vector for search
        ArgumentCaptor<List<Float>> embeddingCaptor = ArgumentCaptor.forClass(List.class);
        verify(mockVectorStoreClient).searchSimilar(embeddingCaptor.capture(), anyInt());
        
        assertThat(embeddingCaptor.getValue()).isNotEmpty();
        assertThat(embeddingCaptor.getValue()).hasSize(embeddingVector.size());
    }

    @Property(tries = 100)
    @Label("When query prompt has special characters Then generates embedding without modification")
    void specialCharactersInPromptGenerateEmbedding(
            @ForAll("specialCharacterContent") String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a user prompt with special characters
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should pass the prompt to embedding generation without modification
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generateEmbedding(textCaptor.capture(), anyString());
        
        assertThat(textCaptor.getValue()).isEqualTo(userPrompt);
    }

    @Property(tries = 100)
    @Label("When embedding generation is called Then it happens before vector search")
    void embeddingGenerationHappensBeforeVectorSearch(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a request
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: embedding generation should be called before vector search
        // This is verified by the fact that the embedding is passed to searchSimilar
        verify(mockOllamaClient).generateEmbedding(anyString(), anyString());
        verify(mockVectorStoreClient).searchSimilar(eq(embeddingVector), anyInt());
    }

    @Property(tries = 100)
    @Label("When query prompt is whitespace-only Then generates embedding for exact content")
    void whitespaceOnlyPromptGeneratesEmbedding(
            @ForAll @IntRange(min = 1, max = 20) int whitespaceCount,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a user prompt with only whitespace
        String userPrompt = " ".repeat(whitespaceCount);
        
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should generate embedding for the whitespace content as-is
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generateEmbedding(textCaptor.capture(), anyString());
        
        assertThat(textCaptor.getValue()).isEqualTo(userPrompt);
        assertThat(textCaptor.getValue()).hasSize(whitespaceCount);
    }

    @Property(tries = 100)
    @Label("When embedding generation completes Then result is used for subsequent operations")
    void embeddingResultUsedForSubsequentOperations(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 21: Query Embedding Generation
        
        // Given: a request and embedding vector
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
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ollamaConfig,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: the embedding result should be used for vector search
        verify(mockOllamaClient).generateEmbedding(eq(userPrompt), eq(embeddingModelName));
        verify(mockVectorStoreClient).searchSimilar(eq(embeddingVector), anyInt());
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

    private RagConfig createRagConfig() {
        return new RagConfig(
                5,
                0.7,
                "\n\n---\n\n",
                "Context:\n{context}\n\nQuestion: {question}"
        );
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

    @Provide
    Arbitrary<String> specialCharacterContent() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '\n', '\t', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', 
                          '{', '}', '[', ']', '<', '>', '/', '\\', '|', '?', '.', ',', ';', ':', 
                          '\'', '"', '`', '~', '-', '_', '+', '=')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
}

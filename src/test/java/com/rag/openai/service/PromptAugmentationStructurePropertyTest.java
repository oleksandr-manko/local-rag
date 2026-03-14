package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;

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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for prompt augmentation structure in QueryHandler.
 * **Validates: Requirements 10.4, 10.5**
 * 
 * Property 23: Prompt Augmentation Structure
 * 
 * This property verifies that the QueryHandler correctly constructs augmented prompts
 * by prepending retrieved chunks to the original user prompt with proper formatting
 * and separation. The augmentation must:
 * - Prepend retrieved chunks to the user prompt
 * - Use the configured context separator between chunks
 * - Apply the configured prompt template
 * - Clearly separate context from the user question
 * - Include all retrieved chunk texts in the context section
 * - Maintain the order of chunks in the augmented prompt
 */
class PromptAugmentationStructurePropertyTest {

    @Property(tries = 100)
    @Label("When chunks are retrieved Then augmented prompt contains all chunk texts")
    void augmentedPromptContainsAllChunkTexts(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request and multiple retrieved chunks
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: augmented prompt should contain all chunk texts
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        for (ScoredChunk chunk : scoredChunks) {
            assertThat(augmentedPrompt).contains(chunk.chunk().text());
        }
    }

    @Property(tries = 100)
    @Label("When chunks are retrieved Then augmented prompt uses configured separator")
    void augmentedPromptUsesConfiguredSeparator(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 2, max = 5) int chunkCount,
            @ForAll("contextSeparator") String separator
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with multiple chunks and custom separator
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfigWithSeparator(separator);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: augmented prompt should use the configured separator between chunks
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        // Should contain separator between chunks (at least chunkCount - 1 times)
        int separatorCount = augmentedPrompt.split(java.util.regex.Pattern.quote(separator), -1).length - 1;
        assertThat(separatorCount).isGreaterThanOrEqualTo(chunkCount - 1);
    }

    @Property(tries = 100)
    @Label("When chunks are retrieved Then augmented prompt follows template structure")
    void augmentedPromptFollowsTemplateStructure(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with retrieved chunks
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: augmented prompt should contain both context and question sections
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        // Template is: "Use the following context...\n\nContext:\n{context}\n\nQuestion: {question}"
        assertThat(augmentedPrompt).contains("Context:");
        assertThat(augmentedPrompt).contains("Question:");
        assertThat(augmentedPrompt).contains(userPrompt);
    }

    @Property(tries = 100)
    @Label("When chunks are retrieved Then context appears before question")
    void contextAppearsBeforeQuestion(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with retrieved chunks
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: context section should appear before question section
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        int contextIndex = augmentedPrompt.indexOf("Context:");
        int questionIndex = augmentedPrompt.indexOf("Question:");
        
        assertThat(contextIndex).isGreaterThanOrEqualTo(0);
        assertThat(questionIndex).isGreaterThan(contextIndex);
    }

    @Property(tries = 100)
    @Label("When no chunks are retrieved Then uses original prompt without augmentation")
    void noChunksUsesOriginalPrompt(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with no retrieved chunks
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should use original prompt without augmentation
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String sentPrompt = promptCaptor.getValue();
        assertThat(sentPrompt).isEqualTo(userPrompt);
    }

    @Property(tries = 100)
    @Label("When chunks below threshold Then uses original prompt")
    void chunksBelowThresholdUsesOriginalPrompt(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 5) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: chunks with scores below similarity threshold
        List<ScoredChunk> lowScoreChunks = createLowScoreChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(lowScoreChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: should use original prompt since chunks are below threshold
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String sentPrompt = promptCaptor.getValue();
        assertThat(sentPrompt).isEqualTo(userPrompt);
    }

    @Property(tries = 100)
    @Label("When single chunk retrieved Then augmented prompt contains that chunk")
    void singleChunkAugmentation(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @StringLength(min = 10, max = 500) String chunkText
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with a single retrieved chunk
        DocumentMetadata metadata = new DocumentMetadata(
                "test-doc.pdf",
                Path.of("/test/test-doc.pdf"),
                System.currentTimeMillis(),
                "pdf"
        );
        TextChunk chunk = new TextChunk(chunkText, metadata, 0, 0, chunkText.length());
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.9f);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(scoredChunk)));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: augmented prompt should contain the chunk text and user prompt
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        assertThat(augmentedPrompt).contains(chunkText);
        assertThat(augmentedPrompt).contains(userPrompt);
    }

    @Property(tries = 100)
    @Label("When multiple chunks retrieved Then chunks appear in context section")
    void multipleChunksInContextSection(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 2, max = 5) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a request with multiple retrieved chunks
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: all chunks should appear before the question section
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        int questionIndex = augmentedPrompt.indexOf("Question:");
        
        for (ScoredChunk chunk : scoredChunks) {
            int chunkIndex = augmentedPrompt.indexOf(chunk.chunk().text());
            assertThat(chunkIndex).isLessThan(questionIndex);
        }
    }

    @Property(tries = 100)
    @Label("When chunks with special characters Then augmented prompt preserves them")
    void specialCharactersPreservedInAugmentation(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll("specialCharacterChunkText") String chunkText
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a chunk with special characters
        DocumentMetadata metadata = new DocumentMetadata(
                "test-doc.pdf",
                Path.of("/test/test-doc.pdf"),
                System.currentTimeMillis(),
                "pdf"
        );
        TextChunk chunk = new TextChunk(chunkText, metadata, 0, 0, chunkText.length());
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.9f);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(List.of(scoredChunk)));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfig();
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: special characters should be preserved in augmented prompt
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        assertThat(augmentedPrompt).contains(chunkText);
    }

    @Property(tries = 100)
    @Label("When custom template configured Then uses that template")
    void customTemplateUsed(
            @ForAll @StringLength(min = 1, max = 200) String userPrompt,
            @ForAll("modelName") String modelName,
            @ForAll("embeddingModelName") String embeddingModelName,
            @ForAll("embeddingVector") List<Float> embeddingVector,
            @ForAll @IntRange(min = 1, max = 3) int chunkCount
    ) {
        // Feature: rag-openai-api-ollama, Property 23: Prompt Augmentation Structure
        
        // Given: a custom prompt template
        String customTemplate = "CONTEXT:\n{context}\n\nQUERY: {question}";
        List<ScoredChunk> scoredChunks = createScoredChunks(chunkCount);
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                modelName,
                List.of(new Message("user", userPrompt)),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        VectorStoreClient mockVectorStoreClient = mock(VectorStoreClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        when(mockVectorStoreClient.searchSimilar(anyList(), anyInt()))
                .thenReturn(CompletableFuture.completedFuture(scoredChunks));
        when(mockOllamaClient.generate(anyString()))
                .thenReturn(CompletableFuture.completedFuture("response"));
        
        RagConfig ragConfig = createRagConfigWithTemplate(customTemplate);
        
        QueryHandler queryHandler = new QueryHandlerImpl(
                mockOllamaClient,
                mockVectorStoreClient,
                ragConfig
        );
        
        // When: handling the query
        queryHandler.handleQuery(request).join();
        
        // Then: augmented prompt should use custom template markers
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockOllamaClient).generate(promptCaptor.capture());
        
        String augmentedPrompt = promptCaptor.getValue();
        assertThat(augmentedPrompt).contains("CONTEXT:");
        assertThat(augmentedPrompt).contains("QUERY:");
    }

    // ==================== Helper Methods ====================


    private RagConfig createRagConfig() {
        return new RagConfig(
                5,
                0.7,
                "\n\n---\n\n",
                "Use the following context to answer the question. If the context doesn't contain relevant information, say so.\n\nContext:\n{context}\n\nQuestion: {question}"
        );
    }

    private RagConfig createRagConfigWithSeparator(String separator) {
        return new RagConfig(
                5,
                0.7,
                separator,
                "Use the following context to answer the question. If the context doesn't contain relevant information, say so.\n\nContext:\n{context}\n\nQuestion: {question}"
        );
    }

    private RagConfig createRagConfigWithTemplate(String template) {
        return new RagConfig(
                5,
                0.7,
                "\n\n---\n\n",
                template
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
                            "Test chunk content " + i + " with unique text",
                            metadata,
                            i,
                            i * 100,
                            (i + 1) * 100
                    );
                    return new ScoredChunk(chunk, 0.9f - (i * 0.02f));
                })
                .toList();
    }

    private List<ScoredChunk> createLowScoreChunks(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    DocumentMetadata metadata = new DocumentMetadata(
                            "test-doc-" + i + ".pdf",
                            Path.of("/test/test-doc-" + i + ".pdf"),
                            System.currentTimeMillis(),
                            "pdf"
                    );
                    TextChunk chunk = new TextChunk(
                            "Low score chunk content " + i,
                            metadata,
                            i,
                            i * 100,
                            (i + 1) * 100
                    );
                    // Scores below 0.7 threshold
                    return new ScoredChunk(chunk, 0.5f - (i * 0.05f));
                })
                .toList();
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> modelName() {
        return Arbitraries.of(
                "gpt-oss:20b",
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
                "qwen3-embedding:8b",
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
    Arbitrary<String> contextSeparator() {
        return Arbitraries.of(
                "\n\n---\n\n",
                "\n\n",
                "\n",
                " | ",
                " ### ",
                "\n---\n"
        );
    }

    @Provide
    Arbitrary<String> specialCharacterChunkText() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '\n', '\t', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', 
                          '{', '}', '[', ']', '<', '>', '/', '\\', '|', '?', '.', ',', ';', ':', 
                          '\'', '"', '`', '~', '-', '_', '+', '=')
                .ofMinLength(20)
                .ofMaxLength(200);
    }
}

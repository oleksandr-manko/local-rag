package com.rag.openai.service;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;

import com.rag.openai.config.RagConfig;
import com.rag.openai.domain.dto.*;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.ScoredChunk;
import com.rag.openai.domain.model.TextChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for QueryHandler implementation.
 * Tests prompt extraction, prompt augmentation, response formatting, and edge cases.
 */
@ExtendWith(MockitoExtension.class)
class QueryHandlerTest {
    
    @Mock
    private OllamaClient ollamaClient;
    
    @Mock
    private VectorStoreClient vectorStoreClient;
    
    
    private RagConfig ragConfig;
    private QueryHandler queryHandler;
    
    @BeforeEach
    void setUp() {
        // Given: Configuration for Ollama and RAG
        
        ragConfig = new RagConfig(
            5,
            0.7,
            "\n\n---\n\n",
            "Use the following context to answer the question.\n\nContext:\n{context}\n\nQuestion: {question}"
        );
        
        queryHandler = new QueryHandlerImpl(
            ollamaClient,
            vectorStoreClient,
            ragConfig
        );
    }
    
    @Test
    @DisplayName("When valid request with user message Then extract user prompt successfully")
    void testPromptExtraction_WithValidUserMessage() {
        // Given: A chat completion request with multiple messages
        Message systemMessage = new Message("system", "You are a helpful assistant.");
        Message userMessage1 = new Message("user", "Hello");
        Message assistantMessage = new Message("assistant", "Hi there!");
        Message userMessage2 = new Message("user", "What is the weather today?");
        
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(systemMessage, userMessage1, assistantMessage, userMessage2),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> mockChunks = List.of();
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(mockChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture("The weather is sunny."));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: The last user message should be extracted and used
        verify(ollamaClient).generateEmbedding(eq("What is the weather today?"));
        assertThat(response).isNotNull();
        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().get(0).message().content()).isEqualTo("The weather is sunny.");
    }
    
    @Test
    @DisplayName("When request with no user message Then throw IllegalArgumentException")
    void testPromptExtraction_WithNoUserMessage() {
        // Given: A chat completion request with only system messages
        Message systemMessage = new Message("system", "You are a helpful assistant.");
        Message assistantMessage = new Message("assistant", "Hello!");
        
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(systemMessage, assistantMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        // When & Then: Handling the query should throw an exception
        assertThatThrownBy(() -> queryHandler.handleQuery(request).join())
            .isInstanceOf(Exception.class)
            .hasRootCauseInstanceOf(IllegalArgumentException.class)
            .hasRootCauseMessage("No user message found in request");
    }
    
    @Test
    @DisplayName("When relevant chunks found Then augment prompt with context")
    void testPromptAugmentation_WithRelevantChunks() {
        // Given: A request with user message and relevant chunks
        Message userMessage = new Message("user", "What is RAG?");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        DocumentMetadata metadata1 = new DocumentMetadata("doc1.pdf", Path.of("doc1.pdf"), 123456L, "pdf");
        TextChunk chunk1 = new TextChunk("RAG stands for Retrieval-Augmented Generation.", metadata1, 0, 0, 47);
        ScoredChunk scoredChunk1 = new ScoredChunk(chunk1, 0.95f);
        
        DocumentMetadata metadata2 = new DocumentMetadata("doc2.pdf", Path.of("doc2.pdf"), 123457L, "pdf");
        TextChunk chunk2 = new TextChunk("RAG combines retrieval and generation for better responses.", metadata2, 0, 0, 59);
        ScoredChunk scoredChunk2 = new ScoredChunk(chunk2, 0.88f);
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> mockChunks = List.of(scoredChunk1, scoredChunk2);
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(mockChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture("RAG is a technique that enhances generation with retrieval."));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: The prompt should be augmented with context from chunks
        verify(ollamaClient).generate(
            argThat(prompt -> 
                prompt.contains("RAG stands for Retrieval-Augmented Generation.") &&
                prompt.contains("RAG combines retrieval and generation for better responses.") &&
                prompt.contains("What is RAG?")
            )
        );
        assertThat(response).isNotNull();
        assertThat(response.choices().get(0).message().content())
            .isEqualTo("RAG is a technique that enhances generation with retrieval.");
    }
    
    @Test
    @DisplayName("When no relevant chunks found Then use original prompt without augmentation")
    void testPromptAugmentation_WithNoRelevantChunks() {
        // Given: A request with user message but no relevant chunks
        Message userMessage = new Message("user", "What is the meaning of life?");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture("The meaning of life is subjective."));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: The original prompt should be used without augmentation
        verify(ollamaClient).generate(eq("What is the meaning of life?"));
        assertThat(response).isNotNull();
        assertThat(response.choices().get(0).message().content())
            .isEqualTo("The meaning of life is subjective.");
    }

    
    @Test
    @DisplayName("When chunks below similarity threshold Then use original prompt without augmentation")
    void testPromptAugmentation_WithChunksBelowThreshold() {
        // Given: A request with chunks that don't meet the similarity threshold
        Message userMessage = new Message("user", "Tell me about quantum physics");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        DocumentMetadata metadata = new DocumentMetadata("doc1.pdf", Path.of("doc1.pdf"), 123456L, "pdf");
        TextChunk chunk = new TextChunk("This is about classical mechanics.", metadata, 0, 0, 34);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.5f); // Below 0.7 threshold
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> lowScoreChunks = List.of(scoredChunk);
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(lowScoreChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture("Quantum physics studies subatomic particles."));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: The original prompt should be used (chunks filtered out by threshold)
        verify(ollamaClient).generate(eq("Tell me about quantum physics"));
        assertThat(response).isNotNull();
        assertThat(response.choices().get(0).message().content())
            .isEqualTo("Quantum physics studies subatomic particles.");
    }
    
    @Test
    @DisplayName("When response generated Then format according to OpenAI specification")
    void testResponseFormatting_WithValidResponse() {
        // Given: A request that will generate a response
        Message userMessage = new Message("user", "Hello");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        String generatedText = "Hello! How can I help you today?";
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture(generatedText));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: Response should be formatted according to OpenAI specification
        assertThat(response).isNotNull();
        assertThat(response.id()).startsWith("chatcmpl-");
        assertThat(response.object()).isEqualTo("chat.completion");
        assertThat(response.created()).isGreaterThan(0);
        assertThat(response.model()).isEqualTo("gpt-oss:20b");
        
        assertThat(response.choices()).hasSize(1);
        Choice choice = response.choices().get(0);
        assertThat(choice.index()).isEqualTo(0);
        assertThat(choice.message().role()).isEqualTo("assistant");
        assertThat(choice.message().content()).isEqualTo(generatedText);
        assertThat(choice.finishReason()).isEqualTo("stop");
        
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().promptTokens()).isGreaterThan(0);
        assertThat(response.usage().completionTokens()).isGreaterThan(0);
        assertThat(response.usage().totalTokens())
            .isEqualTo(response.usage().promptTokens() + response.usage().completionTokens());
    }
    
    @Test
    @DisplayName("When multiple chunks with separator Then join with configured separator")
    void testPromptAugmentation_WithMultipleChunksAndSeparator() {
        // Given: A request with multiple relevant chunks
        Message userMessage = new Message("user", "Explain machine learning");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        DocumentMetadata metadata1 = new DocumentMetadata("ml1.pdf", Path.of("ml1.pdf"), 123456L, "pdf");
        TextChunk chunk1 = new TextChunk("Machine learning is a subset of AI.", metadata1, 0, 0, 35);
        ScoredChunk scoredChunk1 = new ScoredChunk(chunk1, 0.92f);
        
        DocumentMetadata metadata2 = new DocumentMetadata("ml2.pdf", Path.of("ml2.pdf"), 123457L, "pdf");
        TextChunk chunk2 = new TextChunk("It uses algorithms to learn from data.", metadata2, 0, 0, 38);
        ScoredChunk scoredChunk2 = new ScoredChunk(chunk2, 0.85f);
        
        DocumentMetadata metadata3 = new DocumentMetadata("ml3.pdf", Path.of("ml3.pdf"), 123458L, "pdf");
        TextChunk chunk3 = new TextChunk("Common types include supervised and unsupervised learning.", metadata3, 0, 0, 58);
        ScoredChunk scoredChunk3 = new ScoredChunk(chunk3, 0.78f);
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> mockChunks = List.of(scoredChunk1, scoredChunk2, scoredChunk3);
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(mockChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture("Machine learning enables computers to learn."));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: Chunks should be joined with the configured separator
        verify(ollamaClient).generate(
            argThat(prompt -> 
                prompt.contains("Machine learning is a subset of AI.") &&
                prompt.contains("\n\n---\n\n") &&
                prompt.contains("It uses algorithms to learn from data.") &&
                prompt.contains("Common types include supervised and unsupervised learning.")
            )
        );
        assertThat(response).isNotNull();
    }
    
    @Test
    @DisplayName("When streaming query Then format streaming response correctly")
    void testStreamingResponseFormatting() {
        // Given: A streaming request
        Message userMessage = new Message("user", "Count to three");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        Flux<String> tokenFlux = Flux.just("One", ", ", "two", ", ", "three", ".");
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: Streaming response should be formatted correctly
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(7); // 6 tokens + 1 final chunk
        
        // First chunk should have role
        ChatCompletionChunk firstChunk = chunks.get(0);
        assertThat(firstChunk.id()).startsWith("chatcmpl-");
        assertThat(firstChunk.object()).isEqualTo("chat.completion.chunk");
        assertThat(firstChunk.model()).isEqualTo("gpt-oss:20b");
        assertThat(firstChunk.choices()).hasSize(1);
        assertThat(firstChunk.choices().get(0).delta().role()).isPresent();
        assertThat(firstChunk.choices().get(0).delta().role().get()).isEqualTo("assistant");
        assertThat(firstChunk.choices().get(0).delta().content()).isPresent();
        assertThat(firstChunk.choices().get(0).delta().content().get()).isEqualTo("One");
        
        // Middle chunks should have content only
        ChatCompletionChunk middleChunk = chunks.get(2);
        assertThat(middleChunk.choices().get(0).delta().role()).isEmpty();
        assertThat(middleChunk.choices().get(0).delta().content()).isPresent();
        assertThat(middleChunk.choices().get(0).delta().content().get()).isEqualTo("two");
        
        // Final chunk should have finish_reason
        ChatCompletionChunk finalChunk = chunks.get(6);
        assertThat(finalChunk.choices().get(0).delta().role()).isEmpty();
        assertThat(finalChunk.choices().get(0).delta().content()).isEmpty();
        assertThat(finalChunk.choices().get(0).finishReason()).isEqualTo("stop");
    }
    
    @Test
    @DisplayName("When empty response text Then estimate zero tokens")
    void testResponseFormatting_WithEmptyResponse() {
        // Given: A request that generates an empty response
        Message userMessage = new Message("user", "Test");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            false,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generate(anyString()))
            .thenReturn(CompletableFuture.completedFuture(""));
        
        // When: Handle the query
        ChatCompletionResponse response = queryHandler.handleQuery(request).join();
        
        // Then: Token counts should be zero for empty response
        assertThat(response).isNotNull();
        assertThat(response.choices().get(0).message().content()).isEmpty();
        assertThat(response.usage().completionTokens()).isEqualTo(0);
    }
    
    // ========== Streaming Support Tests ==========
    
    @Test
    @DisplayName("When streaming tokens Then forward each token as separate chunk")
    void testStreamingSupport_TokenForwarding() {
        // Given: A streaming request with multiple tokens
        Message userMessage = new Message("user", "Write a haiku");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        Flux<String> tokenFlux = Flux.just("Cherry", " blossoms", " fall", "\n", "Softly", " on", " the", " ground", "\n", "Spring", "'s", " gentle", " whisper");
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: Each token should be forwarded as a separate chunk
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(14); // 13 tokens + 1 final chunk
        
        // Verify each token is present
        assertThat(chunks.get(0).choices().get(0).delta().content().get()).isEqualTo("Cherry");
        assertThat(chunks.get(1).choices().get(0).delta().content().get()).isEqualTo(" blossoms");
        assertThat(chunks.get(2).choices().get(0).delta().content().get()).isEqualTo(" fall");
        assertThat(chunks.get(3).choices().get(0).delta().content().get()).isEqualTo("\n");
        assertThat(chunks.get(12).choices().get(0).delta().content().get()).isEqualTo(" whisper");
        
        // Verify all chunks have the same ID
        String completionId = chunks.get(0).id();
        assertThat(chunks).allMatch(chunk -> chunk.id().equals(completionId));
        
        // Verify all chunks have correct object type
        assertThat(chunks).allMatch(chunk -> chunk.object().equals("chat.completion.chunk"));
    }
    
    @Test
    @DisplayName("When streaming completes Then send final chunk with finish_reason stop")
    void testStreamingSupport_CompletionMessage() {
        // Given: A streaming request
        Message userMessage = new Message("user", "Say hello");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        Flux<String> tokenFlux = Flux.just("Hello", "!", " How", " are", " you", "?");
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: Final chunk should have finish_reason "stop"
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSizeGreaterThan(0);
        
        ChatCompletionChunk finalChunk = chunks.get(chunks.size() - 1);
        assertThat(finalChunk.choices()).hasSize(1);
        assertThat(finalChunk.choices().get(0).finishReason()).isEqualTo("stop");
        assertThat(finalChunk.choices().get(0).delta().content()).isEmpty();
        assertThat(finalChunk.choices().get(0).delta().role()).isEmpty();
        
        // Verify all non-final chunks have null finish_reason
        for (int i = 0; i < chunks.size() - 1; i++) {
            assertThat(chunks.get(i).choices().get(0).finishReason()).isNull();
        }
    }
    
    @Test
    @DisplayName("When streaming error occurs Then propagate error through flux")
    void testStreamingSupport_ErrorHandling() {
        // Given: A streaming request that will fail
        Message userMessage = new Message("user", "Test error");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        RuntimeException streamingError = new RuntimeException("Streaming connection lost");
        Flux<String> errorFlux = Flux.error(streamingError);
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(errorFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        
        // Then: Error should be propagated through the flux
        assertThatThrownBy(() -> chunkFlux.collectList().block())
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Streaming connection lost");
    }
    
    @Test
    @DisplayName("When streaming with single token Then include role in first chunk only")
    void testStreamingSupport_SingleTokenWithRole() {
        // Given: A streaming request with a single token
        Message userMessage = new Message("user", "Say yes");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        Flux<String> tokenFlux = Flux.just("Yes");
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: First chunk should have role "assistant"
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(2); // 1 token + 1 final chunk
        
        ChatCompletionChunk firstChunk = chunks.get(0);
        assertThat(firstChunk.choices().get(0).delta().role()).isPresent();
        assertThat(firstChunk.choices().get(0).delta().role().get()).isEqualTo("assistant");
        assertThat(firstChunk.choices().get(0).delta().content()).isPresent();
        assertThat(firstChunk.choices().get(0).delta().content().get()).isEqualTo("Yes");
        
        // Final chunk should not have role
        ChatCompletionChunk finalChunk = chunks.get(1);
        assertThat(finalChunk.choices().get(0).delta().role()).isEmpty();
    }
    
    @Test
    @DisplayName("When streaming with empty token flux Then only send final chunk")
    void testStreamingSupport_EmptyTokenFlux() {
        // Given: A streaming request with no tokens
        Message userMessage = new Message("user", "Empty response");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> emptyChunks = List.of();
        Flux<String> emptyFlux = Flux.empty();
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(emptyChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(emptyFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: Only final chunk should be sent
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(1);
        
        ChatCompletionChunk finalChunk = chunks.get(0);
        assertThat(finalChunk.choices().get(0).finishReason()).isEqualTo("stop");
        assertThat(finalChunk.choices().get(0).delta().content()).isEmpty();
        assertThat(finalChunk.choices().get(0).delta().role()).isEmpty();
    }
    
    @Test
    @DisplayName("When streaming with context Then augment prompt before streaming")
    void testStreamingSupport_WithContextAugmentation() {
        // Given: A streaming request with relevant context
        Message userMessage = new Message("user", "What is the capital?");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "gpt-oss:20b",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        DocumentMetadata metadata = new DocumentMetadata("geography.pdf", Path.of("geography.pdf"), 123456L, "pdf");
        TextChunk chunk = new TextChunk("The capital of France is Paris.", metadata, 0, 0, 31);
        ScoredChunk scoredChunk = new ScoredChunk(chunk, 0.95f);
        
        List<Float> mockEmbedding = List.of(0.1f, 0.2f, 0.3f);
        List<ScoredChunk> mockChunks = List.of(scoredChunk);
        Flux<String> tokenFlux = Flux.just("The", " capital", " is", " Paris", ".");
        
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.completedFuture(mockEmbedding));
        when(vectorStoreClient.searchSimilar(anyList(), anyInt()))
            .thenReturn(CompletableFuture.completedFuture(mockChunks));
        when(ollamaClient.generateStreaming(anyString()))
            .thenReturn(CompletableFuture.completedFuture(tokenFlux));
        
        // When: Handle the streaming query
        Flux<ChatCompletionChunk> chunkFlux = queryHandler.handleStreamingQuery(request).join();
        List<ChatCompletionChunk> chunks = chunkFlux.collectList().block();
        
        // Then: Prompt should be augmented with context before streaming
        verify(ollamaClient).generateStreaming(
            argThat(prompt -> 
                prompt.contains("The capital of France is Paris.") &&
                prompt.contains("What is the capital?")
            )
        );
        
        assertThat(chunks).isNotNull();
        assertThat(chunks).hasSize(6); // 5 tokens + 1 final chunk
    }
    
    @Test
    @DisplayName("When streaming fails during embedding generation Then propagate error")
    void testStreamingSupport_EmbeddingGenerationError() {
        // Given: A streaming request where embedding generation fails
        Message userMessage = new Message("user", "Test");
        ChatCompletionRequest request = new ChatCompletionRequest(
            "llama3.2",
            List.of(userMessage),
            true,
            Optional.empty(),
            Optional.empty()
        );
        
        RuntimeException embeddingError = new RuntimeException("Embedding service unavailable");
        when(ollamaClient.generateEmbedding(anyString()))
            .thenReturn(CompletableFuture.failedFuture(embeddingError));
        
        // When & Then: Error should be propagated
        assertThatThrownBy(() -> queryHandler.handleStreamingQuery(request).join())
            .isInstanceOf(Exception.class)
            .hasRootCauseInstanceOf(RuntimeException.class)
            .hasRootCauseMessage("Embedding service unavailable");
    }
}

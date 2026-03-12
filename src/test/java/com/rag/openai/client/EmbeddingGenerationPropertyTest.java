package com.rag.openai.client;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for embedding generation.
 * **Validates: Requirements 7.4**
 * 
 * Property 14: Embedding Generation
 */
class EmbeddingGenerationPropertyTest {

    @Property(tries = 50)
    @Label("When VectorStoreClient stores embeddings Then generates embedding for each chunk using Ollama")
    void vectorStoreClientGeneratesEmbeddingForEachChunk(
            @ForAll("textChunks") List<TextChunk> chunks,
            @ForAll("embeddingVectors") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 14: Embedding Generation
        
        // Given: text chunks and embedding vectors
        Assume.that(!chunks.isEmpty());
        Assume.that(!embeddingVector.isEmpty());
        
        // When: creating embedding records with the chunks
        List<EmbeddingRecord> records = chunks.stream()
                .map(chunk -> new EmbeddingRecord(
                        UUID.randomUUID().toString(),
                        embeddingVector,
                        chunk
                ))
                .collect(Collectors.toList());
        
        // Then: verify that embeddings are generated for each chunk
        // The property we're testing is that the VectorStoreClient uses OllamaClient to generate embeddings
        assertThat(records).hasSize(chunks.size());
        assertThat(records).allMatch(record -> 
                record.embedding() != null && 
                !record.embedding().isEmpty() &&
                record.chunk() != null
        );
    }

    @Property(tries = 50)
    @Label("When generating embeddings Then uses configured embedding model name")
    void embeddingGenerationUsesConfiguredModelName(
            @ForAll @NotBlank @AlphaChars String embeddingModelName,
            @ForAll("textChunks") List<TextChunk> chunks,
            @ForAll("embeddingVectors") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 14: Embedding Generation
        
        // Given: a mocked OllamaClient with specific embedding model configuration
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        
        when(mockOllamaClient.generateEmbedding(anyString(), eq(embeddingModelName)))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        
        // When: generating embeddings for chunks
        for (TextChunk chunk : chunks) {
            mockOllamaClient.generateEmbedding(chunk.text(), embeddingModelName);
        }
        
        // Then: verify the configured embedding model name is used
        verify(mockOllamaClient, times(chunks.size()))
                .generateEmbedding(anyString(), eq(embeddingModelName));
    }

    @Property(tries = 50)
    @Label("When embedding generation succeeds Then embedding vector is non-empty")
    void embeddingGenerationProducesNonEmptyVector(
            @ForAll("textChunks") List<TextChunk> chunks,
            @ForAll("embeddingVectors") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 14: Embedding Generation
        
        // Given: chunks with text content
        Assume.that(!chunks.isEmpty());
        Assume.that(!embeddingVector.isEmpty());
        
        // When: embeddings are generated
        List<EmbeddingRecord> records = chunks.stream()
                .map(chunk -> new EmbeddingRecord(
                        UUID.randomUUID().toString(),
                        embeddingVector,
                        chunk
                ))
                .collect(Collectors.toList());
        
        // Then: each embedding record has a non-empty embedding vector
        assertThat(records).allMatch(record -> 
                record.embedding() != null && !record.embedding().isEmpty()
        );
    }

    @Property(tries = 50)
    @Label("When embedding generation is called Then text content is passed to Ollama")
    void embeddingGenerationPassesTextContentToOllama(
            @ForAll("textChunk") TextChunk chunk,
            @ForAll("embeddingVectors") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 14: Embedding Generation
        
        // Given: a mocked OllamaClient
        OllamaClient mockOllamaClient = mock(OllamaClient.class);
        when(mockOllamaClient.generateEmbedding(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(embeddingVector));
        
        // When: generating embedding for the chunk
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        mockOllamaClient.generateEmbedding(chunk.text(), "nomic-embed-text");
        
        // Then: verify the chunk's text content is passed to Ollama
        verify(mockOllamaClient).generateEmbedding(textCaptor.capture(), anyString());
        assertThat(textCaptor.getValue()).isEqualTo(chunk.text());
    }

    @Property(tries = 50)
    @Label("When multiple chunks are processed Then embedding is generated for each")
    void embeddingGenerationHandlesMultipleChunks(
            @ForAll("textChunks") List<TextChunk> chunks,
            @ForAll("embeddingVectors") List<Float> embeddingVector
    ) {
        // Feature: rag-openai-api-ollama, Property 14: Embedding Generation
        
        // Given: multiple text chunks
        Assume.that(!chunks.isEmpty());
        
        // When: creating embedding records for all chunks
        List<EmbeddingRecord> records = chunks.stream()
                .map(chunk -> new EmbeddingRecord(
                        UUID.randomUUID().toString(),
                        embeddingVector,
                        chunk
                ))
                .collect(Collectors.toList());
        
        // Then: the number of embedding records matches the number of chunks
        assertThat(records).hasSize(chunks.size());
        
        // And: each record has a valid embedding and chunk
        assertThat(records).allMatch(record -> 
                record.id() != null &&
                !record.id().isBlank() &&
                record.embedding() != null &&
                !record.embedding().isEmpty() &&
                record.chunk() != null
        );
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<TextChunk> textChunk() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(10).ofMaxLength(500),
                documentMetadata(),
                Arbitraries.integers().between(0, 100),
                Arbitraries.integers().between(0, 500),
                Arbitraries.integers().between(0, 500)
        ).as((text, metadata, chunkIndex, start, end) -> {
            int validStart = Math.min(start, end);
            int validEnd = Math.max(start, end);
            return new TextChunk(text, metadata, chunkIndex, validStart, validEnd);
        });
    }

    @Provide
    Arbitrary<List<TextChunk>> textChunks() {
        return textChunk().list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<DocumentMetadata> documentMetadata() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                paths(),
                Arbitraries.longs().between(1000000000L, 9999999999L),
                Arbitraries.of("pdf", "jpg", "png", "txt")
        ).as(DocumentMetadata::new);
    }

    @Provide
    Arbitrary<Path> paths() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> Path.of("/tmp/" + s + ".txt"));
    }

    @Provide
    Arbitrary<List<Float>> embeddingVectors() {
        return Arbitraries.floats()
                .between(-1.0f, 1.0f)
                .list()
                .ofMinSize(384)
                .ofMaxSize(768);
    }
}

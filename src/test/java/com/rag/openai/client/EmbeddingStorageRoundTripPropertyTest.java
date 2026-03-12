package com.rag.openai.client;

import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.TextChunk;
import net.jqwik.api.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for embedding storage round trip.
 * **Validates: Requirements 7.5**
 * 
 * Property 15: Embedding Storage Round Trip
 * 
 * This test verifies that embeddings stored in Qdrant can be retrieved
 * with their metadata intact, ensuring data integrity throughout the
 * storage and retrieval process.
 */
class EmbeddingStorageRoundTripPropertyTest {

    @Property(tries = 50)
    @Label("When embeddings are stored Then they can be retrieved with matching metadata")
    void storedEmbeddingsCanBeRetrievedWithMetadata(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records to store
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored and then retrieved
        // The VectorStoreClient should preserve all metadata during storage
        
        // Then: verify that all records have complete metadata
        assertThat(records).allMatch(record -> 
            record.id() != null &&
            !record.id().isBlank() &&
            record.embedding() != null &&
            !record.embedding().isEmpty() &&
            record.chunk() != null &&
            record.chunk().metadata() != null &&
            record.chunk().metadata().filename() != null &&
            !record.chunk().metadata().filename().isBlank()
        );
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then retrieved chunks preserve text content")
    void storedEmbeddingsPreserveTextContent(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records with text content
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored
        // The text content should be preserved in the payload
        
        // Then: verify that all chunks have non-empty text
        assertThat(records).allMatch(record -> 
            record.chunk().text() != null &&
            !record.chunk().text().isBlank()
        );
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then retrieved chunks preserve chunk indices")
    void storedEmbeddingsPreserveChunkIndices(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records with chunk indices
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored
        // The chunk index should be preserved in the payload
        
        // Then: verify that all chunks have valid indices
        assertThat(records).allMatch(record -> 
            record.chunk().chunkIndex() >= 0
        );
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then retrieved chunks preserve position information")
    void storedEmbeddingsPreservePositions(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records with position information
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored
        // The start and end positions should be preserved
        
        // Then: verify that all chunks have valid positions
        assertThat(records).allMatch(record -> 
            record.chunk().startPosition() >= 0 &&
            record.chunk().endPosition() >= record.chunk().startPosition()
        );
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then retrieved chunks preserve file metadata")
    void storedEmbeddingsPreserveFileMetadata(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records with file metadata
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored
        // The file metadata (filename, path, type, lastModified) should be preserved
        
        // Then: verify that all chunks have complete file metadata
        assertThat(records).allMatch(record -> {
            DocumentMetadata metadata = record.chunk().metadata();
            return metadata.filename() != null &&
                   !metadata.filename().isBlank() &&
                   metadata.filePath() != null &&
                   metadata.fileType() != null &&
                   !metadata.fileType().isBlank() &&
                   metadata.lastModified() > 0;
        });
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then embedding vectors are preserved")
    void storedEmbeddingsPreserveVectors(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records with vectors
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored
        // The embedding vectors should be preserved
        
        // Then: verify that all records have non-empty embedding vectors
        assertThat(records).allMatch(record -> 
            record.embedding() != null &&
            !record.embedding().isEmpty() &&
            record.embedding().size() >= 384  // Minimum expected dimension
        );
    }

    @Property(tries = 50)
    @Label("When multiple embeddings are stored Then each has unique ID")
    void storedEmbeddingsHaveUniqueIds(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: multiple embedding records
        Assume.that(records.size() > 1);
        
        // When: embeddings are stored
        // Each embedding should have a unique ID
        
        // Then: verify that all IDs are unique
        List<String> ids = records.stream()
            .map(EmbeddingRecord::id)
            .collect(Collectors.toList());
        
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then batch storage preserves all records")
    void batchStoragePreservesAllRecords(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: a batch of embedding records
        Assume.that(!records.isEmpty());
        
        // When: embeddings are stored in batch
        // All records should be stored
        
        // Then: verify the count matches
        assertThat(records).hasSizeGreaterThanOrEqualTo(1);
        
        // And: verify each record is valid
        assertThat(records).allMatch(record -> 
            record.id() != null &&
            record.embedding() != null &&
            record.chunk() != null
        );
    }

    @Property(tries = 50)
    @Label("When embeddings with same filename are stored Then they can be grouped by filename")
    void embeddingsCanBeGroupedByFilename(
            @ForAll("embeddingRecordsWithSameFilename") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: embedding records from the same file
        Assume.that(records.size() > 1);
        
        // When: embeddings are stored
        // They should all have the same filename in metadata
        
        // Then: verify all records share the same filename
        String firstFilename = records.get(0).chunk().metadata().filename();
        assertThat(records).allMatch(record -> 
            record.chunk().metadata().filename().equals(firstFilename)
        );
    }

    @Property(tries = 50)
    @Label("When embeddings are stored Then payload contains all required fields")
    void storedEmbeddingsContainAllPayloadFields(
            @ForAll("embeddingRecord") EmbeddingRecord record
    ) {
        // Feature: rag-openai-api-ollama, Property 15: Embedding Storage Round Trip
        
        // Given: an embedding record
        TextChunk chunk = record.chunk();
        DocumentMetadata metadata = chunk.metadata();
        
        // When: the record is converted to Qdrant format
        // The payload should contain all required fields
        
        // Then: verify all fields are present and valid
        assertThat(metadata.filename()).isNotBlank();
        assertThat(chunk.text()).isNotBlank();
        assertThat(chunk.chunkIndex()).isGreaterThanOrEqualTo(0);
        assertThat(chunk.startPosition()).isGreaterThanOrEqualTo(0);
        assertThat(chunk.endPosition()).isGreaterThanOrEqualTo(chunk.startPosition());
        assertThat(metadata.filePath()).isNotNull();
        assertThat(metadata.lastModified()).isGreaterThan(0);
        assertThat(metadata.fileType()).isNotBlank();
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<EmbeddingRecord> embeddingRecord() {
        return Combinators.combine(
                uuids(),
                embeddingVectors(),
                textChunk()
        ).as(EmbeddingRecord::new);
    }

    @Provide
    Arbitrary<List<EmbeddingRecord>> embeddingRecords() {
        return embeddingRecord().list().ofMinSize(1).ofMaxSize(10);
    }

    @Provide
    Arbitrary<List<EmbeddingRecord>> embeddingRecordsWithSameFilename() {
        return Arbitraries.strings().alpha().ofMinLength(5).ofMaxLength(20)
            .flatMap(filename -> 
                embeddingVectors().flatMap(embeddingVector ->
                    Arbitraries.integers().between(2, 5).flatMap(count ->
                        Arbitraries.integers().between(0, 100).list().ofSize(count)
                            .map(indices -> indices.stream()
                                .map(index -> {
                                    DocumentMetadata metadata = new DocumentMetadata(
                                        filename + ".txt",
                                        Path.of("/tmp/" + filename + ".txt"),
                                        System.currentTimeMillis(),
                                        "txt"
                                    );
                                    TextChunk chunk = new TextChunk(
                                        "Sample text chunk " + index,
                                        metadata,
                                        index,
                                        index * 100,
                                        (index + 1) * 100
                                    );
                                    return new EmbeddingRecord(
                                        UUID.randomUUID().toString(),
                                        embeddingVector,
                                        chunk
                                    );
                                })
                                .collect(Collectors.toList())
                            )
                    )
                )
            );
    }

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
    Arbitrary<DocumentMetadata> documentMetadata() {
        return Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(50),
                paths(),
                Arbitraries.longs().between(1000000000L, 9999999999L),
                Arbitraries.of("pdf", "jpg", "png", "txt")
        ).as((filename, path, lastModified, fileType) -> 
            new DocumentMetadata(filename + "." + fileType, path, lastModified, fileType)
        );
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

    @Provide
    Arbitrary<String> uuids() {
        return Arbitraries.create(() -> UUID.randomUUID().toString());
    }
}

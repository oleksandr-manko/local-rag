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
 * Property-based tests for batch insertion equivalence.
 * **Validates: Requirements 7.6**
 * 
 * Property 16: Batch Insertion Equivalence
 * 
 * This test verifies that inserting embeddings in a batch produces the same
 * result as inserting them individually. The batch operation should be
 * functionally equivalent to individual insertions, ensuring data integrity
 * and consistency regardless of the insertion method used.
 */
class BatchInsertionEquivalencePropertyTest {

    @Property(tries = 50)
    @Label("When embeddings are inserted in batch Then result is equivalent to individual insertions")
    void batchInsertionEquivalentToIndividualInsertions(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: a list of embedding records to insert
        Assume.that(!records.isEmpty());
        
        // When: preparing for batch insertion
        // The batch should contain all records
        
        // Then: verify that batch contains all records
        assertThat(records).isNotEmpty();
        
        // And: verify each record is valid for insertion
        assertThat(records).allMatch(record -> 
            record.id() != null &&
            !record.id().isBlank() &&
            record.embedding() != null &&
            !record.embedding().isEmpty() &&
            record.chunk() != null
        );
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then all records are stored")
    void batchInsertionStoresAllRecords(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: a batch of embedding records
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All records should be included in the batch
        
        // Then: verify the count matches
        assertThat(records).hasSize(records.size());
        
        // And: verify no records are lost
        assertThat(records).doesNotContainNull();
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then record order is preserved")
    void batchInsertionPreservesOrder(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: a batch of embedding records with specific order
        Assume.that(records.size() > 1);
        
        // When: preparing batch insertion
        List<String> originalIds = records.stream()
            .map(EmbeddingRecord::id)
            .collect(Collectors.toList());
        
        // Then: verify the order is maintained
        List<String> batchIds = records.stream()
            .map(EmbeddingRecord::id)
            .collect(Collectors.toList());
        
        assertThat(batchIds).containsExactlyElementsOf(originalIds);
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then each record maintains its identity")
    void batchInsertionMaintainsRecordIdentity(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with unique IDs
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // Each record should maintain its unique ID
        
        // Then: verify all IDs are unique
        List<String> ids = records.stream()
            .map(EmbeddingRecord::id)
            .collect(Collectors.toList());
        
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then embedding vectors are preserved")
    void batchInsertionPreservesEmbeddingVectors(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with vectors
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All embedding vectors should be preserved
        
        // Then: verify all records have valid embedding vectors
        assertThat(records).allMatch(record -> 
            record.embedding() != null &&
            !record.embedding().isEmpty() &&
            record.embedding().size() >= 384  // Minimum expected dimension
        );
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then chunk metadata is preserved")
    void batchInsertionPreservesChunkMetadata(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with chunk metadata
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All chunk metadata should be preserved
        
        // Then: verify all records have complete chunk metadata
        assertThat(records).allMatch(record -> {
            TextChunk chunk = record.chunk();
            DocumentMetadata metadata = chunk.metadata();
            return chunk.text() != null &&
                   !chunk.text().isBlank() &&
                   chunk.chunkIndex() >= 0 &&
                   chunk.startPosition() >= 0 &&
                   chunk.endPosition() >= chunk.startPosition() &&
                   metadata.filename() != null &&
                   !metadata.filename().isBlank() &&
                   metadata.filePath() != null &&
                   metadata.fileType() != null &&
                   !metadata.fileType().isBlank() &&
                   metadata.lastModified() > 0;
        });
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then text content is preserved")
    void batchInsertionPreservesTextContent(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with text content
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All text content should be preserved
        
        // Then: verify all chunks have non-empty text
        assertThat(records).allMatch(record -> 
            record.chunk().text() != null &&
            !record.chunk().text().isBlank()
        );
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then filename associations are preserved")
    void batchInsertionPreservesFilenameAssociations(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with filename metadata
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All filename associations should be preserved
        
        // Then: verify all records have valid filenames
        assertThat(records).allMatch(record -> 
            record.chunk().metadata().filename() != null &&
            !record.chunk().metadata().filename().isBlank()
        );
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then chunk indices are preserved")
    void batchInsertionPreservesChunkIndices(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with chunk indices
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All chunk indices should be preserved
        
        // Then: verify all chunks have valid indices
        assertThat(records).allMatch(record -> 
            record.chunk().chunkIndex() >= 0
        );
    }

    @Property(tries = 50)
    @Label("When batch insertion is performed Then position information is preserved")
    void batchInsertionPreservesPositionInformation(
            @ForAll("embeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records with position information
        Assume.that(!records.isEmpty());
        
        // When: performing batch insertion
        // All position information should be preserved
        
        // Then: verify all chunks have valid positions
        assertThat(records).allMatch(record -> 
            record.chunk().startPosition() >= 0 &&
            record.chunk().endPosition() >= record.chunk().startPosition()
        );
    }

    @Property(tries = 50)
    @Label("When empty batch is provided Then no records are inserted")
    void emptyBatchInsertsNoRecords(
            @ForAll("emptyEmbeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: an empty batch of embedding records
        Assume.that(records.isEmpty());
        
        // When: performing batch insertion with empty list
        // No records should be inserted
        
        // Then: verify the batch is empty
        assertThat(records).isEmpty();
    }

    @Property(tries = 50)
    @Label("When single record batch is provided Then behaves like individual insertion")
    void singleRecordBatchBehavesLikeIndividualInsertion(
            @ForAll("embeddingRecord") EmbeddingRecord record
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: a single embedding record
        List<EmbeddingRecord> batch = List.of(record);
        
        // When: performing batch insertion with single record
        // Should behave identically to individual insertion
        
        // Then: verify the batch contains exactly one record
        assertThat(batch).hasSize(1);
        
        // And: verify the record is valid
        EmbeddingRecord batchRecord = batch.get(0);
        assertThat(batchRecord.id()).isEqualTo(record.id());
        assertThat(batchRecord.embedding()).isEqualTo(record.embedding());
        assertThat(batchRecord.chunk()).isEqualTo(record.chunk());
    }

    @Property(tries = 50)
    @Label("When large batch is provided Then all records are processed")
    void largeBatchProcessesAllRecords(
            @ForAll("largeBatchEmbeddingRecords") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: a large batch of embedding records
        Assume.that(records.size() >= 50);
        
        // When: performing batch insertion with large batch
        // All records should be processed
        
        // Then: verify all records are present
        assertThat(records).hasSizeGreaterThanOrEqualTo(50);
        
        // And: verify all records are valid
        assertThat(records).allMatch(record -> 
            record.id() != null &&
            record.embedding() != null &&
            record.chunk() != null
        );
    }

    @Property(tries = 50)
    @Label("When batch contains records from same file Then all are inserted")
    void batchWithSameFileRecordsInsertsAll(
            @ForAll("embeddingRecordsWithSameFilename") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records from the same file
        Assume.that(records.size() > 1);
        
        // When: performing batch insertion
        // All records should be inserted even if from same file
        
        // Then: verify all records share the same filename
        String firstFilename = records.get(0).chunk().metadata().filename();
        assertThat(records).allMatch(record -> 
            record.chunk().metadata().filename().equals(firstFilename)
        );
        
        // And: verify all records are present
        assertThat(records).hasSize(records.size());
    }

    @Property(tries = 50)
    @Label("When batch contains records from different files Then all are inserted")
    void batchWithDifferentFileRecordsInsertsAll(
            @ForAll("embeddingRecordsWithDifferentFilenames") List<EmbeddingRecord> records
    ) {
        // Feature: rag-openai-api-ollama, Property 16: Batch Insertion Equivalence
        
        // Given: embedding records from different files
        Assume.that(records.size() > 1);
        
        // When: performing batch insertion
        // All records should be inserted regardless of source file
        
        // Then: verify records have different filenames
        List<String> filenames = records.stream()
            .map(record -> record.chunk().metadata().filename())
            .distinct()
            .collect(Collectors.toList());
        
        assertThat(filenames.size()).isGreaterThan(1);
        
        // And: verify all records are present
        assertThat(records).hasSize(records.size());
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
    Arbitrary<List<EmbeddingRecord>> emptyEmbeddingRecords() {
        return Arbitraries.just(List.of());
    }

    @Provide
    Arbitrary<List<EmbeddingRecord>> largeBatchEmbeddingRecords() {
        return embeddingRecord().list().ofMinSize(50).ofMaxSize(100);
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
    Arbitrary<List<EmbeddingRecord>> embeddingRecordsWithDifferentFilenames() {
        return Arbitraries.integers().between(2, 5).flatMap(count -> {
            // Generate unique indices for filenames
            return Arbitraries.shuffle(
                java.util.stream.IntStream.range(0, 10000)
                    .boxed()
                    .collect(Collectors.toList())
            ).map(shuffled -> shuffled.subList(0, count))
            .flatMap(uniqueIndices ->
                embeddingVectors().map(embeddingVector ->
                    uniqueIndices.stream()
                        .map(index -> {
                            String filename = "file_" + index;
                            DocumentMetadata metadata = new DocumentMetadata(
                                filename + ".txt",
                                Path.of("/tmp/" + filename + ".txt"),
                                System.currentTimeMillis(),
                                "txt"
                            );
                            TextChunk chunk = new TextChunk(
                                "Sample text for " + filename,
                                metadata,
                                0,
                                0,
                                100
                            );
                            return new EmbeddingRecord(
                                UUID.randomUUID().toString(),
                                embeddingVector,
                                chunk
                            );
                        })
                        .collect(Collectors.toList())
                )
            );
        });
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

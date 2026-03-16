package com.rag.openai.client;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.qdrant.VectorStoreClientImpl;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.QdrantConfig;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.ScoredChunk;
import com.rag.openai.domain.model.TextChunk;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VectorStoreClient implementation.
 * Tests collection creation, embedding storage, similarity search, deletion, and retry logic.
 */
@ExtendWith(MockitoExtension.class)
class VectorStoreClientTest {

    @Mock
    private QdrantClient mockQdrantClient;

    @Mock
    private OllamaClient mockOllamaClient;

    private QdrantConfig qdrantConfig;
    private OllamaConfig ollamaConfig;
    private VectorStoreClient vectorStoreClient;

    @BeforeEach
    void setUp() {
        // Given: Configuration for Qdrant and Ollama
        qdrantConfig = new QdrantConfig(
            "localhost",
            6334,
            "test-collection",
            Duration.ofSeconds(10)
        );
        
        ollamaConfig = new OllamaConfig(
            "localhost",
            11434,
            "gpt-oss:20b",
            "qwen3-embedding:8b",
            "qwen3-vl:8b",
            Duration.ofSeconds(30),
            Duration.ofSeconds(120)
        );
    }

    @Test
    @DisplayName("When ensuring collection exists and collection is present Then no creation occurs")
    void testEnsureCollectionExistsAlreadyPresent() throws Exception {
        // Given: A collection that already exists
        when(mockQdrantClient.listCollectionsAsync())
            .thenReturn(Futures.immediateFuture(List.of("test-collection", "other-collection")));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Ensuring the collection exists
        CompletableFuture<Void> result = vectorStoreClient.ensureCollectionExists("test-collection", 768);

        // Then: The operation completes without creating a new collection
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).listCollectionsAsync();
        verify(mockQdrantClient, never()).createCollectionAsync(anyString(), any(Collections.VectorParams.class));
    }

    @Test
    @DisplayName("When ensuring collection exists and collection is absent Then collection is created")
    void testEnsureCollectionExistsCreatesNew() throws Exception {
        // Given: A collection that does not exist
        when(mockQdrantClient.listCollectionsAsync())
            .thenReturn(Futures.immediateFuture(List.of("other-collection")));
        
        // Mock the createCollectionAsync to return null (void-like behavior)
        @SuppressWarnings("unchecked")
        ListenableFuture<Object> mockFuture = mock(ListenableFuture.class);
        when(mockFuture.get()).thenReturn(null);
        when(mockQdrantClient.createCollectionAsync(anyString(), any(Collections.VectorParams.class)))
            .thenReturn((ListenableFuture) mockFuture);
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Ensuring the collection exists
        CompletableFuture<Void> result = vectorStoreClient.ensureCollectionExists("test-collection", 768);

        // Then: A new collection is created
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).listCollectionsAsync();
        verify(mockQdrantClient).createCollectionAsync(eq("test-collection"), any(Collections.VectorParams.class));
    }

    @Test
    @DisplayName("When storing embeddings with valid records Then embeddings are stored successfully")
    void testStoreEmbeddingsSuccess() throws Exception {
        // Given: Valid embedding records
        DocumentMetadata metadata = new DocumentMetadata(
            "test.pdf",
            Paths.get("documents/test.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );
        
        TextChunk chunk = new TextChunk(
            "This is a test chunk of text.",
            metadata,
            0,
            0,
            29
        );
        
        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        EmbeddingRecord record = new EmbeddingRecord(
            UUID.randomUUID().toString(),
            embedding,
            chunk
        );
        
        when(mockQdrantClient.upsertAsync(anyString(), anyList()))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Storing embeddings
        CompletableFuture<Void> result = vectorStoreClient.storeEmbeddings(List.of(record));

        // Then: The embeddings are stored successfully
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).upsertAsync(eq("test-collection"), anyList());
    }

    @Test
    @DisplayName("When storing embeddings with empty list Then no operation is performed")
    void testStoreEmbeddingsEmptyList() throws Exception {
        // Given: An empty list of embeddings
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Storing empty embeddings
        CompletableFuture<Void> result = vectorStoreClient.storeEmbeddings(List.of());

        // Then: No operation is performed
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient, never()).upsertAsync(anyString(), anyList());
    }

    @Test
    @DisplayName("When storing embeddings with multiple records Then batch storage occurs")
    void testStoreEmbeddingsBatch() throws Exception {
        // Given: Multiple embedding records
        DocumentMetadata metadata = new DocumentMetadata(
            "test.pdf",
            Paths.get("documents/test.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );
        
        List<EmbeddingRecord> records = List.of(
            new EmbeddingRecord(
                UUID.randomUUID().toString(),
                List.of(0.1f, 0.2f, 0.3f),
                new TextChunk("Chunk 1", metadata, 0, 0, 7)
            ),
            new EmbeddingRecord(
                UUID.randomUUID().toString(),
                List.of(0.4f, 0.5f, 0.6f),
                new TextChunk("Chunk 2", metadata, 1, 8, 15)
            ),
            new EmbeddingRecord(
                UUID.randomUUID().toString(),
                List.of(0.7f, 0.8f, 0.9f),
                new TextChunk("Chunk 3", metadata, 2, 16, 23)
            )
        );
        
        when(mockQdrantClient.upsertAsync(anyString(), anyList()))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Storing multiple embeddings
        CompletableFuture<Void> result = vectorStoreClient.storeEmbeddings(records);

        // Then: All embeddings are stored in a batch
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).upsertAsync(eq("test-collection"), argThat(list -> 
            list != null && list.size() == 3
        ));
    }

    @Test
    @DisplayName("When searching for similar vectors Then matching chunks are returned")
    void testSearchSimilarSuccess() throws Exception {
        // Given: A query embedding and matching results
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        
        // Create mock scored points
        Points.ScoredPoint scoredPoint1 = createMockScoredPoint(
            "id1",
            0.95f,
            "test1.pdf",
            "This is the first matching chunk.",
            0,
            0,
            33
        );
        
        Points.ScoredPoint scoredPoint2 = createMockScoredPoint(
            "id2",
            0.87f,
            "test2.pdf",
            "This is the second matching chunk.",
            0,
            0,
            34
        );
        
        when(mockQdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFuture(List.of(scoredPoint1, scoredPoint2)));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Searching for similar vectors
        CompletableFuture<List<ScoredChunk>> result = vectorStoreClient.searchSimilar(queryEmbedding, 5);

        // Then: Matching chunks are returned with scores
        assertThat(result).isNotNull();
        List<ScoredChunk> chunks = result.get();
        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).score()).isEqualTo(0.95f);
        assertThat(chunks.get(0).chunk().text()).isEqualTo("This is the first matching chunk.");
        assertThat(chunks.get(1).score()).isEqualTo(0.87f);
        assertThat(chunks.get(1).chunk().text()).isEqualTo("This is the second matching chunk.");
        
        verify(mockQdrantClient).searchAsync(any(Points.SearchPoints.class));
    }

    @Test
    @DisplayName("When searching for similar vectors with no matches Then empty list is returned")
    void testSearchSimilarNoMatches() throws Exception {
        // Given: A query embedding with no matching results
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
        
        when(mockQdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFuture(List.of()));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Searching for similar vectors
        CompletableFuture<List<ScoredChunk>> result = vectorStoreClient.searchSimilar(queryEmbedding, 5);

        // Then: An empty list is returned
        assertThat(result).isNotNull();
        List<ScoredChunk> chunks = result.get();
        assertThat(chunks).isEmpty();
        
        verify(mockQdrantClient).searchAsync(any(Points.SearchPoints.class));
    }

    @Test
    @DisplayName("When searching with topK parameter Then correct limit is applied")
    void testSearchSimilarTopKLimit() throws Exception {
        // Given: A query embedding and topK limit
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
        int topK = 3;
        
        when(mockQdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFuture(List.of()));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Searching with specific topK
        CompletableFuture<List<ScoredChunk>> result = vectorStoreClient.searchSimilar(queryEmbedding, topK);

        // Then: The search is performed with correct limit
        assertThat(result).isNotNull();
        result.get();
        
        verify(mockQdrantClient).searchAsync(argThat(searchPoints ->
            searchPoints != null && searchPoints.getLimit() == topK
        ));
    }

    @Test
    @DisplayName("When deleting embeddings by filename Then matching embeddings are removed")
    void testDeleteEmbeddingsByFilenameSuccess() throws Exception {
        // Given: A filename to delete
        String filename = "test.pdf";
        
        when(mockQdrantClient.deleteAsync(anyString(), any(Points.Filter.class)))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Deleting embeddings by filename
        CompletableFuture<Void> result = vectorStoreClient.deleteEmbeddingsByFilename(filename);

        // Then: The embeddings are deleted successfully
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).deleteAsync(eq("test-collection"), any(Points.Filter.class));
    }

    @Test
    @DisplayName("When deleting embeddings with special characters in filename Then deletion succeeds")
    void testDeleteEmbeddingsByFilenameSpecialCharacters() throws Exception {
        // Given: A filename with special characters
        String filename = "test-file (copy) [2024].pdf";
        
        when(mockQdrantClient.deleteAsync(anyString(), any(Points.Filter.class)))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Deleting embeddings by filename
        CompletableFuture<Void> result = vectorStoreClient.deleteEmbeddingsByFilename(filename);

        // Then: The embeddings are deleted successfully
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockQdrantClient).deleteAsync(eq("test-collection"), any(Points.Filter.class));
    }

    @Test
    @DisplayName("When Qdrant connection fails during store Then retry logic is triggered")
    void testStoreEmbeddingsRetryOnFailure() throws Exception {
        // Given: A connection that fails initially then succeeds
        DocumentMetadata metadata = new DocumentMetadata(
            "test.pdf",
            Paths.get("documents/test.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );
        
        EmbeddingRecord record = new EmbeddingRecord(
            UUID.randomUUID().toString(),
            List.of(0.1f, 0.2f, 0.3f),
            new TextChunk("Test chunk", metadata, 0, 0, 10)
        );
        
        when(mockQdrantClient.upsertAsync(anyString(), anyList()))
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Connection failed")))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Storing embeddings with initial failure
        CompletableFuture<Void> result = vectorStoreClient.storeEmbeddings(List.of(record));

        // Then: The operation retries and eventually succeeds
        assertThat(result).isNotNull();
        result.get(); // Should not throw after retry
        
        verify(mockQdrantClient, times(2)).upsertAsync(eq("test-collection"), anyList());
    }

    @Test
    @DisplayName("When Qdrant connection fails during search Then retry logic is triggered")
    void testSearchSimilarRetryOnFailure() throws Exception {
        // Given: A connection that fails initially then succeeds
        List<Float> queryEmbedding = List.of(0.1f, 0.2f, 0.3f);
        
        Points.ScoredPoint scoredPoint = createMockScoredPoint(
            "id1",
            0.95f,
            "test.pdf",
            "Test chunk",
            0,
            0,
            10
        );
        
        when(mockQdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Connection failed")))
            .thenReturn(Futures.immediateFuture(List.of(scoredPoint)));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Searching with initial failure
        CompletableFuture<List<ScoredChunk>> result = vectorStoreClient.searchSimilar(queryEmbedding, 5);

        // Then: The operation retries and eventually succeeds
        assertThat(result).isNotNull();
        List<ScoredChunk> chunks = result.get();
        assertThat(chunks).hasSize(1);
        
        verify(mockQdrantClient, times(2)).searchAsync(any(Points.SearchPoints.class));
    }

    @Test
    @DisplayName("When Qdrant connection fails during delete Then retry logic is triggered")
    void testDeleteEmbeddingsRetryOnFailure() throws Exception {
        // Given: A connection that fails initially then succeeds
        String filename = "test.pdf";
        
        when(mockQdrantClient.deleteAsync(anyString(), any(Points.Filter.class)))
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Connection failed")))
            .thenReturn(Futures.immediateFuture(null));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Deleting with initial failure
        CompletableFuture<Void> result = vectorStoreClient.deleteEmbeddingsByFilename(filename);

        // Then: The operation retries and eventually succeeds
        assertThat(result).isNotNull();
        result.get(); // Should not throw after retry
        
        verify(mockQdrantClient, times(2)).deleteAsync(eq("test-collection"), any(Points.Filter.class));
    }

    @Test
    @DisplayName("When Qdrant connection fails repeatedly Then exception is thrown after max retries")
    void testStoreEmbeddingsMaxRetriesExceeded() throws Exception {
        // Given: A connection that always fails
        DocumentMetadata metadata = new DocumentMetadata(
            "test.pdf",
            Paths.get("documents/test.pdf"),
            System.currentTimeMillis(),
            "pdf"
        );
        
        EmbeddingRecord record = new EmbeddingRecord(
            UUID.randomUUID().toString(),
            List.of(0.1f, 0.2f, 0.3f),
            new TextChunk("Test chunk", metadata, 0, 0, 10)
        );
        
        when(mockQdrantClient.upsertAsync(anyString(), anyList()))
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Connection failed")));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Storing embeddings with persistent failure
        CompletableFuture<Void> result = vectorStoreClient.storeEmbeddings(List.of(record));

        // Then: An exception is thrown after max retries
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get(10, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(java.util.concurrent.ExecutionException.class)
            .hasCauseInstanceOf(RuntimeException.class);
        
        // Verify retry attempts (3 attempts total)
        verify(mockQdrantClient, times(3)).upsertAsync(eq("test-collection"), anyList());
    }

    @Test
    @DisplayName("When verifying connectivity with healthy Qdrant Then true is returned")
    void testVerifyConnectivitySuccess() throws Exception {
        // Given: A healthy Qdrant connection
        when(mockQdrantClient.listCollectionsAsync())
            .thenReturn(Futures.immediateFuture(List.of("test-collection")));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = vectorStoreClient.verifyConnectivity();

        // Then: True is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isTrue();
        
        verify(mockQdrantClient).listCollectionsAsync();
    }

    @Test
    @DisplayName("When verifying connectivity with unavailable Qdrant Then false is returned")
    void testVerifyConnectivityFailure() throws Exception {
        // Given: An unavailable Qdrant connection
        when(mockQdrantClient.listCollectionsAsync())
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Connection failed")));
        
        vectorStoreClient = createVectorStoreClientWithMocks();

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = vectorStoreClient.verifyConnectivity();

        // Then: False is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isFalse();
        
        verify(mockQdrantClient).listCollectionsAsync();
    }

    /**
     * Helper method to create VectorStoreClientImpl with mocked QdrantClient.
     * Uses reflection to inject mocks since the client initializes in constructor.
     */
    private VectorStoreClient createVectorStoreClientWithMocks() {
        try {
            VectorStoreClientImpl client = new VectorStoreClientImpl(
                qdrantConfig,
                ollamaConfig,
                mockOllamaClient
            );
            
            // Use reflection to inject mocked QdrantClient
            java.lang.reflect.Field clientField = VectorStoreClientImpl.class.getDeclaredField("qdrantClient");
            clientField.setAccessible(true);
            clientField.set(client, mockQdrantClient);
            
            return client;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create VectorStoreClient with mocks", e);
        }
    }

    /**
     * Helper method to create a mock ScoredPoint with payload.
     */
    private Points.ScoredPoint createMockScoredPoint(
            String id,
            float score,
            String filename,
            String text,
            int chunkIndex,
            int startPosition,
            int endPosition) {
        
        // Create payload map
        var payload = new java.util.HashMap<String, io.qdrant.client.grpc.JsonWithInt.Value>();
        payload.put("filename", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(filename)
            .build());
        payload.put("text", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(text)
            .build());
        payload.put("chunkIndex", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(chunkIndex)
            .build());
        payload.put("startPosition", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(startPosition)
            .build());
        payload.put("endPosition", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(endPosition)
            .build());
        payload.put("filePath", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue("documents/" + filename)
            .build());
        payload.put("lastModified", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(System.currentTimeMillis())
            .build());
        payload.put("fileType", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue("pdf")
            .build());
        
        return Points.ScoredPoint.newBuilder()
            .setId(Points.PointId.newBuilder().setUuid(id).build())
            .setScore(score)
            .putAllPayload(payload)
            .build();
    }
}

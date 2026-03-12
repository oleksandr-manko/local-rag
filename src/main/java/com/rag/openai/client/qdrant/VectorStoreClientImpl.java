package com.rag.openai.client.qdrant;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.config.QdrantConfig;
import com.rag.openai.domain.model.DocumentMetadata;
import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.ScoredChunk;
import com.rag.openai.domain.model.TextChunk;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Implementation of VectorStoreClient using Qdrant gRPC client.
 * Handles vector storage, retrieval, and deletion operations with retry logic.
 */
@Component
public class VectorStoreClientImpl implements VectorStoreClient {
    
    private static final Logger logger = LoggerFactory.getLogger(VectorStoreClientImpl.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(100);
    
    private final QdrantClient qdrantClient;
    private final QdrantConfig qdrantConfig;
    
    public VectorStoreClientImpl(
            QdrantConfig qdrantConfig,
            OllamaConfig ollamaConfig,
            OllamaClient ollamaClient) {
        this.qdrantConfig = qdrantConfig;
        this.qdrantClient = new QdrantClient(
            QdrantGrpcClient.newBuilder(
                qdrantConfig.host(),
                qdrantConfig.port(),
                false
            ).build()
        );
        logger.info("VectorStoreClient initialized for {}:{}", qdrantConfig.host(), qdrantConfig.port());
    }
    
    @Override
    public CompletableFuture<Void> ensureCollectionExists(String collectionName, int vectorDimension) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Checking if collection '{}' exists", collectionName);
                
                // Check if collection exists
                boolean exists = qdrantClient.listCollectionsAsync()
                    .get()
                    .stream()
                    .anyMatch(name -> name.equals(collectionName));
                
                if (!exists) {
                    logger.info("Creating collection '{}' with dimension {}", collectionName, vectorDimension);
                    
                    qdrantClient.createCollectionAsync(
                        collectionName,
                        Collections.VectorParams.newBuilder()
                            .setSize(vectorDimension)
                            .setDistance(Collections.Distance.Cosine)
                            .build()
                    ).get();
                    
                    logger.info("Collection '{}' created successfully", collectionName);
                } else {
                    logger.debug("Collection '{}' already exists", collectionName);
                }
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to ensure collection exists: {}", collectionName, e);
                throw new RuntimeException("Failed to ensure collection exists", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> storeEmbeddings(List<EmbeddingRecord> records) {
        if (records.isEmpty()) {
            logger.debug("No embeddings to store");
            return CompletableFuture.completedFuture(null);
        }
        
        return retryWithBackoff(() -> {
            try {
                logger.debug("Storing {} embeddings", records.size());
                
                List<Points.PointStruct> points = records.stream()
                    .map(this::toQdrantPoint)
                    .collect(Collectors.toList());
                
                qdrantClient.upsertAsync(
                    qdrantConfig.collectionName(),
                    points
                ).get();
                
                logger.info("Successfully stored {} embeddings", records.size());
                return null;
            } catch (Exception e) {
                logger.error("Failed to store embeddings", e);
                throw new RuntimeException("Failed to store embeddings", e);
            }
        }, "storeEmbeddings");
    }
    
    @Override
    public CompletableFuture<List<ScoredChunk>> searchSimilar(List<Float> queryEmbedding, int topK) {
        return retryWithBackoff(() -> {
            try {
                logger.debug("Searching for top {} similar vectors", topK);
                
                List<Points.ScoredPoint> results = qdrantClient.searchAsync(
                    Points.SearchPoints.newBuilder()
                        .setCollectionName(qdrantConfig.collectionName())
                        .addAllVector(queryEmbedding)
                        .setLimit(topK)
                        .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                        .build()
                ).get();
                
                List<ScoredChunk> scoredChunks = results.stream()
                    .map(this::toScoredChunk)
                    .collect(Collectors.toList());
                
                logger.debug("Found {} similar chunks", scoredChunks.size());
                return scoredChunks;
            } catch (Exception e) {
                logger.error("Failed to search similar vectors", e);
                throw new RuntimeException("Failed to search similar vectors", e);
            }
        }, "searchSimilar");
    }
    
    @Override
    public CompletableFuture<Void> deleteEmbeddingsByFilename(String filename) {
        return retryWithBackoff(() -> {
            try {
                logger.debug("Deleting embeddings for filename: {}", filename);
                
                // Create filter for filename
                Points.Filter filter = Points.Filter.newBuilder()
                    .addMust(Points.Condition.newBuilder()
                        .setField(Points.FieldCondition.newBuilder()
                            .setKey("filename")
                            .setMatch(Points.Match.newBuilder()
                                .setKeyword(filename)
                                .build())
                            .build())
                        .build())
                    .build();
                
                qdrantClient.deleteAsync(
                    qdrantConfig.collectionName(),
                    filter
                ).get();
                
                logger.info("Successfully deleted embeddings for filename: {}", filename);
                return null;
            } catch (Exception e) {
                logger.error("Failed to delete embeddings for filename: {}", filename, e);
                throw new RuntimeException("Failed to delete embeddings", e);
            }
        }, "deleteEmbeddingsByFilename");
    }
    
    @Override
    public CompletableFuture<Boolean> verifyConnectivity() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Verifying Qdrant connectivity");
                qdrantClient.listCollectionsAsync().get();
                logger.debug("Qdrant connectivity verified");
                return true;
            } catch (Exception e) {
                logger.error("Qdrant connectivity check failed", e);
                return false;
            }
        });
    }

    /**
     * Retry an operation with exponential backoff.
     */
    private <T> CompletableFuture<T> retryWithBackoff(
            java.util.function.Supplier<T> operation,
            String operationName) {
        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            Duration backoff = INITIAL_BACKOFF;
            
            while (attempt < MAX_RETRIES) {
                try {
                    return operation.get();
                } catch (Exception e) {
                    attempt++;
                    if (attempt >= MAX_RETRIES) {
                        logger.error("Operation '{}' failed after {} attempts", operationName, MAX_RETRIES);
                        throw e;
                    }
                    
                    logger.warn("Operation '{}' failed (attempt {}/{}), retrying after {}ms",
                        operationName, attempt, MAX_RETRIES, backoff.toMillis());
                    
                    try {
                        Thread.sleep(backoff.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Retry interrupted", ie);
                    }
                    
                    backoff = backoff.multipliedBy(2);
                }
            }
            
            throw new RuntimeException("Should not reach here");
        });
    }
    
    /**
     * Convert an EmbeddingRecord to a Qdrant PointStruct.
     */
    private Points.PointStruct toQdrantPoint(EmbeddingRecord record) {
        TextChunk chunk = record.chunk();
        DocumentMetadata metadata = chunk.metadata();
        
        // Build vectors
        Points.Vectors vectors = Points.Vectors.newBuilder()
            .setVector(Points.Vector.newBuilder()
                .addAllData(record.embedding())
                .build())
            .build();
        
        // Build payload using Qdrant's JsonWithInt.Value
        Map<String, io.qdrant.client.grpc.JsonWithInt.Value> payload = new HashMap<>();
        payload.put("filename", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(metadata.filename())
            .build());
        payload.put("text", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(chunk.text())
            .build());
        payload.put("chunkIndex", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(chunk.chunkIndex())
            .build());
        payload.put("startPosition", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(chunk.startPosition())
            .build());
        payload.put("endPosition", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(chunk.endPosition())
            .build());
        payload.put("filePath", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(metadata.filePath().toString())
            .build());
        payload.put("lastModified", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setIntegerValue(metadata.lastModified())
            .build());
        payload.put("fileType", io.qdrant.client.grpc.JsonWithInt.Value.newBuilder()
            .setStringValue(metadata.fileType())
            .build());
        
        return Points.PointStruct.newBuilder()
            .setId(Points.PointId.newBuilder().setUuid(record.id()).build())
            .setVectors(vectors)
            .putAllPayload(payload)
            .build();
    }

    /**
     * Convert a Qdrant ScoredPoint to a ScoredChunk.
     */
    private ScoredChunk toScoredChunk(Points.ScoredPoint scoredPoint) {
        var payload = scoredPoint.getPayloadMap();
        
        String filename = payload.get("filename").getStringValue();
        String text = payload.get("text").getStringValue();
        int chunkIndex = (int) payload.get("chunkIndex").getIntegerValue();
        int startPosition = (int) payload.get("startPosition").getIntegerValue();
        int endPosition = (int) payload.get("endPosition").getIntegerValue();
        String filePath = payload.get("filePath").getStringValue();
        long lastModified = payload.get("lastModified").getIntegerValue();
        String fileType = payload.get("fileType").getStringValue();
        
        DocumentMetadata metadata = new DocumentMetadata(
            filename,
            Path.of(filePath),
            lastModified,
            fileType
        );
        
        TextChunk chunk = new TextChunk(
            text,
            metadata,
            chunkIndex,
            startPosition,
            endPosition
        );
        
        return new ScoredChunk(chunk, scoredPoint.getScore());
    }
}

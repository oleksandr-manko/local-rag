package com.rag.openai.client.qdrant;

import com.rag.openai.domain.model.EmbeddingRecord;
import com.rag.openai.domain.model.ScoredChunk;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for interacting with Qdrant vector database.
 * Provides methods for storing and retrieving vector embeddings.
 */
public interface VectorStoreClient {
    
    /**
     * Ensure the collection exists in Qdrant, creating it if necessary.
     * 
     * @param collectionName The name of the collection
     * @param vectorDimension The dimension of the vectors to store
     * @return CompletableFuture that completes when the collection is ready
     */
    CompletableFuture<Void> ensureCollectionExists(String collectionName, int vectorDimension);
    
    /**
     * Store embeddings in the vector database with batch support.
     * 
     * @param records The embedding records to store
     * @return CompletableFuture that completes when storage is complete
     */
    CompletableFuture<Void> storeEmbeddings(List<EmbeddingRecord> records);
    
    /**
     * Search for similar vectors in the database.
     * 
     * @param queryEmbedding The query vector
     * @param topK The number of results to return
     * @return CompletableFuture containing the top K most similar chunks with scores
     */
    CompletableFuture<List<ScoredChunk>> searchSimilar(List<Float> queryEmbedding, int topK);
    
    /**
     * Delete all embeddings associated with a specific filename.
     * 
     * @param filename The filename to delete embeddings for
     * @return CompletableFuture that completes when deletion is complete
     */
    CompletableFuture<Void> deleteEmbeddingsByFilename(String filename);
    
    /**
     * Verify connectivity to the Qdrant server.
     * 
     * @return CompletableFuture containing true if connected, false otherwise
     */
    CompletableFuture<Boolean> verifyConnectivity();
}

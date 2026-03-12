package com.rag.openai.client.redis;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Client interface for interacting with Redis to store and retrieve file hashes.
 * Used for tracking document processing state to avoid reprocessing unchanged files.
 */
public interface RedisClient {
    
    /**
     * Retrieves the stored hash for a given file path.
     *
     * @param filePath the path of the file
     * @return a CompletableFuture containing an Optional with the hash if found, empty otherwise
     */
    CompletableFuture<Optional<String>> getFileHash(Path filePath);
    
    /**
     * Stores a file hash in Redis.
     *
     * @param filePath the path of the file
     * @param hash the SHA-256 hash of the file
     * @return a CompletableFuture that completes when the hash is stored
     */
    CompletableFuture<Void> storeFileHash(Path filePath, String hash);
    
    /**
     * Retrieves all stored file hashes.
     *
     * @return a CompletableFuture containing a map of file paths to their hashes
     */
    CompletableFuture<Map<Path, String>> getAllFileHashes();
    
    /**
     * Deletes a file hash from Redis.
     *
     * @param filePath the path of the file
     * @return a CompletableFuture that completes when the hash is deleted
     */
    CompletableFuture<Void> deleteFileHash(Path filePath);
    
    /**
     * Verifies connectivity to the Redis server.
     *
     * @return a CompletableFuture containing true if connected, false otherwise
     */
    CompletableFuture<Boolean> verifyConnectivity();
}

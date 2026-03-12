package com.rag.openai.client.redis;

import com.rag.openai.config.RedisConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of RedisClient using Lettuce Redis client with connection pooling.
 * Stores file hashes to track document processing state.
 */
@Component
public class RedisClientImpl implements com.rag.openai.client.redis.RedisClient {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisClientImpl.class);
    private static final String HASH_KEY_PREFIX = "file:hash:";
    
    private final RedisConfig config;
    private RedisClient redisClient;
    private GenericObjectPool<StatefulRedisConnection<String, String>> connectionPool;
    
    public RedisClientImpl(RedisConfig config) {
        this.config = config;
    }
    
    @PostConstruct
    public void initialize() {
        try {
            RedisURI redisUri = RedisURI.builder()
                .withHost(config.host())
                .withPort(config.port())
                .withDatabase(config.database())
                .withTimeout(config.connectionTimeout())
                .build();
            
            redisClient = RedisClient.create(redisUri);
            
            // Configure connection pool
            GenericObjectPoolConfig<StatefulRedisConnection<String, String>> poolConfig = 
                new GenericObjectPoolConfig<>();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(2);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            
            connectionPool = ConnectionPoolSupport.createGenericObjectPool(
                () -> redisClient.connect(),
                poolConfig
            );
            
            logger.info("Redis client initialized successfully: {}:{}", 
                config.host(), config.port());
        } catch (Exception e) {
            logger.error("Failed to initialize Redis client: {}", e.getMessage(), e);
            throw new RuntimeException("Redis initialization failed", e);
        }
    }
    
    @PreDestroy
    public void shutdown() {
        try {
            if (connectionPool != null) {
                connectionPool.close();
            }
            if (redisClient != null) {
                redisClient.shutdown();
            }
            logger.info("Redis client shut down successfully");
        } catch (Exception e) {
            logger.error("Error during Redis client shutdown: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public CompletableFuture<Optional<String>> getFileHash(Path filePath) {
        String key = buildKey(filePath);
        
        return executeWithConnection(commands -> 
            commands.get(key)
                .toCompletableFuture()
                .thenApply(Optional::ofNullable)
                .exceptionally(throwable -> {
                    logger.error("Error retrieving hash for file {}: {}", 
                        filePath, throwable.getMessage(), throwable);
                    return Optional.empty();
                })
        );
    }
    
    @Override
    public CompletableFuture<Void> storeFileHash(Path filePath, String hash) {
        String key = buildKey(filePath);
        
        return executeWithConnection(commands ->
            commands.set(key, hash)
                .toCompletableFuture()
                .thenApply(result -> {
                    logger.debug("Stored hash for file {}: {}", filePath, hash);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    logger.error("Error storing hash for file {}: {}", 
                        filePath, throwable.getMessage(), throwable);
                    return null;
                })
        );
    }
    
    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<Path, String>> getAllFileHashes() {
        return executeWithConnection(commands ->
            commands.keys(HASH_KEY_PREFIX + "*")
                .toCompletableFuture()
                .thenCompose(keys -> {
                    if (keys == null || keys.isEmpty()) {
                        return CompletableFuture.completedFuture(Map.<Path, String>of());
                    }
                    
                    // Fetch all values for the keys
                    CompletableFuture<Map.Entry<Path, String>>[] futures = keys.stream()
                        .map(key -> commands.get(key).toCompletableFuture()
                            .thenApply(value -> Map.entry(extractPath(key), value)))
                        .toArray(CompletableFuture[]::new);
                    
                    return CompletableFuture.allOf(futures)
                        .thenApply(v -> {
                            Map<Path, String> result = new HashMap<>();
                            for (CompletableFuture<Map.Entry<Path, String>> future : futures) {
                                Map.Entry<Path, String> entry = future.join();
                                if (entry.getValue() != null) {
                                    result.put(entry.getKey(), entry.getValue());
                                }
                            }
                            return result;
                        });
                })
                .exceptionally(throwable -> {
                    logger.error("Error retrieving all file hashes: {}", 
                        throwable.getMessage(), throwable);
                    return Map.of();
                })
        );
    }
    
    @Override
    public CompletableFuture<Void> deleteFileHash(Path filePath) {
        String key = buildKey(filePath);
        
        return executeWithConnection(commands ->
            commands.del(key)
                .toCompletableFuture()
                .thenApply(result -> {
                    logger.debug("Deleted hash for file {}", filePath);
                    return (Void) null;
                })
                .exceptionally(throwable -> {
                    logger.error("Error deleting hash for file {}: {}", 
                        filePath, throwable.getMessage(), throwable);
                    return null;
                })
        );
    }
    
    @Override
    public CompletableFuture<Boolean> verifyConnectivity() {
        return executeWithConnection(commands ->
            commands.ping()
                .toCompletableFuture()
                .thenApply(response -> {
                    boolean isConnected = "PONG".equalsIgnoreCase(response);
                    if (isConnected) {
                        logger.debug("Redis connectivity verified successfully");
                    } else {
                        logger.warn("Redis connectivity check returned unexpected response: {}", 
                            response);
                    }
                    return isConnected;
                })
                .exceptionally(throwable -> {
                    logger.error("Redis connectivity check failed: {}", 
                        throwable.getMessage(), throwable);
                    return false;
                })
        );
    }
    
    /**
     * Executes a Redis operation with a connection from the pool.
     */
    private <T> CompletableFuture<T> executeWithConnection(
            ConnectionOperation<T> operation) {
        StatefulRedisConnection<String, String> connection = null;
        try {
            connection = connectionPool.borrowObject();
            RedisAsyncCommands<String, String> commands = connection.async();
            StatefulRedisConnection<String, String> finalConnection = connection;
            
            return operation.execute(commands)
                .whenComplete((result, throwable) -> {
                    try {
                        connectionPool.returnObject(finalConnection);
                    } catch (Exception e) {
                        logger.error("Error returning connection to pool: {}", 
                            e.getMessage(), e);
                    }
                });
        } catch (Exception e) {
            if (connection != null) {
                try {
                    connectionPool.returnObject(connection);
                } catch (Exception ex) {
                    logger.error("Error returning connection to pool after exception: {}", 
                        ex.getMessage(), ex);
                }
            }
            logger.error("Error executing Redis operation: {}", e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Builds a Redis key from a file path.
     */
    private String buildKey(Path filePath) {
        return HASH_KEY_PREFIX + filePath.toString();
    }
    
    /**
     * Extracts the file path from a Redis key.
     */
    private Path extractPath(String key) {
        String pathString = key.substring(HASH_KEY_PREFIX.length());
        return Paths.get(pathString);
    }
    
    /**
     * Functional interface for Redis operations.
     */
    @FunctionalInterface
    private interface ConnectionOperation<T> {
        CompletableFuture<T> apply(RedisAsyncCommands<String, String> commands);
        
        default CompletableFuture<T> execute(RedisAsyncCommands<String, String> commands) {
            return apply(commands);
        }
    }
}

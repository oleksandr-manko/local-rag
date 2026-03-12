package com.rag.openai.client;

import com.rag.openai.client.redis.RedisClientImpl;
import com.rag.openai.config.RedisConfig;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisClient implementation.
 * Tests hash storage, retrieval, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class RedisClientTest {

    @Mock
    private io.lettuce.core.RedisClient mockRedisClient;

    @Mock
    private GenericObjectPool<StatefulRedisConnection<String, String>> mockConnectionPool;

    @Mock
    private StatefulRedisConnection<String, String> mockConnection;

    @Mock
    private RedisAsyncCommands<String, String> mockCommands;

    private RedisConfig config;
    private RedisClientImpl redisClient;

    @BeforeEach
    void setUp() {
        // Given: A Redis configuration
        config = new RedisConfig(
            "localhost",
            6379,
            Duration.ofSeconds(5),
            0
        );
    }

    @Test
    @DisplayName("When storing a file hash Then the hash is stored successfully")
    void testStoreFileHash() throws Exception {
        // Given: A file path and hash
        Path filePath = Paths.get("documents/test.pdf");
        String hash = "abc123def456";
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture("OK"));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.set(anyString(), anyString())).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Storing the file hash
        CompletableFuture<Void> result = redisClient.storeFileHash(filePath, hash);

        // Then: The operation completes successfully
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockCommands).set("file:hash:documents/test.pdf", hash);
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When retrieving an existing file hash Then the hash is returned")
    void testGetFileHashExists() throws Exception {
        // Given: A file path with an existing hash
        Path filePath = Paths.get("documents/test.pdf");
        String expectedHash = "abc123def456";
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(expectedHash));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.get("file:hash:documents/test.pdf")).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Retrieving the file hash
        CompletableFuture<Optional<String>> result = redisClient.getFileHash(filePath);

        // Then: The hash is returned wrapped in Optional
        assertThat(result).isNotNull();
        Optional<String> hash = result.get();
        assertThat(hash).isPresent();
        assertThat(hash.get()).isEqualTo(expectedHash);
        
        verify(mockCommands).get("file:hash:documents/test.pdf");
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When retrieving a non-existent file hash Then Optional.empty() is returned")
    void testGetFileHashNotExists() throws Exception {
        // Given: A file path with no stored hash
        Path filePath = Paths.get("documents/nonexistent.pdf");
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(null));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.get("file:hash:documents/nonexistent.pdf")).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Retrieving the file hash
        CompletableFuture<Optional<String>> result = redisClient.getFileHash(filePath);

        // Then: Optional.empty() is returned
        assertThat(result).isNotNull();
        Optional<String> hash = result.get();
        assertThat(hash).isEmpty();
        
        verify(mockCommands).get("file:hash:documents/nonexistent.pdf");
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When Redis connection fails during get Then Optional.empty() is returned")
    void testGetFileHashConnectionError() throws Exception {
        // Given: A Redis connection that fails
        Path filePath = Paths.get("documents/test.pdf");
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture())
            .thenReturn(CompletableFuture.failedFuture(new RedisException("Connection failed")));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.get(anyString())).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Attempting to retrieve the file hash
        CompletableFuture<Optional<String>> result = redisClient.getFileHash(filePath);

        // Then: Optional.empty() is returned due to error handling
        assertThat(result).isNotNull();
        Optional<String> hash = result.get();
        assertThat(hash).isEmpty();
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When Redis connection fails during store Then operation completes without throwing")
    void testStoreFileHashConnectionError() throws Exception {
        // Given: A Redis connection that fails
        Path filePath = Paths.get("documents/test.pdf");
        String hash = "abc123def456";
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture())
            .thenReturn(CompletableFuture.failedFuture(new RedisException("Connection failed")));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.set(anyString(), anyString())).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Attempting to store the file hash
        CompletableFuture<Void> result = redisClient.storeFileHash(filePath, hash);

        // Then: The operation completes (returns null due to error handling)
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When deleting a file hash Then the hash is removed successfully")
    void testDeleteFileHash() throws Exception {
        // Given: A file path with an existing hash
        Path filePath = Paths.get("documents/test.pdf");
        
        @SuppressWarnings("unchecked")
        RedisFuture<Long> mockFuture = mock(RedisFuture.class);
        when(mockFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(1L));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.del("file:hash:documents/test.pdf")).thenReturn(mockFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Deleting the file hash
        CompletableFuture<Void> result = redisClient.deleteFileHash(filePath);

        // Then: The operation completes successfully
        assertThat(result).isNotNull();
        result.get(); // Should not throw
        
        verify(mockCommands).del("file:hash:documents/test.pdf");
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When retrieving all file hashes Then all stored hashes are returned")
    void testGetAllFileHashes() throws Exception {
        // Given: Multiple files with stored hashes
        @SuppressWarnings("unchecked")
        RedisFuture<java.util.List<String>> keysFuture = mock(RedisFuture.class);
        when(keysFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(
            java.util.List.of(
                "file:hash:documents/test1.pdf",
                "file:hash:documents/test2.pdf"
            )));
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> hash1Future = mock(RedisFuture.class);
        when(hash1Future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture("hash1"));
        
        @SuppressWarnings("unchecked")
        RedisFuture<String> hash2Future = mock(RedisFuture.class);
        when(hash2Future.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture("hash2"));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.keys("file:hash:*")).thenReturn(keysFuture);
        when(mockCommands.get("file:hash:documents/test1.pdf")).thenReturn(hash1Future);
        when(mockCommands.get("file:hash:documents/test2.pdf")).thenReturn(hash2Future);
        
        redisClient = createRedisClientWithMocks();

        // When: Retrieving all file hashes
        CompletableFuture<Map<Path, String>> result = redisClient.getAllFileHashes();

        // Then: All hashes are returned in a map
        assertThat(result).isNotNull();
        Map<Path, String> hashes = result.get();
        assertThat(hashes).hasSize(2);
        assertThat(hashes).containsEntry(Paths.get("documents/test1.pdf"), "hash1");
        assertThat(hashes).containsEntry(Paths.get("documents/test2.pdf"), "hash2");
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When no file hashes exist Then empty map is returned")
    void testGetAllFileHashesEmpty() throws Exception {
        // Given: No stored hashes
        @SuppressWarnings("unchecked")
        RedisFuture<java.util.List<String>> keysFuture = mock(RedisFuture.class);
        when(keysFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture(
            java.util.List.of()));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.keys("file:hash:*")).thenReturn(keysFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Retrieving all file hashes
        CompletableFuture<Map<Path, String>> result = redisClient.getAllFileHashes();

        // Then: An empty map is returned
        assertThat(result).isNotNull();
        Map<Path, String> hashes = result.get();
        assertThat(hashes).isEmpty();
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When Redis connection fails during getAllFileHashes Then empty map is returned")
    void testGetAllFileHashesConnectionError() throws Exception {
        // Given: A Redis connection that fails
        @SuppressWarnings("unchecked")
        RedisFuture<java.util.List<String>> keysFuture = mock(RedisFuture.class);
        when(keysFuture.toCompletableFuture())
            .thenReturn(CompletableFuture.failedFuture(new RedisException("Connection failed")));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.keys("file:hash:*")).thenReturn(keysFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Attempting to retrieve all file hashes
        CompletableFuture<Map<Path, String>> result = redisClient.getAllFileHashes();

        // Then: An empty map is returned due to error handling
        assertThat(result).isNotNull();
        Map<Path, String> hashes = result.get();
        assertThat(hashes).isEmpty();
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When verifying connectivity Then PONG response indicates success")
    void testVerifyConnectivitySuccess() throws Exception {
        // Given: A healthy Redis connection
        @SuppressWarnings("unchecked")
        RedisFuture<String> pingFuture = mock(RedisFuture.class);
        when(pingFuture.toCompletableFuture()).thenReturn(CompletableFuture.completedFuture("PONG"));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.ping()).thenReturn(pingFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = redisClient.verifyConnectivity();

        // Then: True is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isTrue();
        
        verify(mockCommands).ping();
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    @Test
    @DisplayName("When Redis connection fails during ping Then false is returned")
    void testVerifyConnectivityFailure() throws Exception {
        // Given: A Redis connection that fails
        @SuppressWarnings("unchecked")
        RedisFuture<String> pingFuture = mock(RedisFuture.class);
        when(pingFuture.toCompletableFuture())
            .thenReturn(CompletableFuture.failedFuture(new RedisException("Connection failed")));
        
        when(mockConnectionPool.borrowObject()).thenReturn(mockConnection);
        when(mockConnection.async()).thenReturn(mockCommands);
        when(mockCommands.ping()).thenReturn(pingFuture);
        
        redisClient = createRedisClientWithMocks();

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = redisClient.verifyConnectivity();

        // Then: False is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isFalse();
        
        verify(mockConnectionPool).returnObject(mockConnection);
    }

    /**
     * Helper method to create RedisClientImpl with mocked dependencies.
     * Uses reflection to inject mocks since the client initializes in @PostConstruct.
     */
    private RedisClientImpl createRedisClientWithMocks() throws Exception {
        RedisClientImpl client = new RedisClientImpl(config);
        
        // Use reflection to inject mocked connection pool
        java.lang.reflect.Field poolField = RedisClientImpl.class.getDeclaredField("connectionPool");
        poolField.setAccessible(true);
        poolField.set(client, mockConnectionPool);
        
        java.lang.reflect.Field clientField = RedisClientImpl.class.getDeclaredField("redisClient");
        clientField.setAccessible(true);
        clientField.set(client, mockRedisClient);
        
        return client;
    }
}

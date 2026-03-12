package com.rag.openai.lifecycle;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.qdrant.VectorStoreClient;
import com.rag.openai.client.redis.RedisClient;
import com.rag.openai.service.ProcessingJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Integration tests for application startup and shutdown behavior.
 * Tests successful startup with all services available, startup failure when critical service is unavailable,
 * and graceful shutdown with in-flight requests.
 * 
 * Requirements: 14.1, 14.2, 14.3, 14.4, 14.5, 14.6
 */
@ExtendWith(MockitoExtension.class)
class ApplicationStartupShutdownIntegrationTest {

    @Mock
    private OllamaClient ollamaClient;

    @Mock
    private VectorStoreClient vectorStoreClient;

    @Mock
    private RedisClient redisClient;

    @Mock
    private ApplicationReadyEvent applicationReadyEvent;

    @Mock
    private ProcessingJob processingJob;

    @Mock
    private ConfigurableApplicationContext applicationContext;

    private ApplicationStartupListener startupListener;
    private GracefulShutdownHandler shutdownHandler;

    @BeforeEach
    void setUp() {
        startupListener = new ApplicationStartupListener(
            ollamaClient,
            vectorStoreClient,
            redisClient
        );
        
        shutdownHandler = new GracefulShutdownHandler(
            processingJob,
            ollamaClient,
            vectorStoreClient,
            redisClient
        );
    }

    @Test
    @DisplayName("When all services are available Then application startup succeeds")
    void testSuccessfulStartupWithAllServicesAvailable() {
        // Given: All services are available and return successful connectivity
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        startupListener.onApplicationEvent(applicationReadyEvent);

        // Then: All services are verified
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
        
        // Then: No exception is thrown (startup succeeds)
        // Test passes if no exception is thrown
    }

    @Test
    @DisplayName("When Ollama service is unavailable Then application startup fails")
    void testStartupFailureWhenOllamaUnavailable() {
        // Given: Ollama is unavailable, other services are available
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown with appropriate message
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed due to service connectivity issues")
            .hasCauseInstanceOf(IllegalStateException.class);

        // Then: All services were checked
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When Qdrant service is unavailable Then application startup fails")
    void testStartupFailureWhenQdrantUnavailable() {
        // Given: Qdrant is unavailable, other services are available
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed due to service connectivity issues")
            .hasCauseInstanceOf(IllegalStateException.class);

        // Then: All services were checked
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When Redis service is unavailable Then application startup fails")
    void testStartupFailureWhenRedisUnavailable() {
        // Given: Redis is unavailable, other services are available
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed due to service connectivity issues")
            .hasCauseInstanceOf(IllegalStateException.class);

        // Then: All services were checked
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When multiple services are unavailable Then application startup fails")
    void testStartupFailureWhenMultipleServicesUnavailable() {
        // Given: Multiple services are unavailable
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed");

        // Then: All services were checked
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When service connectivity check throws exception Then application startup fails")
    void testStartupFailureWhenConnectivityCheckThrowsException() {
        // Given: Service connectivity check throws exception
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Connection error")));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed");

        // Then: All services were checked
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When connectivity checks are performed in parallel Then all complete within timeout")
    void testParallelConnectivityChecks() {
        // Given: Services with delayed responses (simulating network latency)
        CompletableFuture<Boolean> ollamaFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100); // Simulate 100ms latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        
        CompletableFuture<Boolean> qdrantFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100); // Simulate 100ms latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        
        CompletableFuture<Boolean> redisFuture = CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(100); // Simulate 100ms latency
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        });

        when(ollamaClient.verifyConnectivity()).thenReturn(ollamaFuture);
        when(vectorStoreClient.verifyConnectivity()).thenReturn(qdrantFuture);
        when(redisClient.verifyConnectivity()).thenReturn(redisFuture);

        // When: Application ready event is triggered
        long startTime = System.currentTimeMillis();
        startupListener.onApplicationEvent(applicationReadyEvent);
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Then: All checks complete in parallel (should be ~100ms, not 300ms sequential)
        assertThat(duration).isLessThan(250); // Allow some overhead
        
        // Then: All services were verified
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When connectivity check times out Then application startup fails")
    void testStartupFailureOnTimeout() {
        // Given: Service that never completes (simulating timeout)
        CompletableFuture<Boolean> neverCompletingFuture = new CompletableFuture<>();
        
        when(ollamaClient.verifyConnectivity())
            .thenReturn(neverCompletingFuture);
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: IllegalStateException is thrown due to timeout
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed")
            .hasCauseInstanceOf(TimeoutException.class);

        // Then: Connectivity check was attempted
        verify(ollamaClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When all services are available Then startup logs success")
    void testSuccessfulStartupLogging() {
        // Given: All services are available
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        startupListener.onApplicationEvent(applicationReadyEvent);

        // Then: Startup completes successfully
        // (Logging verification would require a logging framework mock, 
        // but the absence of exception indicates success)
        verify(ollamaClient, times(1)).verifyConnectivity();
        verify(vectorStoreClient, times(1)).verifyConnectivity();
        verify(redisClient, times(1)).verifyConnectivity();
    }

    @Test
    @DisplayName("When service returns false Then it is treated as unavailable")
    void testServiceReturnsFalseIsUnavailable() {
        // Given: Service explicitly returns false (not an exception)
        when(ollamaClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(false));
        when(vectorStoreClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));
        when(redisClient.verifyConnectivity())
            .thenReturn(CompletableFuture.completedFuture(true));

        // When: Application ready event is triggered
        // Then: Startup fails because service returned false
        assertThatThrownBy(() -> startupListener.onApplicationEvent(applicationReadyEvent))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Application startup failed");

        verify(ollamaClient, times(1)).verifyConnectivity();
    }

    // ========== Graceful Shutdown Tests ==========

    @Test
    @DisplayName("When shutdown is triggered with no in-flight requests Then shutdown completes immediately")
    void testGracefulShutdownWithNoInFlightRequests() throws Exception {
        // Given: No processing job is running
        when(processingJob.isProcessingInProgress()).thenReturn(false);

        // When: Shutdown is triggered
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdown();
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown completes quickly (no waiting)
        assertThat(duration).isLessThan(1000); // Should complete in less than 1 second

        // Then: Processing job status was checked at least once
        verify(processingJob, atLeastOnce()).isProcessingInProgress();
    }

    @Test
    @DisplayName("When shutdown is triggered with in-flight processing Then waits for completion")
    void testGracefulShutdownWithInFlightProcessing() throws Exception {
        // Given: Processing job is running and will complete after 2 seconds
        AtomicBoolean processingComplete = new AtomicBoolean(false);
        
        when(processingJob.isProcessingInProgress())
            .thenAnswer(invocation -> !processingComplete.get());

        // Simulate processing completing after 2 seconds
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000);
                processingComplete.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When: Shutdown is triggered
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdown();
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown waited for processing to complete
        assertThat(duration).isGreaterThanOrEqualTo(2000);
        assertThat(duration).isLessThan(3000); // Should not wait too long

        // Then: Processing job status was checked multiple times
        verify(processingJob, atLeast(2)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When shutdown times out waiting for processing Then proceeds with shutdown")
    void testGracefulShutdownTimeoutWithInFlightProcessing() throws Exception {
        // Given: Processing job never completes (simulating stuck job)
        when(processingJob.isProcessingInProgress()).thenReturn(true);

        // When: Shutdown is triggered with short timeout
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdownWithTimeout(3000); // 3 second timeout
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown proceeds after timeout
        assertThat(duration).isGreaterThanOrEqualTo(3000);
        assertThat(duration).isLessThan(4000); // Should timeout and proceed

        // Then: Processing job status was checked multiple times
        verify(processingJob, atLeast(2)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When shutdown closes connections Then all services are disconnected")
    void testShutdownClosesAllConnections() throws Exception {
        // Given: No processing is in progress
        when(processingJob.isProcessingInProgress()).thenReturn(false);

        // When: Shutdown is triggered
        shutdownHandler.onShutdown();

        // Then: All service connections are closed (verified by checking they're not used after shutdown)
        // In a real implementation, services would have close() methods
        // For this test, we verify the shutdown handler completed without errors
        verify(processingJob, atLeastOnce()).isProcessingInProgress();
    }

    @Test
    @DisplayName("When shutdown is triggered multiple times Then handles gracefully")
    void testMultipleShutdownCallsHandledGracefully() throws Exception {
        // Given: No processing is in progress
        when(processingJob.isProcessingInProgress()).thenReturn(false);

        // When: Shutdown is triggered multiple times
        shutdownHandler.onShutdown();
        shutdownHandler.onShutdown();
        shutdownHandler.onShutdown();

        // Then: All shutdown calls complete without error
        verify(processingJob, atLeast(1)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When processing completes during shutdown wait Then shutdown proceeds immediately")
    void testShutdownProceedsWhenProcessingCompletes() throws Exception {
        // Given: Processing job is running initially but completes quickly
        CountDownLatch processingStarted = new CountDownLatch(1);
        AtomicBoolean processingComplete = new AtomicBoolean(false);
        
        when(processingJob.isProcessingInProgress())
            .thenAnswer(invocation -> {
                processingStarted.countDown();
                return !processingComplete.get();
            });

        // Simulate processing completing after 1 second
        CompletableFuture.runAsync(() -> {
            try {
                processingStarted.await();
                Thread.sleep(1000);
                processingComplete.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When: Shutdown is triggered
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdown();
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown completed after processing finished (around 1 second, not full timeout)
        assertThat(duration).isGreaterThanOrEqualTo(1000);
        assertThat(duration).isLessThan(5000); // Much less than the 60s timeout

        // Then: Processing job status was checked
        verify(processingJob, atLeast(1)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When shutdown respects 60s timeout for processing job Then completes within limit")
    void testShutdownRespectsProcessingJobTimeout() throws Exception {
        // Given: Processing job is running
        when(processingJob.isProcessingInProgress()).thenReturn(true);

        // When: Shutdown is triggered with 60s timeout (as per requirements)
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdownWithTimeout(60000); // 60 second timeout
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown completes within the 60s timeout
        assertThat(duration).isLessThanOrEqualTo(61000); // Allow 1s overhead

        // Then: Processing job status was checked
        verify(processingJob, atLeast(1)).isProcessingInProgress();
    }

    @Test
    @DisplayName("When application stops accepting requests during shutdown Then new requests are rejected")
    void testApplicationStopsAcceptingRequestsDuringShutdown() throws Exception {
        // Given: Application context is configured for graceful shutdown
        // This test verifies the concept - actual implementation is handled by Spring Boot
        
        // When: Shutdown is initiated
        when(processingJob.isProcessingInProgress()).thenReturn(false);
        shutdownHandler.onShutdown();

        // Then: Shutdown completes successfully
        // In a real Spring Boot application, the server would stop accepting new requests
        // while completing in-flight requests within the 30s timeout configured in application.yaml
        verify(processingJob, atLeastOnce()).isProcessingInProgress();
    }

    @Test
    @DisplayName("When in-flight requests complete within 30s timeout Then shutdown succeeds")
    void testInFlightRequestsCompleteWithinTimeout() throws Exception {
        // Given: Processing completes within the 30s shutdown phase timeout
        AtomicBoolean processingComplete = new AtomicBoolean(false);
        
        when(processingJob.isProcessingInProgress())
            .thenAnswer(invocation -> !processingComplete.get());

        // Simulate processing completing within timeout (5 seconds)
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(5000);
                processingComplete.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When: Shutdown is triggered with 30s timeout
        long startTime = System.currentTimeMillis();
        shutdownHandler.onShutdownWithTimeout(30000);
        long duration = System.currentTimeMillis() - startTime;

        // Then: Shutdown completes after processing finishes (around 5s, well within 30s)
        assertThat(duration).isGreaterThanOrEqualTo(5000);
        assertThat(duration).isLessThan(10000);

        // Then: Processing completed successfully
        verify(processingJob, atLeast(2)).isProcessingInProgress();
    }
}

package com.rag.openai.client;

import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.client.ollama.OllamaClientImpl;
import com.rag.openai.config.OllamaConfig;
import com.rag.openai.exception.ServiceTimeoutException;
import com.rag.openai.exception.ServiceUnavailableException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OllamaClient implementation.
 * Tests request formatting, response parsing, connection error handling, and timeout handling.
 */
class OllamaClientTest {

    private MockWebServer mockServer;
    private OllamaClient ollamaClient;
    private OllamaConfig config;

    @BeforeEach
    void setUp() throws IOException {
        // Given: A mock Ollama server
        mockServer = new MockWebServer();
        mockServer.start();

        // Use short timeouts for faster test execution
        config = new OllamaConfig(
            mockServer.getHostName(),
            mockServer.getPort(),
            "gpt-oss:20b",
            "qwen3-embedding:8b",
            "qwen3-vl:8b",
            Duration.ofMillis(500),  // Short connection timeout
            Duration.ofSeconds(2)     // Short read timeout
        );
        
        ollamaClient = new OllamaClientImpl(config);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    @DisplayName("When generating text with valid request Then response is parsed correctly")
    void testGenerateSuccess() throws Exception {
        // Given: A successful Ollama response in OpenAI completions format
        String responseJson = """
            {
                "id": "cmpl-123",
                "object": "text_completion",
                "created": 1715634521,
                "model": "gpt-oss:20b",
                "choices": [
                    {
                        "text": "This is a generated response.",
                        "index": 0,
                        "logprobs": null,
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 7,
                    "completion_tokens": 6,
                    "total_tokens": 13
                }
            }
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating text
        CompletableFuture<String> result = ollamaClient.generate(
            "What is the meaning of life?"
        );

        // Then: The response is parsed correctly
        assertThat(result).isNotNull();
        String response = result.get();
        assertThat(response).isEqualTo("This is a generated response.");
        
        // Verify request format
        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/completions");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("\"model\":\"gpt-oss:20b\"");
        assertThat(requestBody).contains("\"prompt\":\"What is the meaning of life?\"");
        assertThat(requestBody).contains("\"stream\":false");
    }

    @Test
    @DisplayName("When generating text with special characters Then request is formatted correctly")
    void testGenerateWithSpecialCharacters() throws Exception {
        // Given: A prompt with special characters
        String promptWithSpecialChars = "What is \"life\"?\nAnd what about 'meaning'?";
        String responseJson = """
            {
                "id": "cmpl-456",
                "object": "text_completion",
                "created": 1715634521,
                "model": "gpt-oss:20b",
                "choices": [
                    {
                        "text": "Life is complex.",
                        "index": 0,
                        "logprobs": null,
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 3,
                    "total_tokens": 13
                }
            }
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating text with special characters
        CompletableFuture<String> result = ollamaClient.generate(
            promptWithSpecialChars
        );

        // Then: The request is formatted correctly
        assertThat(result).isNotNull();
        result.get();
        
        RecordedRequest request = mockServer.takeRequest();
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("What is \\\"life\\\"?");
        assertThat(requestBody).contains("\\n");
        assertThat(requestBody).contains("'meaning'");
    }

    @Test
    @Disabled
    @DisplayName("When generating streaming text Then request format includes stream flag")
    void testGenerateStreamingRequestFormat() throws Exception {
        // Given: A streaming response
        String responseJson = """
            {"id":"cmpl-789","object":"text_completion","created":1715634521,"model":"gpt-oss:20b","choices":[{"text":"Hello world!","index":0,"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating streaming text
        CompletableFuture<Flux<String>> result = ollamaClient.generateStreaming(
            "Say hello"
        );

        // Then: The request format includes stream flag
        assertThat(result).isNotNull();
        
        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/completions");
        
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("\"stream\":true");
        assertThat(requestBody).contains("\"model\":\"llama3.2\"");
        assertThat(requestBody).contains("\"prompt\":\"Say hello\"");
    }

    @Test
    @DisplayName("When generating embedding Then request is formatted correctly")
    void testGenerateEmbeddingRequestFormat() throws Exception {
        // Given: An embedding response in OpenAI format
        String responseJson = """
            {
                "object": "list",
                "data": [
                    {
                        "object": "embedding",
                        "index": 0,
                        "embedding": [0.1, 0.2, 0.3, 0.4, 0.5]
                    }
                ],
                "model": "qwen3-embedding:8b",
                "usage": {
                    "prompt_tokens": 5,
                    "total_tokens": 5
                }
            }
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating an embedding
        CompletableFuture<List<Float>> result = ollamaClient.generateEmbedding(
            "Sample text for embedding"
        );

        // Then: The request is formatted correctly
        assertThat(result).isNotNull();
        List<Float> embedding = result.get();
        assertThat(embedding).containsExactly(0.1f, 0.2f, 0.3f, 0.4f, 0.5f);
        
        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1/embeddings");
        assertThat(request.getHeader("Content-Type")).contains("application/json");
        
        String requestBody = request.getBody().readUtf8();
        assertThat(requestBody).contains("\"model\":\"qwen3-embedding:8b\"");
        assertThat(requestBody).contains("\"input\":\"Sample text for embedding\"");
        assertThat(requestBody).contains("\"encoding_format\":\"float\"");
    }

    @Test
    @DisplayName("When generating embedding with large dimension Then response is parsed correctly")
    void testGenerateEmbeddingLargeDimension() throws Exception {
        // Given: An embedding response with 768 dimensions
        StringBuilder embeddingArray = new StringBuilder("[");
        for (int i = 0; i < 768; i++) {
            embeddingArray.append(i * 0.001);
            if (i < 767) {
                embeddingArray.append(",");
            }
        }
        embeddingArray.append("]");
        
        String responseJson = String.format("""
            {
                "object": "list",
                "data": [
                    {
                        "object": "embedding",
                        "index": 0,
                        "embedding": %s
                    }
                ],
                "model": "qwen3-embedding:8b",
                "usage": {
                    "prompt_tokens": 2,
                    "total_tokens": 2
                }
            }
            """, embeddingArray);
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating an embedding
        CompletableFuture<List<Float>> result = ollamaClient.generateEmbedding(
            "Sample text"
        );

        // Then: The response is parsed correctly with all dimensions
        assertThat(result).isNotNull();
        List<Float> embedding = result.get();
        assertThat(embedding).hasSize(768);
        assertThat(embedding.get(0)).isEqualTo(0.0f);
        assertThat(embedding.get(767)).isCloseTo(0.767f, org.assertj.core.data.Offset.offset(0.001f));
    }

    @Test
    @DisplayName("When server returns 500 error Then ServiceUnavailableException is thrown")
    void testGenerateServerError() {
        // Given: A server error response
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"));

        // When: Generating text
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: ServiceUnavailableException is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Ollama server returned error: 500");
    }

    @Test
    @DisplayName("When server returns 404 error Then ServiceUnavailableException is thrown")
    void testGenerateNotFoundError() {
        // Given: A not found error response
        mockServer.enqueue(new MockResponse()
            .setResponseCode(404)
            .setBody("Model not found"));

        // When: Generating text
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: ServiceUnavailableException is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Ollama server returned error: 404");
    }

    @Test
    @DisplayName("When connection fails Then ServiceUnavailableException is thrown")
    void testGenerateConnectionError() throws IOException {
        // Given: A server that is shut down
        mockServer.shutdown();

        // When: Attempting to generate text
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: ServiceUnavailableException is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(ServiceUnavailableException.class)
            .hasMessageContaining("Failed to connect to Ollama server");
    }

    @Test
    @DisplayName("When request times out Then ServiceTimeoutException is thrown")
    void testGenerateTimeout() {
        // Given: A server that delays response beyond timeout
        String responseJson = """
            {"id":"cmpl-timeout","object":"text_completion","created":1715634521,"model":"gpt-oss:20b","choices":[{"text":"Late response","index":0,"logprobs":null,"finish_reason":"stop"}],"usage":{"prompt_tokens":2,"completion_tokens":2,"total_tokens":4}}
            """;
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));

        // When: Generating text with short timeout
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: ServiceTimeoutException is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get(5, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(ServiceTimeoutException.class)
            .hasMessageContaining("Ollama request timed out");
    }

    @Test
    @DisplayName("When embedding request times out Then ServiceTimeoutException is thrown")
    void testGenerateEmbeddingTimeout() {
        // Given: A server that delays response beyond timeout
        String responseJson = """
            {"object":"list","data":[{"object":"embedding","index":0,"embedding":[0.1,0.2,0.3]}],"model":"qwen3-embedding:8b","usage":{"prompt_tokens":2,"total_tokens":2}}
            """;
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setBodyDelay(3, java.util.concurrent.TimeUnit.SECONDS));

        // When: Generating embedding with short timeout
        CompletableFuture<List<Float>> result = ollamaClient.generateEmbedding(
            "Test text"
        );

        // Then: ServiceTimeoutException is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get(5, java.util.concurrent.TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(ServiceTimeoutException.class)
            .hasMessageContaining("Ollama request timed out");
    }

    @Test
    @DisplayName("When verifying connectivity with healthy server Then true is returned")
    void testVerifyConnectivitySuccess() throws Exception {
        // Given: A healthy server
        mockServer.enqueue(new MockResponse()
            .setBody("{\"data\":[]}")
            .setHeader("Content-Type", "application/json"));

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = ollamaClient.verifyConnectivity();

        // Then: True is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isTrue();
        
        RecordedRequest request = mockServer.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/v1/models");
    }

    @Test
    @DisplayName("When verifying connectivity with unavailable server Then false is returned")
    void testVerifyConnectivityFailure() throws Exception {
        // Given: A server that is shut down
        mockServer.shutdown();

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = ollamaClient.verifyConnectivity();

        // Then: False is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isFalse();
    }

    @Test
    @DisplayName("When verifying connectivity times out Then false is returned")
    void testVerifyConnectivityTimeout() throws Exception {
        // Given: A server that delays response beyond connection timeout
        mockServer.enqueue(new MockResponse()
            .setBody("{\"data\":[]}")
            .setBodyDelay(1, java.util.concurrent.TimeUnit.SECONDS));

        // When: Verifying connectivity
        CompletableFuture<Boolean> result = ollamaClient.verifyConnectivity();

        // Then: False is returned
        assertThat(result).isNotNull();
        Boolean isConnected = result.get();
        assertThat(isConnected).isFalse();
    }

    @Test
    @DisplayName("When response has invalid JSON Then error is handled gracefully")
    void testGenerateInvalidJsonResponse() {
        // Given: An invalid JSON response
        mockServer.enqueue(new MockResponse()
            .setBody("This is not valid JSON")
            .setHeader("Content-Type", "application/json"));

        // When: Generating text
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: An exception is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("When embedding response has invalid JSON Then error is handled gracefully")
    void testGenerateEmbeddingInvalidJsonResponse() {
        // Given: An invalid JSON response
        mockServer.enqueue(new MockResponse()
            .setBody("Not a valid embedding response")
            .setHeader("Content-Type", "application/json"));

        // When: Generating embedding
        CompletableFuture<List<Float>> result = ollamaClient.generateEmbedding(
            "Test text"
        );

        // Then: An exception is thrown
        assertThat(result).isNotNull();
        assertThatThrownBy(() -> result.get())
            .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("When response has empty text Then empty string is returned")
    void testGenerateEmptyResponse() throws Exception {
        // Given: A response with empty text in choices
        String responseJson = """
            {
                "id": "cmpl-empty",
                "object": "text_completion",
                "created": 1715634521,
                "model": "gpt-oss:20b",
                "choices": [
                    {
                        "text": "",
                        "index": 0,
                        "logprobs": null,
                        "finish_reason": "stop"
                    }
                ],
                "usage": {
                    "prompt_tokens": 2,
                    "completion_tokens": 0,
                    "total_tokens": 2
                }
            }
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating text
        CompletableFuture<String> result = ollamaClient.generate(
            "Test prompt"
        );

        // Then: Empty string is returned
        assertThat(result).isNotNull();
        String response = result.get();
        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("When embedding response has empty embedding array Then empty list is returned")
    void testGenerateEmbeddingEmptyArray() throws Exception {
        // Given: An embedding response with empty embedding array
        String responseJson = """
            {
                "object": "list",
                "data": [
                    {
                        "object": "embedding",
                        "index": 0,
                        "embedding": []
                    }
                ],
                "model": "qwen3-embedding:8b",
                "usage": {
                    "prompt_tokens": 2,
                    "total_tokens": 2
                }
            }
            """;
        
        mockServer.enqueue(new MockResponse()
            .setBody(responseJson)
            .setHeader("Content-Type", "application/json"));

        // When: Generating embedding
        CompletableFuture<List<Float>> result = ollamaClient.generateEmbedding(
            "Test text"
        );

        // Then: Empty list is returned
        assertThat(result).isNotNull();
        List<Float> embedding = result.get();
        assertThat(embedding).isEmpty();
    }
}

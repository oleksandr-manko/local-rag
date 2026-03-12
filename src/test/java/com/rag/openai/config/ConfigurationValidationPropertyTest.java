package com.rag.openai.config;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for configuration validation.
 * **Validates: Requirements 3.8, 15.5**
 * 
 * Property 6: Configuration Validation
 */
class ConfigurationValidationPropertyTest {

    // ==================== OllamaConfig Tests ====================

    @Property(tries = 100)
    @Label("When OllamaConfig has null host Then throws NullPointerException")
    void ollamaConfigRejectsNullHost(
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String modelName,
            @ForAll @NotBlank @AlphaChars String embeddingModelName,
            @ForAll @NotBlank @AlphaChars String visionModelName,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll("durations") Duration readTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null host
        String host = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new OllamaConfig(
                host, port, modelName, embeddingModelName, visionModelName, connectionTimeout, readTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Ollama host must not be null");
    }

    @Property(tries = 100)
    @Label("When OllamaConfig has null model name Then throws NullPointerException")
    void ollamaConfigRejectsNullModelName(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String embeddingModelName,
            @ForAll @NotBlank @AlphaChars String visionModelName,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll("durations") Duration readTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null model name
        String modelName = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new OllamaConfig(
                host, port, modelName, embeddingModelName, visionModelName, connectionTimeout, readTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Ollama model name must not be null");
    }

    @Property(tries = 100)
    @Label("When OllamaConfig has null embedding model name Then throws NullPointerException")
    void ollamaConfigRejectsNullEmbeddingModelName(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String modelName,
            @ForAll @NotBlank @AlphaChars String visionModelName,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll("durations") Duration readTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null embedding model name
        String embeddingModelName = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new OllamaConfig(
                host, port, modelName, embeddingModelName, visionModelName, connectionTimeout, readTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Ollama embedding model name must not be null");
    }

    @Property(tries = 100)
    @Label("When OllamaConfig has invalid port Then throws IllegalArgumentException")
    void ollamaConfigRejectsInvalidPort(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll("invalidPorts") int port,
            @ForAll @NotBlank @AlphaChars String modelName,
            @ForAll @NotBlank @AlphaChars String embeddingModelName,
            @ForAll @NotBlank @AlphaChars String visionModelName,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll("durations") Duration readTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: invalid port (from generator)
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new OllamaConfig(
                host, port, modelName, embeddingModelName, visionModelName, connectionTimeout, readTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid port");
    }

    @Property(tries = 100)
    @Label("When OllamaConfig has valid parameters Then creates successfully")
    void ollamaConfigAcceptsValidParameters(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String modelName,
            @ForAll @NotBlank @AlphaChars String embeddingModelName,
            @ForAll @NotBlank @AlphaChars String visionModelName,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll("durations") Duration readTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters
        
        // When: creating config
        var config = new OllamaConfig(
                host, port, modelName, embeddingModelName, visionModelName, connectionTimeout, readTimeout
        );
        
        // Then: config is created successfully with correct values
        assertThat(config.host()).isEqualTo(host);
        assertThat(config.port()).isEqualTo(port);
        assertThat(config.modelName()).isEqualTo(modelName);
        assertThat(config.embeddingModelName()).isEqualTo(embeddingModelName);
        assertThat(config.visionModelName()).isEqualTo(visionModelName);
        assertThat(config.connectionTimeout()).isEqualTo(connectionTimeout);
        assertThat(config.readTimeout()).isEqualTo(readTimeout);
    }

    // ==================== QdrantConfig Tests ====================

    @Property(tries = 100)
    @Label("When QdrantConfig has null host Then throws NullPointerException")
    void qdrantConfigRejectsNullHost(
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String collectionName,
            @ForAll @IntRange(min = 1, max = 4096) int vectorDimension,
            @ForAll("durations") Duration connectionTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null host
        String host = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new QdrantConfig(
                host, port, collectionName, vectorDimension, connectionTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Qdrant host must not be null");
    }

    @Property(tries = 100)
    @Label("When QdrantConfig has null collection name Then throws NullPointerException")
    void qdrantConfigRejectsNullCollectionName(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @IntRange(min = 1, max = 4096) int vectorDimension,
            @ForAll("durations") Duration connectionTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null collection name
        String collectionName = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new QdrantConfig(
                host, port, collectionName, vectorDimension, connectionTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Collection name must not be null");
    }

    @Property(tries = 100)
    @Label("When QdrantConfig has invalid port Then throws IllegalArgumentException")
    void qdrantConfigRejectsInvalidPort(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll("invalidPorts") int port,
            @ForAll @NotBlank @AlphaChars String collectionName,
            @ForAll @IntRange(min = 1, max = 4096) int vectorDimension,
            @ForAll("durations") Duration connectionTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: invalid port
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new QdrantConfig(
                host, port, collectionName, vectorDimension, connectionTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid port");
    }

    @Property(tries = 100)
    @Label("When QdrantConfig has invalid vector dimension Then throws IllegalArgumentException")
    void qdrantConfigRejectsInvalidVectorDimension(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String collectionName,
            @ForAll @IntRange(min = -1000, max = 0) int vectorDimension,
            @ForAll("durations") Duration connectionTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: invalid vector dimension (non-positive)
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new QdrantConfig(
                host, port, collectionName, vectorDimension, connectionTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vector dimension must be positive");
    }

    @Property(tries = 100)
    @Label("When QdrantConfig has valid parameters Then creates successfully")
    void qdrantConfigAcceptsValidParameters(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll @NotBlank @AlphaChars String collectionName,
            @ForAll @IntRange(min = 1, max = 4096) int vectorDimension,
            @ForAll("durations") Duration connectionTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters
        
        // When: creating config
        var config = new QdrantConfig(
                host, port, collectionName, vectorDimension, connectionTimeout
        );
        
        // Then: config is created successfully with correct values
        assertThat(config.host()).isEqualTo(host);
        assertThat(config.port()).isEqualTo(port);
        assertThat(config.collectionName()).isEqualTo(collectionName);
        assertThat(config.vectorDimension()).isEqualTo(vectorDimension);
        assertThat(config.connectionTimeout()).isEqualTo(connectionTimeout);
    }

    // ==================== RedisConfig Tests ====================

    @Property(tries = 100)
    @Label("When RedisConfig has null host Then throws NullPointerException")
    void redisConfigRejectsNullHost(
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll @IntRange(min = 0, max = 15) int database
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null host
        String host = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new RedisConfig(
                host, port, connectionTimeout, database
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Redis host must not be null");
    }

    @Property(tries = 100)
    @Label("When RedisConfig has invalid port Then throws IllegalArgumentException")
    void redisConfigRejectsInvalidPort(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll("invalidPorts") int port,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll @IntRange(min = 0, max = 15) int database
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: invalid port
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new RedisConfig(
                host, port, connectionTimeout, database
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid port");
    }

    @Property(tries = 100)
    @Label("When RedisConfig has negative database index Then throws IllegalArgumentException")
    void redisConfigRejectsNegativeDatabaseIndex(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll @IntRange(min = -100, max = -1) int database
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: negative database index
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new RedisConfig(
                host, port, connectionTimeout, database
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Database index must be non-negative");
    }

    @Property(tries = 100)
    @Label("When RedisConfig has valid parameters Then creates successfully")
    void redisConfigAcceptsValidParameters(
            @ForAll @NotBlank @AlphaChars String host,
            @ForAll @IntRange(min = 1, max = 65535) int port,
            @ForAll("durations") Duration connectionTimeout,
            @ForAll @IntRange(min = 0, max = 15) int database
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters
        
        // When: creating config
        var config = new RedisConfig(host, port, connectionTimeout, database);
        
        // Then: config is created successfully with correct values
        assertThat(config.host()).isEqualTo(host);
        assertThat(config.port()).isEqualTo(port);
        assertThat(config.connectionTimeout()).isEqualTo(connectionTimeout);
        assertThat(config.database()).isEqualTo(database);
    }

    // ==================== DocumentsConfig Tests ====================

    @Property(tries = 100)
    @Label("When DocumentsConfig has null input folder Then throws NullPointerException")
    void documentsConfigRejectsNullInputFolder(
            @ForAll("fileExtensions") List<String> supportedExtensions
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null input folder
        Path inputFolder = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new DocumentsConfig(
                inputFolder, supportedExtensions
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Input folder must not be null");
    }

    @Property(tries = 100)
    @Label("When DocumentsConfig has null supported extensions Then throws NullPointerException")
    void documentsConfigRejectsNullSupportedExtensions(
            @ForAll("paths") Path inputFolder
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null supported extensions
        List<String> supportedExtensions = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new DocumentsConfig(
                inputFolder, supportedExtensions
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Supported extensions must not be null");
    }

    @Property(tries = 100)
    @Label("When DocumentsConfig has valid parameters Then creates successfully")
    void documentsConfigAcceptsValidParameters(
            @ForAll("paths") Path inputFolder,
            @ForAll("fileExtensions") List<String> supportedExtensions
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters
        
        // When: creating config
        var config = new DocumentsConfig(inputFolder, supportedExtensions);
        
        // Then: config is created successfully with correct values
        assertThat(config.inputFolder()).isEqualTo(inputFolder);
        assertThat(config.supportedExtensions()).isEqualTo(supportedExtensions);
    }

    // ==================== ProcessingConfig Tests ====================

    @Property(tries = 100)
    @Label("When ProcessingConfig has null schedule Then throws NullPointerException")
    void processingConfigRejectsNullSchedule(
            @ForAll @IntRange(min = 1, max = 10000) int chunkSize,
            @ForAll @IntRange(min = 0, max = 500) int chunkOverlap,
            @ForAll @IntRange(min = 1, max = 1000) int batchSize,
            @ForAll @IntRange(min = 1, max = 100) int maxConcurrentFiles,
            @ForAll("durations") Duration jobTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null schedule
        String schedule = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new ProcessingConfig(
                schedule, chunkSize, chunkOverlap, batchSize, maxConcurrentFiles, jobTimeout
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Schedule must not be null");
    }

    @Property(tries = 100)
    @Label("When ProcessingConfig has non-positive chunk size Then throws IllegalArgumentException")
    void processingConfigRejectsNonPositiveChunkSize(
            @ForAll @NotBlank @AlphaChars String schedule,
            @ForAll @IntRange(min = -1000, max = 0) int chunkSize,
            @ForAll @IntRange(min = 1, max = 1000) int batchSize,
            @ForAll @IntRange(min = 1, max = 100) int maxConcurrentFiles,
            @ForAll("durations") Duration jobTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: non-positive chunk size
        int chunkOverlap = 0; // Valid overlap for this test
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new ProcessingConfig(
                schedule, chunkSize, chunkOverlap, batchSize, maxConcurrentFiles, jobTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Chunk size must be positive");
    }

    @Property(tries = 100)
    @Label("When ProcessingConfig has invalid chunk overlap Then throws IllegalArgumentException")
    void processingConfigRejectsInvalidChunkOverlap(
            @ForAll @NotBlank @AlphaChars String schedule,
            @ForAll @IntRange(min = 1, max = 1000) int chunkSize,
            @ForAll @IntRange(min = 1, max = 1000) int batchSize,
            @ForAll @IntRange(min = 1, max = 100) int maxConcurrentFiles,
            @ForAll("durations") Duration jobTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: chunk overlap >= chunk size (invalid)
        int chunkOverlap = chunkSize + 1;
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new ProcessingConfig(
                schedule, chunkSize, chunkOverlap, batchSize, maxConcurrentFiles, jobTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid chunk overlap");
    }

    @Property(tries = 100)
    @Label("When ProcessingConfig has negative chunk overlap Then throws IllegalArgumentException")
    void processingConfigRejectsNegativeChunkOverlap(
            @ForAll @NotBlank @AlphaChars String schedule,
            @ForAll @IntRange(min = 1, max = 1000) int chunkSize,
            @ForAll @IntRange(min = -100, max = -1) int chunkOverlap,
            @ForAll @IntRange(min = 1, max = 1000) int batchSize,
            @ForAll @IntRange(min = 1, max = 100) int maxConcurrentFiles,
            @ForAll("durations") Duration jobTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: negative chunk overlap
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new ProcessingConfig(
                schedule, chunkSize, chunkOverlap, batchSize, maxConcurrentFiles, jobTimeout
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid chunk overlap");
    }

    @Property(tries = 100)
    @Label("When ProcessingConfig has valid parameters Then creates successfully")
    void processingConfigAcceptsValidParameters(
            @ForAll @NotBlank @AlphaChars String schedule,
            @ForAll @IntRange(min = 100, max = 10000) int chunkSize,
            @ForAll @IntRange(min = 1, max = 1000) int batchSize,
            @ForAll @IntRange(min = 1, max = 100) int maxConcurrentFiles,
            @ForAll("durations") Duration jobTimeout
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters with overlap < chunkSize
        int chunkOverlap = chunkSize / 2; // Valid overlap
        
        // When: creating config
        var config = new ProcessingConfig(
                schedule, chunkSize, chunkOverlap, batchSize, maxConcurrentFiles, jobTimeout
        );
        
        // Then: config is created successfully with correct values
        assertThat(config.schedule()).isEqualTo(schedule);
        assertThat(config.chunkSize()).isEqualTo(chunkSize);
        assertThat(config.chunkOverlap()).isEqualTo(chunkOverlap);
        assertThat(config.batchSize()).isEqualTo(batchSize);
        assertThat(config.maxConcurrentFiles()).isEqualTo(maxConcurrentFiles);
        assertThat(config.jobTimeout()).isEqualTo(jobTimeout);
    }

    // ==================== RagConfig Tests ====================

    @Property(tries = 100)
    @Label("When RagConfig has non-positive top K results Then throws IllegalArgumentException")
    void ragConfigRejectsNonPositiveTopK(
            @ForAll @IntRange(min = -100, max = 0) int topKResults,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double similarityThreshold,
            @ForAll @NotBlank @AlphaChars String contextSeparator,
            @ForAll @NotBlank @AlphaChars String promptTemplate
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: non-positive top K results
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new RagConfig(
                topKResults, similarityThreshold, contextSeparator, promptTemplate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Top K results must be positive");
    }

    @Property(tries = 100)
    @Label("When RagConfig has similarity threshold out of range Then throws IllegalArgumentException")
    void ragConfigRejectsInvalidSimilarityThreshold(
            @ForAll @IntRange(min = 1, max = 100) int topKResults,
            @ForAll("invalidSimilarityThresholds") double similarityThreshold,
            @ForAll @NotBlank @AlphaChars String contextSeparator,
            @ForAll @NotBlank @AlphaChars String promptTemplate
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: similarity threshold outside [0.0, 1.0]
        
        // When & Then: creating config should throw IllegalArgumentException
        assertThatThrownBy(() -> new RagConfig(
                topKResults, similarityThreshold, contextSeparator, promptTemplate
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Similarity threshold must be between 0 and 1");
    }

    @Property(tries = 100)
    @Label("When RagConfig has null context separator Then throws NullPointerException")
    void ragConfigRejectsNullContextSeparator(
            @ForAll @IntRange(min = 1, max = 100) int topKResults,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double similarityThreshold,
            @ForAll @NotBlank @AlphaChars String promptTemplate
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null context separator
        String contextSeparator = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new RagConfig(
                topKResults, similarityThreshold, contextSeparator, promptTemplate
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Context separator must not be null");
    }

    @Property(tries = 100)
    @Label("When RagConfig has null prompt template Then throws NullPointerException")
    void ragConfigRejectsNullPromptTemplate(
            @ForAll @IntRange(min = 1, max = 100) int topKResults,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double similarityThreshold,
            @ForAll @NotBlank @AlphaChars String contextSeparator
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: null prompt template
        String promptTemplate = null;
        
        // When & Then: creating config should throw NullPointerException
        assertThatThrownBy(() -> new RagConfig(
                topKResults, similarityThreshold, contextSeparator, promptTemplate
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Prompt template must not be null");
    }

    @Property(tries = 100)
    @Label("When RagConfig has valid parameters Then creates successfully")
    void ragConfigAcceptsValidParameters(
            @ForAll @IntRange(min = 1, max = 100) int topKResults,
            @ForAll @DoubleRange(min = 0.0, max = 1.0) double similarityThreshold,
            @ForAll @NotBlank @AlphaChars String contextSeparator,
            @ForAll @NotBlank @AlphaChars String promptTemplate
    ) {
        // Feature: rag-openai-api-ollama, Property 6: Configuration Validation
        
        // Given: valid parameters
        
        // When: creating config
        var config = new RagConfig(
                topKResults, similarityThreshold, contextSeparator, promptTemplate
        );
        
        // Then: config is created successfully with correct values
        assertThat(config.topKResults()).isEqualTo(topKResults);
        assertThat(config.similarityThreshold()).isEqualTo(similarityThreshold);
        assertThat(config.contextSeparator()).isEqualTo(contextSeparator);
        assertThat(config.promptTemplate()).isEqualTo(promptTemplate);
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<Duration> durations() {
        return Arbitraries.integers()
                .between(1, 300)
                .map(Duration::ofSeconds);
    }

    @Provide
    Arbitrary<Integer> invalidPorts() {
        return Arbitraries.oneOf(
                Arbitraries.integers().between(-10000, 0),
                Arbitraries.integers().between(65536, 100000)
        );
    }

    @Provide
    Arbitrary<Double> invalidSimilarityThresholds() {
        return Arbitraries.oneOf(
                Arbitraries.doubles().between(-10.0, -0.01),
                Arbitraries.doubles().between(1.01, 10.0)
        );
    }

    @Provide
    Arbitrary<Path> paths() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> Path.of("/tmp/" + s));
    }

    @Provide
    Arbitrary<List<String>> fileExtensions() {
        return Arbitraries.of("pdf", "jpg", "jpeg", "png", "tiff", "txt", "doc")
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }
}

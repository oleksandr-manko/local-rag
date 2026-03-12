package com.rag.openai.api;

import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.Message;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for OpenAI API request validation.
 * **Validates: Requirements 1.2, 1.6**
 * 
 * Property 1: Request Validation
 * 
 * This test suite verifies that the ChatCompletionRequest validates input according to OpenAI API specifications:
 * - Model must not be null or blank
 * - Messages list must not be null or empty
 * - Temperature must be between 0 and 2 (if provided)
 * - Max tokens must be positive (if provided)
 * - Message roles and content must not be null or blank
 */
class RequestValidationPropertyTest {

    // ==================== ChatCompletionRequest Validation Tests ====================

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has null model Then throws NullPointerException")
    void requestRejectsNullModel(
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: null model
        String model = null;
        
        // When & Then: creating request should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, temperature, maxTokens
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Model must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has blank model Then throws IllegalArgumentException")
    void requestRejectsBlankModel(
            @ForAll("blankStrings") String model,
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: blank model (from generator)
        
        // When & Then: creating request should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, temperature, maxTokens
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Model must not be blank");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has null messages Then throws NullPointerException")
    void requestRejectsNullMessages(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: null messages
        List<Message> messages = null;
        
        // When & Then: creating request should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, temperature, maxTokens
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Messages must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has empty messages Then throws IllegalArgumentException")
    void requestRejectsEmptyMessages(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: empty messages list
        List<Message> messages = Collections.emptyList();
        
        // When & Then: creating request should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, temperature, maxTokens
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Messages list must not be empty");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has temperature below 0 Then throws IllegalArgumentException")
    void requestRejectsTemperatureBelowZero(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll @DoubleRange(min = -10.0, max = -0.01) double temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: temperature below 0
        
        // When & Then: creating request should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, Optional.of(temperature), maxTokens
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature must be between 0 and 2");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has temperature above 2 Then throws IllegalArgumentException")
    void requestRejectsTemperatureAboveTwo(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll @DoubleRange(min = 2.01, max = 10.0) double temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: temperature above 2
        
        // When & Then: creating request should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, Optional.of(temperature), maxTokens
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Temperature must be between 0 and 2");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has non-positive max tokens Then throws IllegalArgumentException")
    void requestRejectsNonPositiveMaxTokens(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll @IntRange(min = -1000, max = 0) int maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: non-positive max tokens
        
        // When & Then: creating request should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionRequest(
                model, messages, stream, temperature, Optional.of(maxTokens)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Max tokens must be positive");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionRequest has valid parameters Then creates successfully")
    void requestAcceptsValidParameters(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("messages") List<Message> messages,
            @ForAll boolean stream,
            @ForAll("optionalTemperature") Optional<Double> temperature,
            @ForAll("optionalMaxTokens") Optional<Integer> maxTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: valid parameters
        
        // When: creating request
        var request = new ChatCompletionRequest(
                model, messages, stream, temperature, maxTokens
        );
        
        // Then: request is created successfully with correct values
        assertThat(request.model()).isEqualTo(model);
        assertThat(request.messages()).isEqualTo(messages);
        assertThat(request.stream()).isEqualTo(stream);
        assertThat(request.temperature()).isEqualTo(temperature);
        assertThat(request.maxTokens()).isEqualTo(maxTokens);
    }

    // ==================== Message Validation Tests ====================

    @Property(tries = 100)
    @Label("When Message has null role Then throws NullPointerException")
    void messageRejectsNullRole(
            @ForAll @NotBlank @AlphaChars String content
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: null role
        String role = null;
        
        // When & Then: creating message should throw NullPointerException
        assertThatThrownBy(() -> new Message(role, content))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Message role must not be null");
    }

    @Property(tries = 100)
    @Label("When Message has blank role Then throws IllegalArgumentException")
    void messageRejectsBlankRole(
            @ForAll("blankStrings") String role,
            @ForAll @NotBlank @AlphaChars String content
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: blank role (from generator)
        
        // When & Then: creating message should throw IllegalArgumentException
        assertThatThrownBy(() -> new Message(role, content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Message role must not be blank");
    }

    @Property(tries = 100)
    @Label("When Message has null content Then throws NullPointerException")
    void messageRejectsNullContent(
            @ForAll("roles") String role
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: null content
        String content = null;
        
        // When & Then: creating message should throw NullPointerException
        assertThatThrownBy(() -> new Message(role, content))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Message content must not be null");
    }

    @Property(tries = 100)
    @Label("When Message has valid parameters Then creates successfully")
    void messageAcceptsValidParameters(
            @ForAll("roles") String role,
            @ForAll @NotBlank @AlphaChars String content
    ) {
        // Feature: rag-openai-api-ollama, Property 1: Request Validation
        
        // Given: valid role and content
        
        // When: creating message
        var message = new Message(role, content);
        
        // Then: message is created successfully with correct values
        assertThat(message.role()).isEqualTo(role);
        assertThat(message.content()).isEqualTo(content);
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<List<Message>> messages() {
        return Arbitraries.of("system", "user", "assistant")
                .flatMap(role -> 
                    Arbitraries.strings()
                        .alpha()
                        .ofMinLength(1)
                        .ofMaxLength(100)
                        .map(content -> new Message(role, content))
                )
                .list()
                .ofMinSize(1)
                .ofMaxSize(10);
    }

    @Provide
    Arbitrary<String> roles() {
        return Arbitraries.of("system", "user", "assistant");
    }

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", "   ", "\t", "\n", "  \t\n  ");
    }

    @Provide
    Arbitrary<Optional<Double>> optionalTemperature() {
        return Arbitraries.oneOf(
                Arbitraries.just(Optional.empty()),
                Arbitraries.doubles()
                        .between(0.0, 2.0)
                        .map(Optional::of)
        );
    }

    @Provide
    Arbitrary<Optional<Integer>> optionalMaxTokens() {
        return Arbitraries.oneOf(
                Arbitraries.just(Optional.empty()),
                Arbitraries.integers()
                        .between(1, 10000)
                        .map(Optional::of)
        );
    }
}

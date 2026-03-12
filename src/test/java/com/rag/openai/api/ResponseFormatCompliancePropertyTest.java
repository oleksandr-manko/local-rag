package com.rag.openai.api;

import com.rag.openai.domain.dto.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for OpenAI API response format compliance.
 * **Validates: Requirements 1.3, 10.7**
 * 
 * Property 2: Response Format Compliance
 * 
 * This test suite verifies that ChatCompletionResponse conforms to OpenAI API specifications:
 * - Response must have a valid ID in the format "chatcmpl-{uuid}"
 * - Object type must be "chat.completion"
 * - Created timestamp must be a valid Unix timestamp (positive long)
 * - Model name must not be null or blank
 * - Choices list must not be null or empty
 * - Each choice must have a valid index (non-negative)
 * - Each choice must have a message with role "assistant"
 * - Each choice must have a finish_reason (can be null for incomplete)
 * - Usage must not be null
 * - Usage token counts must be non-negative
 * - Total tokens must equal prompt tokens + completion tokens
 */
class ResponseFormatCompliancePropertyTest {

    // ==================== ChatCompletionResponse Format Tests ====================

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has null id Then throws NullPointerException")
    void responseRejectsNullId(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null id
        String id = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has null object Then throws NullPointerException")
    void responseRejectsNullObject(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null object
        String object = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, object, created, model, choices, usage
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Object must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has null model Then throws NullPointerException")
    void responseRejectsNullModel(
            @ForAll("completionId") String id,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null model
        String model = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Model must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has null choices Then throws NullPointerException")
    void responseRejectsNullChoices(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null choices
        List<Choice> choices = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Choices must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has empty choices Then throws IllegalArgumentException")
    void responseRejectsEmptyChoices(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: empty choices list
        List<Choice> choices = List.of();
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Choices list must not be empty");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has null usage Then throws NullPointerException")
    void responseRejectsNullUsage(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null usage
        Usage usage = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating response should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Usage must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse has valid parameters Then creates successfully")
    void responseAcceptsValidParameters(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters
        long created = System.currentTimeMillis() / 1000;
        String object = "chat.completion";
        
        // When: creating response
        var response = new ChatCompletionResponse(
                id, object, created, model, choices, usage
        );
        
        // Then: response is created successfully with correct values
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.object()).isEqualTo(object);
        assertThat(response.created()).isEqualTo(created);
        assertThat(response.model()).isEqualTo(model);
        assertThat(response.choices()).isEqualTo(choices);
        assertThat(response.usage()).isEqualTo(usage);
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse is created Then id follows OpenAI format")
    void responseIdFollowsOpenAIFormat(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters with OpenAI-formatted id
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating response
        var response = new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        );
        
        // Then: id follows OpenAI format (chatcmpl-{alphanumeric})
        assertThat(response.id()).startsWith("chatcmpl-");
        assertThat(response.id().length()).isGreaterThan(9); // "chatcmpl-" + at least 1 char
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse is created Then object type is chat.completion")
    void responseObjectTypeIsChatCompletion(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating response
        var response = new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        );
        
        // Then: object type is exactly "chat.completion"
        assertThat(response.object()).isEqualTo("chat.completion");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionResponse is created Then created timestamp is positive")
    void responseCreatedTimestampIsPositive(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("choices") List<Choice> choices,
            @ForAll("usage") Usage usage
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters with current timestamp
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating response
        var response = new ChatCompletionResponse(
                id, "chat.completion", created, model, choices, usage
        );
        
        // Then: created timestamp is positive (valid Unix timestamp)
        assertThat(response.created()).isPositive();
    }

    // ==================== Choice Format Tests ====================

    @Property(tries = 100)
    @Label("When Choice has negative index Then throws IllegalArgumentException")
    void choiceRejectsNegativeIndex(
            @ForAll("assistantMessage") Message message,
            @ForAll("finishReason") String finishReason
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: negative index
        int index = -1;
        
        // When & Then: creating choice should throw IllegalArgumentException
        assertThatThrownBy(() -> new Choice(index, message, finishReason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Index must be non-negative");
    }

    @Property(tries = 100)
    @Label("When Choice has null message Then throws NullPointerException")
    void choiceRejectsNullMessage(
            @ForAll @IntRange(min = 0, max = 10) int index,
            @ForAll("finishReason") String finishReason
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: null message
        Message message = null;
        
        // When & Then: creating choice should throw NullPointerException
        assertThatThrownBy(() -> new Choice(index, message, finishReason))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Message must not be null");
    }

    @Property(tries = 100)
    @Label("When Choice has valid parameters Then creates successfully")
    void choiceAcceptsValidParameters(
            @ForAll @IntRange(min = 0, max = 10) int index,
            @ForAll("assistantMessage") Message message,
            @ForAll("finishReason") String finishReason
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters
        
        // When: creating choice
        var choice = new Choice(index, message, finishReason);
        
        // Then: choice is created successfully with correct values
        assertThat(choice.index()).isEqualTo(index);
        assertThat(choice.message()).isEqualTo(message);
        assertThat(choice.finishReason()).isEqualTo(finishReason);
    }

    @Property(tries = 100)
    @Label("When Choice is created Then message role is assistant")
    void choiceMessageRoleIsAssistant(
            @ForAll @IntRange(min = 0, max = 10) int index,
            @ForAll("assistantMessage") Message message,
            @ForAll("finishReason") String finishReason
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid parameters with assistant message
        
        // When: creating choice
        var choice = new Choice(index, message, finishReason);
        
        // Then: message role is "assistant"
        assertThat(choice.message().role()).isEqualTo("assistant");
    }

    // ==================== Usage Format Tests ====================

    @Property(tries = 100)
    @Label("When Usage has negative prompt tokens Then throws IllegalArgumentException")
    void usageRejectsNegativePromptTokens(
            @ForAll @IntRange(min = 0, max = 10000) int completionTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: negative prompt tokens
        int promptTokens = -1;
        int totalTokens = completionTokens;
        
        // When & Then: creating usage should throw IllegalArgumentException
        assertThatThrownBy(() -> new Usage(promptTokens, completionTokens, totalTokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Prompt tokens must be non-negative");
    }

    @Property(tries = 100)
    @Label("When Usage has negative completion tokens Then throws IllegalArgumentException")
    void usageRejectsNegativeCompletionTokens(
            @ForAll @IntRange(min = 0, max = 10000) int promptTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: negative completion tokens
        int completionTokens = -1;
        int totalTokens = promptTokens;
        
        // When & Then: creating usage should throw IllegalArgumentException
        assertThatThrownBy(() -> new Usage(promptTokens, completionTokens, totalTokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Completion tokens must be non-negative");
    }

    @Property(tries = 100)
    @Label("When Usage has negative total tokens Then throws IllegalArgumentException")
    void usageRejectsNegativeTotalTokens(
            @ForAll @IntRange(min = 0, max = 10000) int promptTokens,
            @ForAll @IntRange(min = 0, max = 10000) int completionTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: negative total tokens
        int totalTokens = -1;
        
        // When & Then: creating usage should throw IllegalArgumentException
        assertThatThrownBy(() -> new Usage(promptTokens, completionTokens, totalTokens))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Total tokens must be non-negative");
    }

    @Property(tries = 100)
    @Label("When Usage has valid parameters Then creates successfully")
    void usageAcceptsValidParameters(
            @ForAll @IntRange(min = 0, max = 10000) int promptTokens,
            @ForAll @IntRange(min = 0, max = 10000) int completionTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid token counts
        int totalTokens = promptTokens + completionTokens;
        
        // When: creating usage
        var usage = new Usage(promptTokens, completionTokens, totalTokens);
        
        // Then: usage is created successfully with correct values
        assertThat(usage.promptTokens()).isEqualTo(promptTokens);
        assertThat(usage.completionTokens()).isEqualTo(completionTokens);
        assertThat(usage.totalTokens()).isEqualTo(totalTokens);
    }

    @Property(tries = 100)
    @Label("When Usage is created Then total tokens equals sum of prompt and completion tokens")
    void usageTotalTokensEqualsSum(
            @ForAll @IntRange(min = 0, max = 10000) int promptTokens,
            @ForAll @IntRange(min = 0, max = 10000) int completionTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: valid token counts
        int totalTokens = promptTokens + completionTokens;
        
        // When: creating usage
        var usage = new Usage(promptTokens, completionTokens, totalTokens);
        
        // Then: total tokens equals prompt tokens + completion tokens
        assertThat(usage.totalTokens()).isEqualTo(usage.promptTokens() + usage.completionTokens());
    }

    // ==================== Complete Response Structure Tests ====================

    @Property(tries = 100)
    @Label("When complete response is created Then all required fields are present")
    void completeResponseHasAllRequiredFields(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("assistantMessage") Message message,
            @ForAll("finishReason") String finishReason,
            @ForAll @IntRange(min = 0, max = 10000) int promptTokens,
            @ForAll @IntRange(min = 0, max = 10000) int completionTokens
    ) {
        // Feature: rag-openai-api-ollama, Property 2: Response Format Compliance
        
        // Given: all valid components
        long created = System.currentTimeMillis() / 1000;
        Choice choice = new Choice(0, message, finishReason);
        Usage usage = new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
        
        // When: creating complete response
        var response = new ChatCompletionResponse(
                id, "chat.completion", created, model, List.of(choice), usage
        );
        
        // Then: all required fields are present and valid
        assertThat(response.id()).isNotNull().startsWith("chatcmpl-");
        assertThat(response.object()).isEqualTo("chat.completion");
        assertThat(response.created()).isPositive();
        assertThat(response.model()).isNotBlank();
        assertThat(response.choices()).isNotEmpty();
        assertThat(response.choices().get(0).message().role()).isEqualTo("assistant");
        assertThat(response.usage()).isNotNull();
        assertThat(response.usage().totalTokens())
                .isEqualTo(response.usage().promptTokens() + response.usage().completionTokens());
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> completionId() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofLength(24)
                .map(suffix -> "chatcmpl-" + suffix);
    }

    @Provide
    Arbitrary<Message> assistantMessage() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '!', '?')
                .ofMinLength(1)
                .ofMaxLength(500)
                .map(content -> new Message("assistant", content));
    }

    @Provide
    Arbitrary<String> finishReason() {
        return Arbitraries.of("stop", "length", "content_filter", null);
    }

    @Provide
    Arbitrary<List<Choice>> choices() {
        return assistantMessage()
                .flatMap(message -> finishReason()
                        .map(reason -> new Choice(0, message, reason)))
                .list()
                .ofMinSize(1)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<Usage> usage() {
        return Arbitraries.integers()
                .between(0, 10000)
                .flatMap(promptTokens ->
                        Arbitraries.integers()
                                .between(0, 10000)
                                .map(completionTokens ->
                                        new Usage(promptTokens, completionTokens, promptTokens + completionTokens)
                                )
                );
    }
}

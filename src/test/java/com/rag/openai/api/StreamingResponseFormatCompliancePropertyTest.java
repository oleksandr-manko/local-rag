package com.rag.openai.api;

import com.rag.openai.domain.dto.*;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for OpenAI API streaming response format compliance.
 * **Validates: Requirements 1.4, 11.4**
 * 
 * Property 3: Streaming Response Format Compliance
 * 
 * This test suite verifies that ChatCompletionChunk conforms to OpenAI streaming API specifications:
 * - Each chunk must have a valid ID in the format "chatcmpl-{uuid}"
 * - Object type must be "chat.completion.chunk"
 * - Created timestamp must be a valid Unix timestamp (positive long)
 * - Model name must not be null or blank
 * - Choices list must not be null or empty
 * - Each choice must have a valid index (non-negative)
 * - Each choice must have a delta with optional role and content
 * - First chunk should have role "assistant" in delta
 * - Subsequent chunks should have content in delta
 * - Final chunk should have finish_reason set to "stop"
 */
class StreamingResponseFormatCompliancePropertyTest {

    // ==================== ChatCompletionChunk Format Tests ====================

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has null id Then throws NullPointerException")
    void chunkRejectsNullId(
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: null id
        String id = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating chunk should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("ID must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has null object Then throws NullPointerException")
    void chunkRejectsNullObject(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: null object
        String object = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating chunk should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionChunk(
                id, object, created, model, choices
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Object must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has null model Then throws NullPointerException")
    void chunkRejectsNullModel(
            @ForAll("completionId") String id,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: null model
        String model = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating chunk should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Model must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has null choices Then throws NullPointerException")
    void chunkRejectsNullChoices(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: null choices
        List<ChunkChoice> choices = null;
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating chunk should throw NullPointerException
        assertThatThrownBy(() -> new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Choices must not be null");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has empty choices Then throws IllegalArgumentException")
    void chunkRejectsEmptyChoices(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: empty choices list
        List<ChunkChoice> choices = List.of();
        long created = System.currentTimeMillis() / 1000;
        
        // When & Then: creating chunk should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Choices list must not be empty");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk has valid parameters Then creates successfully")
    void chunkAcceptsValidParameters(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: valid parameters
        long created = System.currentTimeMillis() / 1000;
        String object = "chat.completion.chunk";
        
        // When: creating chunk
        var chunk = new ChatCompletionChunk(
                id, object, created, model, choices
        );
        
        // Then: chunk is created successfully with correct values
        assertThat(chunk.id()).isEqualTo(id);
        assertThat(chunk.object()).isEqualTo(object);
        assertThat(chunk.created()).isEqualTo(created);
        assertThat(chunk.model()).isEqualTo(model);
        assertThat(chunk.choices()).isEqualTo(choices);
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk is created Then id follows OpenAI format")
    void chunkIdFollowsOpenAIFormat(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: valid parameters with OpenAI-formatted id
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        );
        
        // Then: id follows OpenAI format (chatcmpl-{alphanumeric})
        assertThat(chunk.id()).startsWith("chatcmpl-");
        assertThat(chunk.id().length()).isGreaterThan(9); // "chatcmpl-" + at least 1 char
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk is created Then object type is chat.completion.chunk")
    void chunkObjectTypeIsChatCompletionChunk(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: valid parameters
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        );
        
        // Then: object type is exactly "chat.completion.chunk"
        assertThat(chunk.object()).isEqualTo("chat.completion.chunk");
    }

    @Property(tries = 100)
    @Label("When ChatCompletionChunk is created Then created timestamp is positive")
    void chunkCreatedTimestampIsPositive(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("chunkChoices") List<ChunkChoice> choices
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: valid parameters with current timestamp
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, choices
        );
        
        // Then: created timestamp is positive (valid Unix timestamp)
        assertThat(chunk.created()).isPositive();
    }

    // ==================== ChunkChoice Format Tests ====================

    @Property(tries = 100)
    @Label("When ChunkChoice has negative index Then throws IllegalArgumentException")
    void chunkChoiceRejectsNegativeIndex(
            @ForAll("delta") Delta delta
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: negative index
        int index = -1;
        
        // When & Then: creating chunk choice should throw IllegalArgumentException
        assertThatThrownBy(() -> new ChunkChoice(index, delta, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Index must be non-negative");
    }

    @Property(tries = 100)
    @Label("When ChunkChoice has null delta Then throws NullPointerException")
    void chunkChoiceRejectsNullDelta(
            @ForAll @IntRange(min = 0, max = 10) int index
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: null delta
        Delta delta = null;
        
        // When & Then: creating chunk choice should throw NullPointerException
        assertThatThrownBy(() -> new ChunkChoice(index, delta, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Delta must not be null");
    }

    @Property(tries = 100)
    @Label("When ChunkChoice has valid parameters Then creates successfully")
    void chunkChoiceAcceptsValidParameters(
            @ForAll @IntRange(min = 0, max = 10) int index,
            @ForAll("delta") Delta delta,
            @ForAll("finishReason") String finishReason
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: valid parameters
        
        // When: creating chunk choice
        var chunkChoice = new ChunkChoice(index, delta, finishReason);
        
        // Then: chunk choice is created successfully with correct values
        assertThat(chunkChoice.index()).isEqualTo(index);
        assertThat(chunkChoice.delta()).isEqualTo(delta);
        assertThat(chunkChoice.finishReason()).isEqualTo(finishReason);
    }

    // ==================== Delta Format Tests ====================

    @Property(tries = 100)
    @Label("When Delta has both role and content Then creates successfully")
    void deltaAcceptsBothRoleAndContent(
            @ForAll("role") String role,
            @ForAll("content") String content
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: both role and content
        
        // When: creating delta
        var delta = new Delta(Optional.of(role), Optional.of(content));
        
        // Then: delta is created successfully with both values
        assertThat(delta.role()).isPresent().contains(role);
        assertThat(delta.content()).isPresent().contains(content);
    }

    @Property(tries = 100)
    @Label("When Delta has only role Then creates successfully")
    void deltaAcceptsOnlyRole(
            @ForAll("role") String role
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: only role, no content
        
        // When: creating delta
        var delta = new Delta(Optional.of(role), Optional.empty());
        
        // Then: delta is created successfully with role only
        assertThat(delta.role()).isPresent().contains(role);
        assertThat(delta.content()).isEmpty();
    }

    @Property(tries = 100)
    @Label("When Delta has only content Then creates successfully")
    void deltaAcceptsOnlyContent(
            @ForAll("content") String content
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: only content, no role
        
        // When: creating delta
        var delta = new Delta(Optional.empty(), Optional.of(content));
        
        // Then: delta is created successfully with content only
        assertThat(delta.role()).isEmpty();
        assertThat(delta.content()).isPresent().contains(content);
    }

    @Property(tries = 100)
    @Label("When Delta has neither role nor content Then creates successfully")
    void deltaAcceptsEmptyOptionals() {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: neither role nor content
        
        // When: creating delta
        var delta = new Delta(Optional.empty(), Optional.empty());
        
        // Then: delta is created successfully with both empty
        assertThat(delta.role()).isEmpty();
        assertThat(delta.content()).isEmpty();
    }

    // ==================== Streaming Sequence Tests ====================

    @Property(tries = 100)
    @Label("When first chunk is created Then delta contains role assistant")
    void firstChunkContainsRoleAssistant(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("content") String content
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: first chunk with role and content
        long created = System.currentTimeMillis() / 1000;
        Delta delta = new Delta(Optional.of("assistant"), Optional.of(content));
        ChunkChoice choice = new ChunkChoice(0, delta, null);
        
        // When: creating first chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, List.of(choice)
        );
        
        // Then: chunk contains role "assistant" in delta
        assertThat(chunk.choices().get(0).delta().role())
                .isPresent()
                .contains("assistant");
    }

    @Property(tries = 100)
    @Label("When subsequent chunk is created Then delta contains only content")
    void subsequentChunkContainsOnlyContent(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("content") String content
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: subsequent chunk with only content
        long created = System.currentTimeMillis() / 1000;
        Delta delta = new Delta(Optional.empty(), Optional.of(content));
        ChunkChoice choice = new ChunkChoice(0, delta, null);
        
        // When: creating subsequent chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, List.of(choice)
        );
        
        // Then: chunk contains only content in delta, no role
        assertThat(chunk.choices().get(0).delta().role()).isEmpty();
        assertThat(chunk.choices().get(0).delta().content())
                .isPresent()
                .contains(content);
    }

    @Property(tries = 100)
    @Label("When final chunk is created Then finish_reason is stop")
    void finalChunkHasFinishReasonStop(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: final chunk with empty delta and finish_reason
        long created = System.currentTimeMillis() / 1000;
        Delta delta = new Delta(Optional.empty(), Optional.empty());
        ChunkChoice choice = new ChunkChoice(0, delta, "stop");
        
        // When: creating final chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, List.of(choice)
        );
        
        // Then: chunk has finish_reason set to "stop"
        assertThat(chunk.choices().get(0).finishReason()).isEqualTo("stop");
        assertThat(chunk.choices().get(0).delta().role()).isEmpty();
        assertThat(chunk.choices().get(0).delta().content()).isEmpty();
    }

    @Property(tries = 100)
    @Label("When streaming sequence is created Then all chunks share same id and created timestamp")
    void streamingSequenceSharesIdAndTimestamp(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("contentList") List<String> contentList
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: multiple chunks in a streaming sequence
        long created = System.currentTimeMillis() / 1000;
        
        // When: creating chunks with same id and timestamp
        var firstChunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model,
                List.of(new ChunkChoice(0, new Delta(Optional.of("assistant"), Optional.of(contentList.get(0))), null))
        );
        
        var secondChunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model,
                List.of(new ChunkChoice(0, new Delta(Optional.empty(), Optional.of(contentList.get(1))), null))
        );
        
        var finalChunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model,
                List.of(new ChunkChoice(0, new Delta(Optional.empty(), Optional.empty()), "stop"))
        );
        
        // Then: all chunks share the same id and created timestamp
        assertThat(firstChunk.id()).isEqualTo(secondChunk.id()).isEqualTo(finalChunk.id());
        assertThat(firstChunk.created()).isEqualTo(secondChunk.created()).isEqualTo(finalChunk.created());
        assertThat(firstChunk.model()).isEqualTo(secondChunk.model()).isEqualTo(finalChunk.model());
    }

    // ==================== Complete Streaming Response Structure Tests ====================

    @Property(tries = 100)
    @Label("When complete streaming chunk is created Then all required fields are present")
    void completeStreamingChunkHasAllRequiredFields(
            @ForAll("completionId") String id,
            @ForAll @NotBlank @AlphaChars String model,
            @ForAll("content") String content
    ) {
        // Feature: rag-openai-api-ollama, Property 3: Streaming Response Format Compliance
        
        // Given: all valid components for a streaming chunk
        long created = System.currentTimeMillis() / 1000;
        Delta delta = new Delta(Optional.of("assistant"), Optional.of(content));
        ChunkChoice choice = new ChunkChoice(0, delta, null);
        
        // When: creating complete streaming chunk
        var chunk = new ChatCompletionChunk(
                id, "chat.completion.chunk", created, model, List.of(choice)
        );
        
        // Then: all required fields are present and valid
        assertThat(chunk.id()).isNotNull().startsWith("chatcmpl-");
        assertThat(chunk.object()).isEqualTo("chat.completion.chunk");
        assertThat(chunk.created()).isPositive();
        assertThat(chunk.model()).isNotBlank();
        assertThat(chunk.choices()).isNotEmpty();
        assertThat(chunk.choices().get(0).index()).isGreaterThanOrEqualTo(0);
        assertThat(chunk.choices().get(0).delta()).isNotNull();
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
    Arbitrary<String> role() {
        return Arbitraries.of("assistant", "user", "system");
    }

    @Provide
    Arbitrary<String> content() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '.', ',', '!', '?')
                .ofMinLength(1)
                .ofMaxLength(100);
    }

    @Provide
    Arbitrary<List<String>> contentList() {
        return content()
                .list()
                .ofMinSize(2)
                .ofMaxSize(5);
    }

    @Provide
    Arbitrary<String> finishReason() {
        return Arbitraries.of("stop", "length", "content_filter", null);
    }

    @Provide
    Arbitrary<Delta> delta() {
        return Arbitraries.integers().between(0, 3).flatMap(type -> {
            switch (type) {
                case 0: // Both role and content
                    return Combinators.combine(role(), content())
                            .as((r, c) -> new Delta(Optional.of(r), Optional.of(c)));
                case 1: // Only role
                    return role().map(r -> new Delta(Optional.of(r), Optional.empty()));
                case 2: // Only content
                    return content().map(c -> new Delta(Optional.empty(), Optional.of(c)));
                default: // Neither
                    return Arbitraries.just(new Delta(Optional.empty(), Optional.empty()));
            }
        });
    }

    @Provide
    Arbitrary<List<ChunkChoice>> chunkChoices() {
        return delta()
                .flatMap(d -> finishReason()
                        .map(reason -> new ChunkChoice(0, d, reason)))
                .list()
                .ofMinSize(1)
                .ofMaxSize(3);
    }
}

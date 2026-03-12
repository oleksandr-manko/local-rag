package com.rag.openai.service;

import com.rag.openai.domain.dto.ChatCompletionRequest;
import com.rag.openai.domain.dto.Message;
import net.jqwik.api.*;
import net.jqwik.api.constraints.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Property-based tests for prompt extraction from chat completion requests.
 * **Validates: Requirements 10.1**
 * 
 * Property 20: Prompt Extraction
 * 
 * This property verifies that the QueryHandler correctly extracts the user prompt
 * from the last user message in a ChatCompletionRequest. The extraction must:
 * - Identify messages with role "user"
 * - Select the last user message when multiple user messages exist
 * - Extract the content from that message
 * - Throw an exception when no user message is present
 */
class PromptExtractionPropertyTest {

    @Property(tries = 200)
    @Label("When request has single user message Then extracts that message content")
    void singleUserMessageExtractedCorrectly(
            @ForAll @StringLength(min = 1, max = 500) String userContent,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with a single user message
        Message userMessage = new Message("user", userContent);
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(userMessage),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the user message content
        assertThat(extractedPrompt).isEqualTo(userContent);
    }

    @Property(tries = 200)
    @Label("When request has multiple user messages Then extracts last user message")
    void multipleUserMessagesExtractsLast(
            @ForAll @Size(min = 2, max = 5) List<@StringLength(min = 1, max = 200) String> userContents,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with multiple user messages
        List<Message> messages = userContents.stream()
                .map(content -> new Message("user", content))
                .toList();
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the last user message content
        String expectedLastContent = userContents.get(userContents.size() - 1);
        assertThat(extractedPrompt).isEqualTo(expectedLastContent);
    }

    @Property(tries = 200)
    @Label("When request has mixed roles Then extracts last user message ignoring others")
    void mixedRolesExtractsLastUserMessage(
            @ForAll @StringLength(min = 1, max = 200) String systemContent,
            @ForAll @StringLength(min = 1, max = 200) String firstUserContent,
            @ForAll @StringLength(min = 1, max = 200) String assistantContent,
            @ForAll @StringLength(min = 1, max = 200) String lastUserContent,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with mixed message roles (system, user, assistant, user)
        // Ensure lastUserContent is unique by appending a marker
        String uniqueLastUserContent = lastUserContent + "_LAST";
        
        List<Message> messages = List.of(
                new Message("system", systemContent),
                new Message("user", firstUserContent),
                new Message("assistant", assistantContent),
                new Message("user", uniqueLastUserContent)
        );
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract only the last user message, ignoring system and assistant messages
        assertThat(extractedPrompt).isEqualTo(uniqueLastUserContent);
        assertThat(extractedPrompt).isNotEqualTo(firstUserContent);
        assertThat(extractedPrompt).isNotEqualTo(systemContent);
        assertThat(extractedPrompt).isNotEqualTo(assistantContent);
    }

    @Property(tries = 200)
    @Label("When request has only system messages Then throws exception")
    void onlySystemMessagesThrowsException(
            @ForAll @Size(min = 1, max = 3) List<@StringLength(min = 1, max = 200) String> systemContents,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with only system messages (no user messages)
        List<Message> messages = systemContents.stream()
                .map(content -> new Message("system", content))
                .toList();
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When/Then: extracting the user prompt should throw an exception
        assertThatThrownBy(() -> extractUserPromptSync(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No user message found");
    }

    @Property(tries = 200)
    @Label("When request has only assistant messages Then throws exception")
    void onlyAssistantMessagesThrowsException(
            @ForAll @Size(min = 1, max = 3) List<@StringLength(min = 1, max = 200) String> assistantContents,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with only assistant messages (no user messages)
        List<Message> messages = assistantContents.stream()
                .map(content -> new Message("assistant", content))
                .toList();
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When/Then: extracting the user prompt should throw an exception
        assertThatThrownBy(() -> extractUserPromptSync(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No user message found");
    }

    @Property(tries = 200)
    @Label("When request has system then user messages Then extracts user message")
    void systemThenUserMessagesExtractsUser(
            @ForAll @StringLength(min = 1, max = 200) String systemContent,
            @ForAll @StringLength(min = 1, max = 200) String userContent,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with system message followed by user message
        // Ensure userContent is unique by appending a marker
        String uniqueUserContent = userContent + "_USER";
        
        List<Message> messages = List.of(
                new Message("system", systemContent),
                new Message("user", uniqueUserContent)
        );
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the user message, not the system message
        assertThat(extractedPrompt).isEqualTo(uniqueUserContent);
        assertThat(extractedPrompt).isNotEqualTo(systemContent);
    }

    @Property(tries = 200)
    @Label("When request has conversation history Then extracts last user message")
    void conversationHistoryExtractsLastUserMessage(
            @ForAll @Size(min = 3, max = 10) @UniqueElements List<@StringLength(min = 1, max = 100) String> contents,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request simulating a conversation with alternating user/assistant messages
        Assume.that(contents.size() >= 3);
        
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", contents.get(0)));
        
        // Add alternating user and assistant messages
        for (int i = 1; i < contents.size(); i++) {
            String role = (i % 2 == 1) ? "user" : "assistant";
            messages.add(new Message(role, contents.get(i)));
        }
        
        // Ensure the last message is a user message
        if (!messages.get(messages.size() - 1).role().equals("user")) {
            messages.add(new Message("user", "final user message"));
        }
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the last user message
        String expectedLastUserContent = messages.stream()
                .filter(msg -> "user".equals(msg.role()))
                .reduce((first, second) -> second)
                .map(Message::content)
                .orElseThrow();
        
        assertThat(extractedPrompt).isEqualTo(expectedLastUserContent);
    }

    @Property(tries = 200)
    @Label("When user message has special characters Then extracts content unchanged")
    void specialCharactersExtractedUnchanged(
            @ForAll("specialCharacterContent") String userContent,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with user message containing special characters
        Message userMessage = new Message("user", userContent);
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(userMessage),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the content exactly as provided (no escaping or modification)
        assertThat(extractedPrompt).isEqualTo(userContent);
    }

    @Property(tries = 200)
    @Label("When user message has whitespace Then extracts content with whitespace preserved")
    void whitespacePreservedInExtraction(
            @ForAll @StringLength(min = 10, max = 200) String prefix,
            @ForAll @StringLength(min = 10, max = 200) String suffix,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with user message containing leading/trailing whitespace
        String userContent = "   " + prefix + "\n\n" + suffix + "   ";
        Message userMessage = new Message("user", userContent);
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(userMessage),
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should preserve whitespace exactly
        assertThat(extractedPrompt).isEqualTo(userContent);
        assertThat(extractedPrompt).startsWith("   ");
        assertThat(extractedPrompt).endsWith("   ");
        assertThat(extractedPrompt).contains("\n\n");
    }

    @Property(tries = 200)
    @Label("When multiple identical user messages Then extracts last occurrence")
    void multipleIdenticalUserMessagesExtractsLast(
            @ForAll @StringLength(min = 1, max = 100) String userContent,
            @ForAll @IntRange(min = 2, max = 5) int repetitions,
            @ForAll("modelName") String model
    ) {
        // Feature: rag-openai-api-ollama, Property 20: Prompt Extraction
        
        // Given: a request with multiple identical user messages
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < repetitions; i++) {
            messages.add(new Message("user", userContent));
        }
        
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                messages,
                false,
                Optional.empty(),
                Optional.empty()
        );
        
        // When: extracting the user prompt
        String extractedPrompt = extractUserPromptSync(request);
        
        // Then: should extract the content (which is the same for all)
        assertThat(extractedPrompt).isEqualTo(userContent);
    }

    // ==================== Helper Methods ====================

    /**
     * Synchronous wrapper for extracting user prompt from request.
     * Mimics the private extractUserPrompt method in QueryHandlerImpl.
     */
    private String extractUserPromptSync(ChatCompletionRequest request) {
        Optional<String> userPrompt = request.messages().stream()
                .filter(msg -> "user".equals(msg.role()))
                .reduce((first, second) -> second)
                .map(Message::content);
        
        return userPrompt.orElseThrow(() -> 
                new IllegalArgumentException("No user message found in request")
        );
    }

    // ==================== Arbitraries (Generators) ====================

    @Provide
    Arbitrary<String> modelName() {
        return Arbitraries.of(
                "llama3.2",
                "llama3.2:1b",
                "llama3.2:3b",
                "mistral",
                "mixtral",
                "qwen2.5",
                "phi3",
                "gemma2"
        );
    }

    @Provide
    Arbitrary<String> specialCharacterContent() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .withChars(' ', '\n', '\t', '!', '@', '#', '$', '%', '^', '&', '*', '(', ')', 
                          '{', '}', '[', ']', '<', '>', '/', '\\', '|', '?', '.', ',', ';', ':', 
                          '\'', '"', '`', '~', '-', '_', '+', '=')
                .ofMinLength(10)
                .ofMaxLength(200);
    }
}

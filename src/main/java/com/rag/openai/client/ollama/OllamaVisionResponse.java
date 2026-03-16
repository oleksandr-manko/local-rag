package com.rag.openai.client.ollama;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Response from Ollama vision API using the OpenAI-compatible /v1/chat/completions format.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaVisionResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    public OllamaVisionResponse {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(choices, "Choices must not be null");
    }

    /**
     * Extracts the generated text from the first choice's message content.
     */
    public String response() {
        if (choices.isEmpty()) {
            return "";
        }
        VisionMessage msg = choices.getFirst().message();
        return msg != null && msg.content() != null ? msg.content() : "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
        int index,
        VisionMessage message,
        @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VisionMessage(
        String role,
        String content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens,
        @JsonProperty("total_tokens") int totalTokens
    ) {}
}

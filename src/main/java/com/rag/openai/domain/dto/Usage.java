package com.rag.openai.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents token usage information in an OpenAI response.
 * 
 * @param promptTokens Number of tokens in the prompt
 * @param completionTokens Number of tokens in the completion
 * @param totalTokens Total number of tokens used
 */
public record Usage(
    @JsonProperty("prompt_tokens") int promptTokens,
    @JsonProperty("completion_tokens") int completionTokens,
    @JsonProperty("total_tokens") int totalTokens
) {
    public Usage {
        if (promptTokens < 0) {
            throw new IllegalArgumentException("Prompt tokens must be non-negative");
        }
        if (completionTokens < 0) {
            throw new IllegalArgumentException("Completion tokens must be non-negative");
        }
        if (totalTokens < 0) {
            throw new IllegalArgumentException("Total tokens must be non-negative");
        }
    }
}

package com.rag.openai.client.ollama;

import java.util.List;
import java.util.Objects;

/**
 * Represents an OpenAI-compatible completions response from Ollama.
 *
 * @param id The completion ID
 * @param object The object type (e.g., "text_completion")
 * @param created The creation timestamp
 * @param model The model used for generation
 * @param choices The list of completion choices
 * @param usage Token usage information
 */
public record OllamaGenerateResponse(
    String id,
    String object,
    long created,
    String model,
    List<Choice> choices,
    Usage usage
) {
    public OllamaGenerateResponse {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(choices, "Choices must not be null");
    }

    /**
     * Extracts the generated text from the first choice.
     */
    public String response() {
        if (choices.isEmpty()) {
            return "";
        }
        return choices.getFirst().text() != null ? choices.getFirst().text() : "";
    }

    public record Choice(
        String text,
        int index,
        Object logprobs,
        String finish_reason
    ) {}

    public record Usage(
        int prompt_tokens,
        int completion_tokens,
        int total_tokens
    ) {}
}

package com.rag.openai.client.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Request for Ollama vision API using the OpenAI-compatible /v1/chat/completions endpoint.
 * Vision requires the chat completions format with multipart content (text + image_url).
 */
public record OllamaVisionRequest(
    String model,
    List<VisionMessage> messages,
    boolean stream
) {
    public OllamaVisionRequest {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(messages, "Messages must not be null");
    }

    public record VisionMessage(
        String role,
        List<ContentPart> content
    ) {}

    public sealed interface ContentPart permits TextContent, ImageContent {}

    public record TextContent(
        String type,
        String text
    ) implements ContentPart {
        public TextContent(String text) {
            this("text", text);
        }
    }

    public record ImageContent(
        String type,
        @JsonProperty("image_url") ImageUrl imageUrl
    ) implements ContentPart {
        public ImageContent(String base64Data) {
            this("image_url", new ImageUrl("data:image/png;base64," + base64Data));
        }
    }

    public record ImageUrl(String url) {}
}

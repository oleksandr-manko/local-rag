package com.rag.openai.domain.dto;

import java.util.Objects;

/**
 * Represents a message in an OpenAI chat completion request or response.
 * 
 * @param role The role of the message author (system, user, or assistant)
 * @param content The content of the message
 */
public record Message(
    String role,
    String content
) {
    public Message {
        Objects.requireNonNull(role, "Message role must not be null");
        Objects.requireNonNull(content, "Message content must not be null");
        if (role.isBlank()) {
            throw new IllegalArgumentException("Message role must not be blank");
        }
    }
}

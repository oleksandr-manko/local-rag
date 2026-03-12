package com.rag.openai.client.ollama;

import java.util.List;
import java.util.Optional;

/**
 * Request for Ollama vision API to analyze images.
 * 
 * @param model The vision model to use
 * @param prompt The prompt describing what to extract from the image
 * @param images List of base64-encoded images
 * @param stream Whether to stream the response
 * @param options Optional generation options
 */
public record OllamaVisionRequest(
    String model,
    String prompt,
    List<String> images,
    boolean stream,
    Optional<OllamaOptions> options
) {}

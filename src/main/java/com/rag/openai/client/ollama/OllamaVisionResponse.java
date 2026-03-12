package com.rag.openai.client.ollama;

/**
 * Response from Ollama vision API.
 * 
 * @param model The model that generated the response
 * @param response The generated text response
 * @param done Whether the generation is complete
 */
public record OllamaVisionResponse(
    String model,
    String response,
    boolean done
) {}

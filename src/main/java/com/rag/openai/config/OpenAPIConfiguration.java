package com.rag.openai.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration class for OpenAPI documentation and Swagger UI.
 * Provides comprehensive API documentation for all public endpoints.
 */
@Configuration
public class OpenAPIConfiguration {

    /**
     * Configures the OpenAPI specification with API metadata, server information,
     * and external documentation links.
     *
     * @return configured OpenAPI instance
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("RAG OpenAI API with Ollama")
                .version("1.0.0")
                .description("Retrieval-Augmented Generation API with OpenAI-compatible endpoints. " +
                    "This API provides document processing, vector storage, and RAG-enhanced chat completions " +
                    "using local Ollama models.")
                .contact(new Contact()
                    .name("API Support")
                    .email("support@example.com")))
            .servers(List.of(
                new Server()
                    .url("http://localhost:8080")
                    .description("Local development server")))
            .externalDocs(new ExternalDocumentation()
                .description("Project Documentation")
                .url("https://github.com/example/rag-openai-api-ollama"));
    }

    /**
     * Configures grouped API documentation for public endpoints.
     * Includes all endpoints under /v1/** and /api/** paths while excluding actuator endpoints.
     *
     * @return configured GroupedOpenApi instance for public endpoints
     */
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/v1/**", "/api/**")
            .pathsToExclude("/actuator/**")
            .build();
    }
}

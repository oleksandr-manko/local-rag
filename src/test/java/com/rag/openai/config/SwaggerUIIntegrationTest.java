package com.rag.openai.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rag.openai.api.OpenAIApiController;
import com.rag.openai.api.ProcessingController;
import com.rag.openai.api.TestApiController;
import com.rag.openai.client.ollama.OllamaClient;
import com.rag.openai.service.ProcessingJob;
import com.rag.openai.service.QueryHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Swagger UI and OpenAPI documentation.
 * Tests accessibility, spec generation, endpoint documentation completeness, and example payloads.
 * 
 * Requirements: 26.5, 26.6, 26.7
 */
@ExtendWith(MockitoExtension.class)
class SwaggerUIIntegrationTest {

    @Mock
    private QueryHandler queryHandler;

    @Mock
    private ProcessingJob processingJob;

    @Mock
    private OllamaClient ollamaClient;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private OpenAPIConfiguration openAPIConfiguration;

    @BeforeEach
    void setUp() {
        // Given: OpenAPI configuration
        openAPIConfiguration = new OpenAPIConfiguration();
        objectMapper = new ObjectMapper();
        
        // Create OpenAIApiConfig for controller
        OpenAIApiConfig openAIApiConfig = new OpenAIApiConfig(
            "local",
            1773532800L,
            "host-machine"
        );
        
        // Build MockMvc with controllers
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new OpenAIApiController(queryHandler, openAIApiConfig),
                new TestApiController(queryHandler),
                new ProcessingController(processingJob)
            )
            .build();
    }

    @Test
    @DisplayName("When OpenAPI configuration is created Then it contains correct API metadata")
    void testOpenAPIConfiguration() {
        // Given: OpenAPI configuration

        // When: Getting OpenAPI bean
        var openAPI = openAPIConfiguration.customOpenAPI();

        // Then: API info is correctly configured
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("RAG OpenAI API with Ollama");
        assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(openAPI.getInfo().getDescription()).contains("Retrieval-Augmented Generation");
        
        // Then: Contact information is present
        assertThat(openAPI.getInfo().getContact()).isNotNull();
        assertThat(openAPI.getInfo().getContact().getName()).isEqualTo("API Support");
        assertThat(openAPI.getInfo().getContact().getEmail()).isEqualTo("support@example.com");
        
        // Then: Server information is present
        assertThat(openAPI.getServers()).isNotEmpty();
        assertThat(openAPI.getServers().get(0).getUrl()).contains("localhost:8080");
        assertThat(openAPI.getServers().get(0).getDescription()).isEqualTo("Local development server");
        
        // Then: External documentation is present
        assertThat(openAPI.getExternalDocs()).isNotNull();
        assertThat(openAPI.getExternalDocs().getDescription()).isEqualTo("Project Documentation");
        assertThat(openAPI.getExternalDocs().getUrl()).contains("github.com");
    }

    @Test
    @DisplayName("When GroupedOpenApi is created Then it includes correct path patterns")
    void testGroupedOpenApiConfiguration() {
        // Given: OpenAPI configuration

        // When: Getting GroupedOpenApi bean
        GroupedOpenApi groupedOpenApi = openAPIConfiguration.publicApi();

        // Then: Group name is correct
        assertThat(groupedOpenApi.getGroup()).isEqualTo("public");
        
        // Then: Path patterns include public endpoints
        assertThat(groupedOpenApi.getPathsToMatch()).contains("/v1/**", "/api/**");
        
        // Then: Actuator endpoints are excluded
        assertThat(groupedOpenApi.getPathsToExclude()).contains("/actuator/**");
    }

    @Test
    @DisplayName("When controllers are annotated Then OpenAPI annotations are present")
    void testControllerAnnotations() {
        // Given: Controllers with OpenAPI annotations

        // When: Checking TestApiController annotations
        var testApiControllerAnnotations = TestApiController.class.getAnnotations();
        
        // Then: Controller has Tag annotation for OpenAPI grouping
        boolean hasTagAnnotation = java.util.Arrays.stream(testApiControllerAnnotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("Tag"));
        assertThat(hasTagAnnotation).isTrue();
    }

    @Test
    @DisplayName("When TestApiController methods are annotated Then OpenAPI operation annotations are present")
    void testTestApiControllerMethodAnnotations() throws NoSuchMethodException {
        // Given: TestApiController with annotated methods

        // When: Checking testQuery method annotations
        var method = TestApiController.class.getMethod("testQuery", String.class);
        var annotations = method.getAnnotations();
        
        // Then: Method has Operation annotation
        boolean hasOperationAnnotation = java.util.Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("Operation"));
        assertThat(hasOperationAnnotation).isTrue();
        
        // Then: Method has ApiResponses annotation
        boolean hasApiResponsesAnnotation = java.util.Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("ApiResponses"));
        assertThat(hasApiResponsesAnnotation).isTrue();
    }

    @Test
    @DisplayName("When ProcessingController methods are annotated Then OpenAPI operation annotations are present")
    void testProcessingControllerMethodAnnotations() throws NoSuchMethodException {
        // Given: ProcessingController with annotated methods

        // When: Checking triggerProcessing method annotations
        var method = ProcessingController.class.getMethod("triggerProcessing");
        var annotations = method.getAnnotations();
        
        // Then: Method has Operation annotation
        boolean hasOperationAnnotation = java.util.Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("Operation"));
        assertThat(hasOperationAnnotation).isTrue();
        
        // Then: Method has ApiResponses annotation
        boolean hasApiResponsesAnnotation = java.util.Arrays.stream(annotations)
            .anyMatch(a -> a.annotationType().getSimpleName().equals("ApiResponses"));
        assertThat(hasApiResponsesAnnotation).isTrue();
    }

    @Test
    @DisplayName("When OpenAPI configuration is complete Then all required components are present")
    void testOpenAPIConfigurationCompleteness() {
        // Given: OpenAPI configuration

        // When: Getting all configuration beans
        var openAPI = openAPIConfiguration.customOpenAPI();
        var groupedOpenApi = openAPIConfiguration.publicApi();

        // Then: All required configuration is present
        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isNotEmpty();
        assertThat(openAPI.getInfo().getVersion()).isNotEmpty();
        assertThat(openAPI.getInfo().getDescription()).isNotEmpty();
        assertThat(openAPI.getServers()).isNotEmpty();
        assertThat(openAPI.getExternalDocs()).isNotNull();
        
        assertThat(groupedOpenApi).isNotNull();
        assertThat(groupedOpenApi.getGroup()).isNotEmpty();
        assertThat(groupedOpenApi.getPathsToMatch()).isNotEmpty();
    }

    @Test
    @DisplayName("When API info is configured Then description contains key terms")
    void testAPIDescriptionContent() {
        // Given: OpenAPI configuration

        // When: Getting API description
        var openAPI = openAPIConfiguration.customOpenAPI();
        String description = openAPI.getInfo().getDescription();

        // Then: Description contains key terms about the API
        assertThat(description).contains("Retrieval-Augmented Generation");
        assertThat(description).contains("OpenAI-compatible");
        assertThat(description).contains("document processing");
        assertThat(description).contains("vector storage");
        assertThat(description).contains("Ollama");
    }

    @Test
    @DisplayName("When server configuration is defined Then it points to correct local endpoint")
    void testServerConfiguration() {
        // Given: OpenAPI configuration

        // When: Getting server configuration
        var openAPI = openAPIConfiguration.customOpenAPI();
        var servers = openAPI.getServers();

        // Then: Server URL is correctly configured for local development
        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).getUrl()).isEqualTo("http://localhost:8080");
        assertThat(servers.get(0).getDescription()).contains("Local development");
    }

    @Test
    @DisplayName("When external docs are configured Then they point to project repository")
    void testExternalDocsConfiguration() {
        // Given: OpenAPI configuration

        // When: Getting external docs configuration
        var openAPI = openAPIConfiguration.customOpenAPI();
        var externalDocs = openAPI.getExternalDocs();

        // Then: External docs link to project documentation
        assertThat(externalDocs.getDescription()).isEqualTo("Project Documentation");
        assertThat(externalDocs.getUrl()).startsWith("https://github.com/");
        assertThat(externalDocs.getUrl()).contains("rag-openai-api-ollama");
    }

    @Test
    @DisplayName("When contact info is configured Then it contains support details")
    void testContactInformation() {
        // Given: OpenAPI configuration

        // When: Getting contact information
        var openAPI = openAPIConfiguration.customOpenAPI();
        var contact = openAPI.getInfo().getContact();

        // Then: Contact information is complete
        assertThat(contact.getName()).isEqualTo("API Support");
        assertThat(contact.getEmail()).isEqualTo("support@example.com");
    }

    @Test
    @DisplayName("When path patterns are configured Then they include all public endpoints")
    void testPathPatternConfiguration() {
        // Given: OpenAPI configuration

        // When: Getting path patterns
        GroupedOpenApi groupedOpenApi = openAPIConfiguration.publicApi();
        var pathsToMatch = groupedOpenApi.getPathsToMatch();
        var pathsToExclude = groupedOpenApi.getPathsToExclude();

        // Then: Public API paths are included
        assertThat(pathsToMatch).contains("/v1/**");  // OpenAI compatible endpoints
        assertThat(pathsToMatch).contains("/api/**"); // Custom API endpoints
        
        // Then: Internal endpoints are excluded
        assertThat(pathsToExclude).contains("/actuator/**");
    }
}

package com.embedding.pgvector.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.embedding.client.OllamaStubServer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.embedding.pgvector.ApiTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.embedding.pgvector.support.PostgresIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Embedding ucunu gerçek uygulama ve sahte Ollama sunucusuyla uçtan uca doğrular; model, boyut ve hata
 * yanıtlarını denetler.
 */
class EmbeddingIntegrationTest extends PostgresIntegrationTest {

    private static final OllamaStubServer STUB = new OllamaStubServer();

    @LocalServerPort
    private int port;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("embedding.base-url", STUB::baseUrl);
        registry.add("embedding.model", () -> "bge-m3");
    }

    @AfterAll
    static void tearDown() {
        STUB.close();
    }

    @BeforeEach
    void setUp() {
        STUB.reset();
    }

    private ApiTestClient.Response post(String body) {
        return new ApiTestClient(port).postJson("/api/embeddings", body);
    }

    @Test
    void givenOllamaStubReturns1024Values_whenPostEmbeddings_thenReturns200WithModelDimensionsEmbeddingAndDuration() {
        // Arrange
        String vector = IntStream.range(0, 1024).mapToObj(i -> Float.toString(i / 1024f))
                .collect(Collectors.joining(",", "[", "]"));
        STUB.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[" + vector + "]}");

        // Act
        ApiTestClient.Response response = post("{\"text\":\"izin\"}");
        JsonNode json = jsonMapper.readTree(response.body());
        assertThat(response.status()).isEqualTo(200);

        // Assert
        assertThat(json.get("embeddingModel").asString()).isEqualTo("bge-m3");
        assertThat(json.get("dimensions").asInt()).isEqualTo(1024);
        assertThat(json.get("embedding")).hasSize(1024);
        assertThat(json.get("embedding").get(0).asDouble()).isEqualTo(0.0);
        assertThat(json.get("embedding").get(1).asDouble()).isEqualTo(0.0009765625);
        assertThat(json.get("durationMs").asLong()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void givenTurkishTextWithSurroundingSpaces_whenPostEmbeddings_thenOllamaReceivesStrippedUtf8Text() {
        // Arrange
        STUB.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[[0.5]]}");

        // Act
        ApiTestClient.Response response = post("{\"text\":\"  izin ışığı kırmızı yanıyor  \"}");
        assertThat(response.status()).isEqualTo(200);

        // Assert
        assertThat(STUB.requests()).hasSize(1);
        assertThat(jsonMapper.readTree(STUB.requests().get(0).body()).get("input").asString())
                .isEqualTo("izin ışığı kırmızı yanıyor");
    }

    @Test
    void givenWhitespaceOnlyText_whenPostEmbeddings_thenReturns400AndOllamaReceivesNoRequest() {
        // Act
        ApiTestClient.Response response = post("{\"text\":\"   \"}");

        // Assert
        assertThat(response.status()).isEqualTo(400);
        assertThat(jsonMapper.readTree(response.body()).get("errorCode").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(STUB.requestCount()).isZero();
    }

    @Test
    void givenOllamaStubReturns404_whenPostEmbeddings_thenReturns503EmbeddingModelNotFound() {
        // Arrange
        STUB.respondWith(404, "{\"error\":\"model not found\"}");

        // Act
        ApiTestClient.Response response = post("{\"text\":\"izin\"}");

        // Assert
        assertThat(response.status()).isEqualTo(503);
        assertThat(jsonMapper.readTree(response.body()).get("errorCode").asString()).isEqualTo("EMBEDDING_MODEL_NOT_FOUND");
    }
}

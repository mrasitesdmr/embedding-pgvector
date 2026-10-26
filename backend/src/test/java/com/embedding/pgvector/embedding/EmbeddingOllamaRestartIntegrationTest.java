package com.embedding.pgvector.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.embedding.client.OllamaStubServer;
import org.junit.jupiter.api.Test;
import com.embedding.pgvector.ApiTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.embedding.pgvector.support.PostgresIntegrationTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ollama kapalıyken 503, sonradan açılınca 200 dönüldüğünü, yani istemcinin yeniden bağlanabildiğini
 * doğrular.
 */
class EmbeddingOllamaRestartIntegrationTest extends PostgresIntegrationTest {

    private static final int OLLAMA_PORT = OllamaStubServer.freePort();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("embedding.base-url", () -> "http://127.0.0.1:" + OLLAMA_PORT);
        registry.add("embedding.model", () -> "bge-m3");
    }

    private ApiTestClient.Response post() {
        return new ApiTestClient(port).postJson("/api/embeddings", "{\"text\":\"izin\"}");
    }

    @Test
    void givenOllamaDownThenStarted_whenPostEmbeddingsTwice_thenFirstReturns503AndSecondReturns200() {
        // Arrange
        JsonMapper jsonMapper = JsonMapper.builder().build();

        // Act
        ApiTestClient.Response first = post();
        try (OllamaStubServer stub = new OllamaStubServer(OLLAMA_PORT)) {
            stub.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[[0.5,-0.25]]}");
            ApiTestClient.Response second = post();

            // Assert
            assertThat(first.status()).isEqualTo(503);
            assertThat(second.status()).isEqualTo(200);
            assertThat(jsonMapper.readTree(first.body()).get("errorCode").asString()).isEqualTo("EMBEDDING_UNAVAILABLE");
            assertThat(jsonMapper.readTree(second.body()).get("dimensions").asInt()).isEqualTo(2);
        }
    }
}

package com.embedding.pgvector.embedding.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.exception.ServiceUnavailableException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * OllamaEmbeddingClient'ın istek gövdesini, tek ve toplu embedding yanıtlarını, zaman aşımını ve hata
 * dönüşümlerini sahte Ollama sunucusuyla doğrular.
 */
class OllamaEmbeddingClientTest {

    private static final String VALID_RESPONSE = """
            {"model":"bge-m3","embeddings":[[0.5,-0.25,0.125]],"total_duration":84512000,"load_duration":1234000,"prompt_eval_count":14}
            """;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private OllamaEmbeddingClient clientFor(int port, Duration readTimeout) {
        EmbeddingProperties properties = new EmbeddingProperties(
                URI.create("http://127.0.0.1:" + port), "bge-m3", 1024, Duration.ofSeconds(2), readTimeout);
        return new OllamaEmbeddingClient(RestClient.builder(), properties);
    }

    @Test
    void givenSameTextTwice_whenEmbed_thenSendsIdenticalRequestBodies() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, VALID_RESPONSE);
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            EmbeddingResult first = client.embed("Parolamı unuttum");
            EmbeddingResult second = client.embed("Parolamı unuttum");

            // Assert
            assertThat(stub.requests()).hasSize(2);
            assertThat(stub.requests().get(0).body()).isEqualTo(stub.requests().get(1).body());
            assertThat(first.embedding()).containsExactly(0.5f, -0.25f, 0.125f);
            assertThat(second.embedding()).containsExactly(0.5f, -0.25f, 0.125f);
        }
    }

    @Test
    void givenTurkishText_whenEmbed_thenPostsConfiguredModelAndUtf8InputToApiEmbed() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, VALID_RESPONSE);
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));
            String text = "çğıİöşü ÇĞIÖŞÜ Parolamı unuttum";

            // Act
            client.embed(text);

            // Assert
            assertThat(stub.requests()).hasSize(1);
            OllamaStubServer.RecordedRequest request = stub.requests().get(0);
            assertThat(request.method()).isEqualTo("POST");
            assertThat(request.path()).isEqualTo("/api/embed");
            assertThat(jsonMapper.readTree(request.body()).get("model").asString()).isEqualTo("bge-m3");
            assertThat(jsonMapper.readTree(request.body()).get("input").asString()).isEqualTo(text);
        }
    }

    @Test
    void givenNothingListeningOnBaseUrl_whenEmbed_thenThrowsEmbeddingUnavailable() {
        // Arrange
        OllamaEmbeddingClient client = clientFor(OllamaStubServer.freePort(), Duration.ofSeconds(2));

        // Act
        Throwable thrown = catchThrowable(() -> client.embed("izin"));

        // Assert
        assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
    }

    @Test
    void givenOllamaReturns404_whenEmbed_thenThrowsEmbeddingModelNotFound() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(404, "{\"error\":\"model \\\"bge-m3\\\" not found, try pulling it first\"}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            Throwable thrown = catchThrowable(() -> client.embed("izin"));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_MODEL_NOT_FOUND");
            assertThat(thrown.getMessage()).doesNotContain("try pulling");
        }
    }

    @Test
    void givenOllamaReturns500_whenEmbed_thenThrowsEmbeddingUnavailable() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(500, "{\"error\":\"boom\"}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            Throwable thrown = catchThrowable(() -> client.embed("izin"));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
            assertThat(thrown.getMessage()).doesNotContain("boom");
        }
    }

    @Test
    void givenOllamaDoesNotAnswerWithinReadTimeout_whenEmbed_thenThrowsEmbeddingUnavailable() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.hangUntilClosed();
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofMillis(300));

            // Act
            Throwable thrown = catchThrowable(() -> client.embed("izin"));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
        }
    }

    @Test
    void givenOllamaStartedAfterFailedCall_whenEmbedAgain_thenReturnsEmbedding() {
        // Arrange
        int port = OllamaStubServer.freePort();
        OllamaEmbeddingClient client = clientFor(port, Duration.ofSeconds(2));
        Throwable firstFailure = catchThrowable(() -> client.embed("izin"));

        try (OllamaStubServer stub = new OllamaStubServer(port)) {
            stub.respondWith(200, VALID_RESPONSE);

            // Act
            EmbeddingResult result = client.embed("izin");

            // Assert
            assertThat(firstFailure).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
            assertThat(result.embedding()).containsExactly(0.5f, -0.25f, 0.125f);
        }
    }

    @Test
    void givenOllamaReturnsEmbedding_whenEmbed_thenReturnsEmbeddingWithConfiguredModel() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, VALID_RESPONSE);
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            EmbeddingResult result = client.embed("izin");

            // Assert
            assertThat(result.embeddingModel()).isEqualTo("bge-m3");
            assertThat(result.embedding()).containsExactly(0.5f, -0.25f, 0.125f);
        }
    }

    @Test
    void givenOllamaReturnsNoEmbeddings_whenEmbed_thenThrowsEmbeddingUnavailable() {
        for (String body : List.of(
                "{\"model\":\"bge-m3\",\"embeddings\":[]}",
                "{\"model\":\"bge-m3\",\"embeddings\":[[]]}",
                "{\"model\":\"bge-m3\"}")) {
            try (OllamaStubServer stub = new OllamaStubServer()) {
                // Arrange
                stub.respondWith(200, body);
                OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

                // Act
                Throwable thrown = catchThrowable(() -> client.embed("izin"));

                // Assert
                assertThat(thrown).as(body).isInstanceOf(ServiceUnavailableException.class)
                        .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
            }
        }
    }

    @Test
    void givenTwoTexts_whenEmbedAll_thenPostsInputArrayAndReturnsVectorsInOrder() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[[0.5,0.25],[0.125,1.0]]}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            EmbeddingBatchResult result = client.embedAll(List.of("izin ışığı", "şifre"));

            // Assert
            assertThat(stub.requests()).hasSize(1);
            assertThat(stub.requests().get(0).path()).isEqualTo("/api/embed");
            assertThat(jsonMapper.readTree(stub.requests().get(0).body()))
                    .isEqualTo(jsonMapper.readTree("{\"model\":\"bge-m3\",\"input\":[\"izin ışığı\",\"şifre\"]}"));
            assertThat(result.embeddingModel()).isEqualTo("bge-m3");
            assertThat(result.embeddings()).hasSize(2);
            assertThat(result.embeddings().get(0)).containsExactly(0.5f, 0.25f);
            assertThat(result.embeddings().get(1)).containsExactly(0.125f, 1.0f);
        }
    }

    @Test
    void givenEmptyList_whenEmbedAll_thenReturnsEmptyWithoutCallingOllama() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            EmbeddingBatchResult result = client.embedAll(List.of());

            // Assert
            assertThat(result.embeddings()).isEmpty();
            assertThat(stub.requestCount()).isZero();
        }
    }

    @Test
    void givenOllamaReturnsFewerEmbeddingsThanInputs_whenEmbedAll_thenThrowsEmbeddingUnavailable() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[[0.5]]}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            Throwable thrown = catchThrowable(() -> client.embedAll(List.of("bir", "iki")));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
        }
    }

    @Test
    void givenOllamaReturnsEmptyVectorInBatch_whenEmbedAll_thenThrowsEmbeddingUnavailable() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(200, "{\"model\":\"bge-m3\",\"embeddings\":[[0.5],[]]}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            Throwable thrown = catchThrowable(() -> client.embedAll(List.of("bir", "iki")));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_UNAVAILABLE");
        }
    }

    @Test
    void givenOllamaAnswers404_whenEmbedAll_thenThrowsEmbeddingModelNotFound() {
        try (OllamaStubServer stub = new OllamaStubServer()) {
            // Arrange
            stub.respondWith(404, "{\"error\":\"model not found\"}");
            OllamaEmbeddingClient client = clientFor(stub.port(), Duration.ofSeconds(2));

            // Act
            Throwable thrown = catchThrowable(() -> client.embedAll(List.of("bir")));

            // Assert
            assertThat(thrown).isInstanceOf(ServiceUnavailableException.class)
                    .extracting("errorCode").isEqualTo("EMBEDDING_MODEL_NOT_FOUND");
        }
    }

    @Test
    void givenModelAndDimensions_whenModelTag_thenJoinedWithAtSign() {
        // Arrange
        EmbeddingProperties properties = new EmbeddingProperties(URI.create("http://127.0.0.1:9"), "bge-m3", 1024,
                Duration.ofSeconds(1), Duration.ofSeconds(1));

        // Act
        String tag = properties.modelTag();

        // Assert
        assertThat(tag).isEqualTo("bge-m3@1024");
    }
}

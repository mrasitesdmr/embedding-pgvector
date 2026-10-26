package com.embedding.pgvector.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/**
 * Framework düzeyindeki hataların (sayısal olmayan id, bilinmeyen yol, yanlış method ve içerik türü) gerçek
 * sunucuda RFC 9457 Problem Details olarak döndüğünü doğrular.
 */
class GlobalExceptionHandlerIntegrationTest extends FakeEmbeddingIntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/workspaces/abc",
            "/api/workspaces/42/sources/abc",
            "/api/workspaces/42/sources/10001/chunks/abc",
            "/api/workspaces/42/ingestion-jobs/abc",
            "/api/workspaces/99999999999999999999"})
    void givenNonNumericOrOverflowingPathId_whenGet_thenReturns400BadRequestProblem(String path) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        ApiTestClient.Response response = api().get(path);

        // Assert
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.contentType()).startsWith("application/problem+json");
        JsonNode body = json(response);
        assertThat(body.get("errorCode").asString()).isEqualTo("BAD_REQUEST");
        assertThat(body.get("type").asString()).isEqualTo("https://embedding-pgvector.example/errors/bad-request");
        assertThat(response.body()).doesNotContain("Exception").doesNotContain("at com.");
    }

    @Test
    void givenUnknownPath_whenGet_thenReturns404NotFoundProblem() {
        // Act
        ApiTestClient.Response response = api().get("/api/does-not-exist");

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(json(response).get("errorCode").asString()).isEqualTo("NOT_FOUND");
    }

    @Test
    void givenDeleteOnSource_whenSent_thenReturns405MethodNotAllowedProblem() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/workspaces/42/sources/10001")).DELETE().build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertThat(response.statusCode()).isEqualTo(405);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void givenPlainTextSearchBody_whenPost_thenReturns415UnsupportedMediaTypeProblem() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                + "/api/workspaces/42/search")).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("izin")).build();

        // Act
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        // Assert
        assertThat(response.statusCode()).isEqualTo(415);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(JSON.readTree(response.body()).get("errorCode").asString()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }
}

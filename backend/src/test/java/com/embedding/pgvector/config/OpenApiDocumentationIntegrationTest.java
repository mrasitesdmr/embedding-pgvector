package com.embedding.pgvector.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.embedding.pgvector.ApiTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.embedding.pgvector.support.PostgresIntegrationTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Çalışan uygulamanın OpenAPI belgesinde sürüm ve son içe alma işi alanlarının yer aldığını doğrular.
 */
class OpenApiDocumentationIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(node -> values.add(node.asString()));
        return values;
    }

    @Test
    void givenApplication_whenGetApiDocs_thenVersionAndLatestJob() {
        // Act
        ApiTestClient.Response response = new ApiTestClient(port).get("/v3/api-docs");
        JsonNode docs = JsonMapper.builder().build().readTree(response.body());
        JsonNode schemas = docs.get("components").get("schemas");

        // Assert
        assertThat(response.status()).isEqualTo(200);
        assertThat(docs.get("info").get("title").asString()).isEqualTo("Embedding ve pgvector API");
        assertThat(docs.get("info").get("version").asString()).isEqualTo("0.4.0");
        JsonNode problemProperties = schemas.get("ProblemDetail").get("properties");
        assertThat(problemProperties.has("errorCode")).isTrue();
        assertThat(problemProperties.has("violations")).isTrue();
        assertThat(problemProperties.has("properties")).isFalse();
        assertThat(texts(problemProperties.get("errorCode").get("enum"))).containsExactly(
                "VALIDATION_FAILED", "BAD_REQUEST", "NOT_FOUND", "METHOD_NOT_ALLOWED", "NOT_ACCEPTABLE",
                "UNSUPPORTED_MEDIA_TYPE", "EMBEDDING_UNAVAILABLE", "EMBEDDING_MODEL_NOT_FOUND", "INTERNAL_ERROR",
                "WORKSPACE_NOT_FOUND", "SOURCE_NOT_FOUND", "CHUNK_NOT_FOUND", "INGESTION_JOB_NOT_FOUND", "SOURCE_NAME_TAKEN",
                "SOURCE_LIMIT_REACHED", "SOURCE_KIND_MISMATCH", "INGESTION_IN_PROGRESS",
                "EMBEDDING_MODEL_MISMATCH", "FILE_TOO_LARGE", "UNSUPPORTED_FILE_TYPE",
                "UNSUPPORTED_FILE_ENCODING");
        assertThat(docs.get("paths").propertyNames()).containsExactlyInAnyOrder(
                "/api/embeddings", "/api/workspaces", "/api/workspaces/{workspaceId}",
                "/api/workspaces/{workspaceId}/sources", "/api/workspaces/{workspaceId}/sources/{sourceId}",
                "/api/workspaces/{workspaceId}/sources/{sourceId}/versions",
                "/api/workspaces/{workspaceId}/sources/{sourceId}/chunks",
                "/api/workspaces/{workspaceId}/ingestion-jobs/{jobId}",
                "/api/workspaces/{workspaceId}/sources/{sourceId}/chunks/{chunkId}",
                "/api/workspaces/{workspaceId}/search", "/api/workspaces/{workspaceId}/search-events",
                "/api/workspaces/{workspaceId}/search-events/stats",
                "/api/workspaces/{workspaceId}/search-events/popular-queries");
        assertThat(texts(problemProperties.get("errorCode").get("enum"))).hasSize(21);
        JsonNode latestJob = schemas.get("SourceResponse").get("properties").get("latestJob");
        assertThat(latestJob.get("anyOf").size()).isEqualTo(2);
        assertThat(latestJob.get("anyOf").get(0).get("$ref").asString())
                .isEqualTo("#/components/schemas/IngestionJobResponse");
        assertThat(latestJob.get("anyOf").get(1).get("type").asString()).isEqualTo("null");
        assertThat(latestJob.has("type")).isFalse();
        assertThat(texts(schemas.get("SourceResponse").get("required"))).doesNotContain("latestJob");
        assertThat(texts(schemas.get("SearchEventStatsResponse").get("required"))).containsExactlyInAnyOrder(
                "totalCount", "todayCount", "last7DaysCount", "zeroResultCount", "lowResultCount", "timeZone",
                "todayStart", "last7DaysStart");
        assertThat(texts(schemas.get("WorkspaceResponse").get("required"))).contains("embeddingModels");
        assertThat(texts(schemas.get("SearchHitResponse").get("required"))).contains("chunkNo");
        assertThat(schemas.get("SearchEventResponse").get("properties").has("limit")).isTrue();
        assertThat(schemas.has("SearchResultResponse")).isTrue();
        assertThat(schemas.has("IngestionJobResponse")).isTrue();
        assertThat(schemas.has("PageResponseSourceResponse")).isTrue();
        assertThat(texts(schemas.get("Violation").get("required"))).containsExactlyInAnyOrder("field", "message");
        assertThat(texts(schemas.get("EmbeddingResponse").get("required")))
                .containsExactlyInAnyOrder("embeddingModel", "dimensions", "embedding", "durationMs");
    }
}

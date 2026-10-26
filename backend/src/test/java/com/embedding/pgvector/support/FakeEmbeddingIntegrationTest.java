package com.embedding.pgvector.support;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sahte embedding istemcisiyle çalışan entegrasyon testlerinin ortak tabanıdır; istemciyi sıfırlar, işin
 * bitmesini bekleyen yardımcılar sunar.
 */
@Import(FakeEmbeddingConfiguration.class)
public abstract class FakeEmbeddingIntegrationTest extends PostgresIntegrationTest {

    protected static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    @Autowired
    protected FakeEmbeddingClient embeddingClient;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @BeforeEach
    void resetEmbeddingClient() {
        embeddingClient.reset();
    }

    @AfterEach
    void awaitIdleIngestion() {
        embeddingClient.release();
        Awaitility.await().atMost(20, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject(
                "SELECT count(*) FROM ingestion_jobs WHERE stage NOT IN ('DONE', 'FAILED')", Long.class) == 0);
    }

    protected JsonNode awaitFinishedJob(long workspaceId, long jobId) {
        return awaitJob(workspaceId, jobId, node -> {
            String stage = node.get("stage").asString();
            return stage.equals("DONE") || stage.equals("FAILED");
        });
    }

    protected JsonNode awaitJob(long workspaceId, long jobId, java.util.function.Predicate<JsonNode> condition) {
        java.util.concurrent.atomic.AtomicReference<JsonNode> last = new java.util.concurrent.atomic.AtomicReference<>();
        Awaitility.await().atMost(20, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS).until(() -> {
            JsonNode node = json(api().get("/api/workspaces/" + workspaceId + "/ingestion-jobs/" + jobId));
            last.set(node);
            return condition.test(node);
        });
        return last.get();
    }

    protected ApiTestClient.Response createSource(long workspaceId, String name, String fileName, byte[] content) {
        return api().postMultipart("/api/workspaces/" + workspaceId + "/sources",
                ApiTestClient.Part.field("name", name), ApiTestClient.Part.file("file", fileName, content));
    }

    protected ApiTestClient.Response uploadVersion(long workspaceId, long sourceId, String version, String fileName,
            byte[] content) {
        if (version == null) {
            return api().postMultipart("/api/workspaces/" + workspaceId + "/sources/" + sourceId + "/versions",
                    ApiTestClient.Part.file("file", fileName, content));
        }
        return api().postMultipart("/api/workspaces/" + workspaceId + "/sources/" + sourceId + "/versions",
                ApiTestClient.Part.file("file", fileName, content), ApiTestClient.Part.field("version", version));
    }

    protected JsonNode search(long workspaceId, String body) {
        ApiTestClient.Response response = api().postJson("/api/workspaces/" + workspaceId + "/search", body);
        if (response.status() != 200) {
            throw new AssertionError("Search failed: " + response.status() + " " + response.body());
        }
        return json(response);
    }

    protected ApiTestClient api() {
        return new ApiTestClient(port);
    }

    protected JsonNode json(ApiTestClient.Response response) {
        return JSON.readTree(response.body());
    }

    protected void insertWorkspace(long id, String name) {
        jdbcTemplate.update("INSERT INTO workspaces (id, name, created_at) VALUES (?, ?, now())", id, name);
    }

    protected void insertSource(long id, long workspaceId, String name, SourceKind kind, String version,
            String fileName, String status, int chunkCount, boolean synced) {
        jdbcTemplate.update("""
                INSERT INTO sources (id, workspace_id, name, name_key, kind, version, file_name, status, chunk_count,
                                     synced_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """, id, workspaceId, name, Source.toNameKey(name), kind.name(), version, fileName, status, chunkCount,
                synced ? java.sql.Timestamp.from(java.time.Instant.parse("2026-10-12T11:31:40Z")) : null);
    }

    protected long insertChunk(long workspaceId, long sourceId, int chunkNo, String heading, String content,
            float[] embedding, String modelTag) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO chunks (id, workspace_id, source_id, chunk_no, heading, content, token_count, embedding,
                                    embedding_model, created_at)
                VALUES (nextval('chunks_id_seq'), ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?, now())
                RETURNING id
                """, Long.class, workspaceId, sourceId, chunkNo, heading, content, (content.length() + 3) / 4,
                com.embedding.pgvector.search.service.VectorLiteral.format(embedding), modelTag);
    }

    protected long insertChunk(long workspaceId, long sourceId, int chunkNo, String heading, String content,
            float[] embedding) {
        return insertChunk(workspaceId, sourceId, chunkNo, heading, content, embedding, ChunkFixtures.MODEL_TAG);
    }
}

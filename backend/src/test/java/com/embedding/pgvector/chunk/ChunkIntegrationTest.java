package com.embedding.pgvector.chunk;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.VectorFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Tek parça getirme ucunu gerçek PostgreSQL ile doğrular; başka kaynağa ait parça, başka çalışma alanındaki kaynak,
 * bilinmeyen çalışma alanı ve yenilenmiş parça için 404 döner.
 */
class ChunkIntegrationTest extends FakeEmbeddingIntegrationTest {

    private long chunkOfFirstSource;
    private long chunkOfSecondSource;

    @BeforeEach
    void seed() {
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");
        insertSource(301L, 42L, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 1, true);
        insertSource(302L, 42L, "Kılavuz", SourceKind.PDF, "3.1", "k.pdf", "READY", 1, true);
        insertSource(1191L, 19L, "Kıyı SSS", SourceKind.WEB_CRAWL, null, null, "READY", 0, true);
        chunkOfFirstSource = insertChunk(42L, 301L, 7, "IZN-99 hata kodu", "IZN-99 hata kodu içeriği",
                VectorFixtures.unit(0));
        chunkOfSecondSource = insertChunk(42L, 302L, 0, "Kurulum", "İzni kur", VectorFixtures.unit(1));
    }

    @Test
    void givenChunkOfSource_whenGetChunk_thenReturnsChunk() {
        // Act
        var response = api().get("/api/workspaces/42/sources/301/chunks/" + chunkOfFirstSource);

        // Assert
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("chunkId").asLong()).isEqualTo(chunkOfFirstSource);
        assertThat(body.get("chunkNo").asInt()).isEqualTo(7);
        assertThat(body.get("heading").asString()).isEqualTo("IZN-99 hata kodu");
        assertThat(body.get("content").asString()).isEqualTo("IZN-99 hata kodu içeriği");
        assertThat(body.get("tokenCount").asInt()).isEqualTo(6);
        assertThat(body.get("embeddingModel").asString()).isEqualTo("bge-m3@1024");
        assertThat(body.has("embedding")).isFalse();
    }

    @Test
    void givenChunkOfOtherSource_whenGetChunk_thenReturns404ChunkNotFound() {
        // Act
        var response = api().get("/api/workspaces/42/sources/301/chunks/" + chunkOfSecondSource);

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("CHUNK_NOT_FOUND");
    }

    @Test
    void givenSourceOfOtherWorkspace_whenGetChunk_thenReturns404SourceNotFound() {
        // Act
        var response = api().get("/api/workspaces/42/sources/1191/chunks/" + chunkOfFirstSource);

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void givenUnknownWorkspace_whenGetChunk_thenReturns404WorkspaceNotFound() {
        // Act
        var response = api().get("/api/workspaces/999/sources/301/chunks/" + chunkOfFirstSource);

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenReplacedChunk_whenGetChunk_thenReturns404ChunkNotFound() {
        // Arrange
        jdbcTemplate.update("DELETE FROM chunks WHERE id = ?", chunkOfFirstSource);
        insertChunk(42L, 301L, 0, "Yeni sürüm", "yeni içerik", VectorFixtures.unit(2));

        // Act
        var response = api().get("/api/workspaces/42/sources/301/chunks/" + chunkOfFirstSource);

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("CHUNK_NOT_FOUND");
    }
}

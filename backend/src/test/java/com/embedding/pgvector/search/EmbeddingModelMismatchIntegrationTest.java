package com.embedding.pgvector.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.VectorFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * Kayıtlı parçalar başka embedding modeliyle üretilmişse uygulamanın uyarı yazıp yine de açıldığını
 * doğrular.
 */
@TestPropertySource(properties = "embedding.model=embeddinggemma")
@ExtendWith(OutputCaptureExtension.class)
class EmbeddingModelMismatchIntegrationTest extends FakeEmbeddingIntegrationTest {

    @Autowired
    private EmbeddingModelGuard embeddingModelGuard;

    @Test
    void givenStoredChunksWithOtherModel_whenApplicationReady_thenLogsWarnAndStarts(CapturedOutput output) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Yardım", SourceKind.WEB_CRAWL, null, null, "READY", 1, true);
        insertChunk(42L, 301L, 0, "Başlık", "izinkelime içerik", VectorFixtures.unit(0), "bge-m3@1024");

        // Act
        embeddingModelGuard.warnOnMismatch();
        var health = api().get("/actuator/health");
        var vector = api().postJson("/api/workspaces/42/search", "{\"query\":\"izinkelime\",\"mode\":\"VECTOR\"}");
        var hybrid = api().postJson("/api/workspaces/42/search", "{\"query\":\"izinkelime\",\"mode\":\"HYBRID\"}");
        var keyword = api().postJson("/api/workspaces/42/search", "{\"query\":\"izinkelime\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(output.getOut()).contains(
                "Embedding model mismatch configuredModel=embeddinggemma@1024 storedModel=bge-m3@1024 workspaceCount=1");
        assertThat(health.status()).isEqualTo(200);
        assertThat(vector.status()).isEqualTo(409);
        assertThat(json(vector).get("errorCode").asString()).isEqualTo("EMBEDDING_MODEL_MISMATCH");
        assertThat(hybrid.status()).isEqualTo(409);
        assertThat(keyword.status()).isEqualTo(200);
        assertThat(json(keyword).get("returnedCount").asInt()).isEqualTo(1);
        assertThat(embeddingClient.singleTexts()).isEmpty();
    }

    @Test
    void givenChunksWithConfiguredModel_whenWarnOnMismatch_thenNoWarning(CapturedOutput output) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Yardım", SourceKind.WEB_CRAWL, null, null, "READY", 1, true);
        insertChunk(42L, 301L, 0, "Başlık", "içerik", VectorFixtures.unit(0), "embeddinggemma@1024");

        // Act
        embeddingModelGuard.warnOnMismatch();

        // Assert
        assertThat(output.getOut()).doesNotContain("Embedding model mismatch configuredModel=embeddinggemma@1024 "
                + "storedModel=embeddinggemma@1024");
    }
}

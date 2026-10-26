package com.embedding.pgvector.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.SampleFiles;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Yapılandırılan boyut 1024 olmayan bir modelle çalışılırken (kolon sabit 1024) arama ve yüklemenin SQL
 * hatasıyla 500 yerine model uyuşmazlığıyla bittiğini doğrular.
 */
@TestPropertySource(properties = "embedding.dimensions=768")
class EmbeddingDimensionsIntegrationTest extends FakeEmbeddingIntegrationTest {

    @Test
    void givenModelReturns768Dimensions_whenVectorSearchAndUpload_thenUploadFailsAsModelMismatchNotInternalError() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        embeddingClient.useDimensions(768);

        // Act
        ApiTestClient.Response search = api().postJson("/api/workspaces/42/search",
                "{\"query\":\"izin\",\"mode\":\"VECTOR\"}");
        ApiTestClient.Response accepted = createSource(42L, "Yeni", "yeni.md", SampleFiles.utf8("# a\nmetin"));
        JsonNode job = awaitFinishedJob(42L, json(accepted).get("jobId").asLong());

        // Assert
        assertThat(search.status()).isLessThan(500);
        assertThat(job.get("stage").asString()).isEqualTo("FAILED");
        assertThat(job.get("errorCode").asString()).isEqualTo("EMBEDDING_MODEL_MISMATCH");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isZero();
    }
}

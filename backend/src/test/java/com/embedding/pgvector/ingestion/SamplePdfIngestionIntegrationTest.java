package com.embedding.pgvector.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Örnek el kitabı PDF'inin içe alınıp işin tamamlandığını ve parça üretildiğini gerçek PostgreSQL ile
 * doğrular.
 */
class SamplePdfIngestionIntegrationTest extends FakeEmbeddingIntegrationTest {

    private static final Path SAMPLE = Path.of("..", "ornek-dokuman", "Atlaskap-Lojistik-Surec-El-Kitabi.pdf");

    @Test
    void givenSampleHandbookPdf_whenCreateSource_thenJobDoneWithChunks() throws IOException {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        byte[] pdf = Files.readAllBytes(SAMPLE);

        // Act
        ApiTestClient.Response created = createSource(42L, "Atlaskap-Lojistik-Surec-El-Kitabi",
                "Atlaskap-Lojistik-Surec-El-Kitabi.pdf", pdf);
        JsonNode job = awaitFinishedJob(42L, json(created).get("jobId").asLong());

        // Assert
        assertThat(created.status()).isEqualTo(202);
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(job.get("errorCode").isNull()).isTrue();
        long sourceId = json(created).get("sourceId").asLong();
        Integer chunkCount = jdbcTemplate.queryForObject("SELECT chunk_count FROM sources WHERE id = ?",
                Integer.class, sourceId);
        List<String> contents = jdbcTemplate.queryForList("SELECT content FROM chunks WHERE source_id = ?",
                String.class, sourceId);
        assertThat(chunkCount).isGreaterThanOrEqualTo(22).isEqualTo(contents.size());
        String all = String.join("\n", contents);
        assertThat(all).contains("Yıllık izin").contains("Masraf").contains("Parola");
        assertThat(contents).allSatisfy(content -> assertThat(content.length()).isLessThanOrEqualTo(2000));
    }
}

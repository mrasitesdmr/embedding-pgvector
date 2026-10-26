package com.embedding.pgvector.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import com.embedding.pgvector.ingestion.service.IngestionRecovery;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.SampleFiles;
import com.embedding.pgvector.support.VectorFixtures;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Dosya yüklemeden parçaların yazılmasına kadar içe alma akışını gerçek PostgreSQL ve sahte embedding
 * istemcisiyle doğrular.
 */
class IngestionIntegrationTest extends FakeEmbeddingIntegrationTest {

    private static final String MARKDOWN = "# Kurulum\nİzni prize tak.\n# Wi-Fi\nAğ adını değiştir.\n# Sorun\nIşık yanmıyorsa kabloyu kontrol et.";

    @Autowired
    private IngestionRecovery ingestionRecovery;

    private long sourceCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM sources", Long.class);
    }

    private long jobCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM ingestion_jobs", Long.class);
    }

    private void seedPdfSourceWithFiveChunks() {
        insertWorkspace(42L, "Belgelerim");
        insertSource(302L, 42L, "Kılavuz", SourceKind.PDF, "3.1", "v3.1.pdf", "READY", 5, true);
        for (int index = 0; index < 5; index++) {
            insertChunk(42L, 302L, index, "eski başlık " + index, "eskikelime sayfa " + index,
                    VectorFixtures.unit(index));
        }
    }

    @Test
    void givenMarkdownFile_whenCreateSourceAndPollJob_thenSourceReadyWithOrderedChunks() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        ApiTestClient.Response accepted = createSource(42L, "Yardım notları", "yardim.md", SampleFiles.utf8(MARKDOWN));
        JsonNode queued = json(accepted);
        JsonNode job = awaitFinishedJob(42L, queued.get("jobId").asLong());
        long sourceId = job.get("sourceId").asLong();
        JsonNode source = json(api().get("/api/workspaces/42/sources/" + sourceId));
        JsonNode chunks = json(api().get("/api/workspaces/42/sources/" + sourceId + "/chunks"));

        // Assert
        assertThat(accepted.status()).isEqualTo(202);
        assertThat(queued.get("stage").asString()).isEqualTo("QUEUED");
        assertThat(queued.get("statusUrl").asString()).isEqualTo("/api/workspaces/42/ingestion-jobs/" + queued.get("jobId").asLong());
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(job.get("progress").asDouble()).isEqualTo(1.0);
        assertThat(job.get("embeddedChunks").asInt()).isEqualTo(3);
        assertThat(job.get("totalChunks").asInt()).isEqualTo(3);
        assertThat(job.get("startedAt").isNull()).isFalse();
        assertThat(job.get("finishedAt").isNull()).isFalse();
        assertThat(job.get("errorCode").isNull()).isTrue();
        assertThat(sourceId).isGreaterThanOrEqualTo(10001L);
        assertThat(source.get("status").asString()).isEqualTo("READY");
        assertThat(source.get("chunkCount").asInt()).isEqualTo(3);
        assertThat(source.get("version").isNull()).isTrue();
        assertThat(source.get("fileName").asString()).isEqualTo("yardim.md");
        assertThat(source.get("kind").asString()).isEqualTo("MARKDOWN");
        assertThat(source.get("syncedAt").isNull()).isFalse();
        assertThat(chunks.get("content")).hasSize(3);
        for (int index = 0; index < 3; index++) {
            assertThat(chunks.get("content").get(index).get("chunkNo").asInt()).isEqualTo(index);
            assertThat(chunks.get("content").get(index).get("embeddingModel").asString()).isEqualTo("bge-m3@1024");
        }
        assertThat(chunks.get("content").get(0).get("heading").asString()).isEqualTo("Kurulum");
        assertThat(chunks.get("content").get(1).get("heading").asString()).isEqualTo("Wi-Fi");
        assertThat(chunks.get("content").get(2).get("heading").asString()).isEqualTo("Sorun");
        assertThat(embeddingClient.batchSizes()).containsExactly(3);
    }

    @Test
    void givenCsvAndPdfFiles_whenCreateSource_thenBothParsedAndIngested() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        byte[] csv = SampleFiles.utf8("Kod;Açıklama\nTS-104;Fiber sinyali zayıf\nTS-105;İzin ısındı");
        byte[] pdf = SampleFiles.pdf("Baslik bir\ngovde bir", "", "Baslik uc\ngovde uc");

        // Act
        long csvJob = json(createSource(42L, "Hata kodları", "hata-kodlari.csv", csv)).get("jobId").asLong();
        long pdfJob = json(createSource(42L, "Kılavuz", "kilavuz.pdf", pdf)).get("jobId").asLong();
        JsonNode csvResult = awaitFinishedJob(42L, csvJob);
        JsonNode pdfResult = awaitFinishedJob(42L, pdfJob);

        // Assert
        assertThat(csvResult.get("stage").asString()).isEqualTo("DONE");
        assertThat(csvResult.get("totalChunks").asInt()).isEqualTo(2);
        assertThat(pdfResult.get("stage").asString()).isEqualTo("DONE");
        assertThat(pdfResult.get("totalChunks").asInt()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("SELECT heading FROM chunks ORDER BY heading", String.class))
                .containsExactlyInAnyOrder("TS-104", "TS-105", "s. 1 · Baslik bir", "s. 3 · Baslik uc");
    }

    @Test
    void givenSourceWithOldChunks_whenUploadNewVersion_thenOldChunksReplacedAndSearchSeesOnlyNew() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Notlar", SourceKind.MARKDOWN, "3.1", "eski.md", "READY", 3, true);
        List<Long> oldIds = List.of(
                insertChunk(42L, 301L, 0, "Eski bir", "eskikelime birinci", VectorFixtures.unit(0)),
                insertChunk(42L, 301L, 1, "Eski iki", "eskikelime ikinci", VectorFixtures.unit(1)),
                insertChunk(42L, 301L, 2, "Eski üç", "eskikelime üçüncü", VectorFixtures.unit(2)));

        // Act
        ApiTestClient.Response accepted = uploadVersion(42L, 301L, "3.2", "yeni.md",
                SampleFiles.utf8("# Yeni\nyenikelime burada"));
        JsonNode job = awaitFinishedJob(42L, json(accepted).get("jobId").asLong());
        JsonNode source = json(api().get("/api/workspaces/42/sources/301"));
        JsonNode newHits = search(42L, "{\"query\":\"yenikelime\",\"mode\":\"KEYWORD\"}");
        JsonNode oldHits = search(42L, "{\"query\":\"eskikelime\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(accepted.status()).isEqualTo(202);
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(job.get("version").asString()).isEqualTo("3.2");
        assertThat(source.get("version").asString()).isEqualTo("3.2");
        assertThat(source.get("fileName").asString()).isEqualTo("yeni.md");
        assertThat(source.get("chunkCount").asInt()).isEqualTo(1);
        assertThat(source.get("status").asString()).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForList("SELECT id FROM chunks WHERE source_id = 301", Long.class))
                .hasSize(1).doesNotContainAnyElementsOf(oldIds);
        assertThat(newHits.get("returnedCount").asInt()).isEqualTo(1);
        assertThat(oldHits.get("returnedCount").asInt()).isZero();
    }

    @Test
    void givenVersionLabelBlank_whenUploadNewVersion_thenSourceVersionBecomesNull() {
        // Arrange
        seedPdfSourceWithFiveChunks();

        // Act
        JsonNode job = awaitFinishedJob(42L, json(uploadVersion(42L, 302L, "   ", "yeni.pdf",
                SampleFiles.pdf("Baslik\nmetin"))).get("jobId").asLong());

        // Assert
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(json(api().get("/api/workspaces/42/sources/302")).get("version").isNull()).isTrue();
    }

    @Test
    void givenJobBlockedInEmbedding_whenSearchAndPoll_thenOldChunksVisibleAndProgressReported() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Notlar", SourceKind.MARKDOWN, "3.1", "eski.md", "READY", 1, true);
        insertChunk(42L, 301L, 0, "Eski", "eskikelime burada", VectorFixtures.unit(0));
        embeddingClient.blockUntilReleased();

        // Act
        long jobId = json(uploadVersion(42L, 301L, "3.2", "yeni.md", SampleFiles.utf8("# Yeni\nyenikelime")))
                .get("jobId").asLong();
        boolean reachedEmbedding = embeddingClient.awaitBlockedCall(10);
        JsonNode running = awaitJob(42L, jobId, node -> node.get("stage").asString().equals("EMBEDDING"));
        JsonNode source = json(api().get("/api/workspaces/42/sources/301"));
        JsonNode duringHits = search(42L, "{\"query\":\"eskikelime\",\"mode\":\"KEYWORD\"}");
        embeddingClient.release();
        JsonNode finished = awaitFinishedJob(42L, jobId);
        JsonNode afterHits = search(42L, "{\"query\":\"eskikelime\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(reachedEmbedding).isTrue();
        assertThat(running.get("progress").asDouble()).isEqualTo(0.1);
        assertThat(running.get("totalChunks").asInt()).isEqualTo(1);
        assertThat(running.get("embeddedChunks").asInt()).isZero();
        assertThat(source.get("status").asString()).isEqualTo("INGESTING");
        assertThat(source.get("version").asString()).isEqualTo("3.1");
        assertThat(duringHits.get("returnedCount").asInt()).isEqualTo(1);
        assertThat(finished.get("stage").asString()).isEqualTo("DONE");
        assertThat(afterHits.get("returnedCount").asInt()).isZero();
    }

    @Test
    void givenActiveJob_whenUploadAnotherVersion_thenReturns409AndOtherSourceAccepted() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Birinci", SourceKind.MARKDOWN, null, "a.md", "READY", 0, true);
        insertSource(302L, 42L, "İkinci", SourceKind.MARKDOWN, null, "b.md", "READY", 0, true);
        embeddingClient.blockUntilReleased();
        ApiTestClient.Response first = uploadVersion(42L, 301L, null, "a.md", SampleFiles.utf8("# a\nb"));
        embeddingClient.awaitBlockedCall(10);

        // Act
        ApiTestClient.Response second = uploadVersion(42L, 301L, null, "a2.md", SampleFiles.utf8("# a\nb"));
        ApiTestClient.Response other = uploadVersion(42L, 302L, null, "b.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(first.status()).isEqualTo(202);
        assertThat(second.status()).isEqualTo(409);
        assertThat(json(second).get("errorCode").asString()).isEqualTo("INGESTION_IN_PROGRESS");
        assertThat(other.status()).isEqualTo(202);
        assertThat(jobCount()).isEqualTo(2);
    }

    @Test
    void givenTwoParallelUploadsToSameSource_whenSubmitted_thenExactlyOneAcceptedAndOneConflict() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Birinci", SourceKind.MARKDOWN, null, "a.md", "READY", 0, true);
        embeddingClient.blockUntilReleased();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Act
        List<Future<ApiTestClient.Response>> futures = Stream.of("a1.md", "a2.md").map(name -> executor.submit(() -> {
            start.await();
            return uploadVersion(42L, 301L, null, name, SampleFiles.utf8("# a\nb"));
        })).toList();
        start.countDown();
        List<ApiTestClient.Response> responses = List.of(futures.get(0).get(), futures.get(1).get());
        executor.shutdown();

        // Assert
        assertThat(responses).extracting(ApiTestClient.Response::status).containsExactlyInAnyOrder(202, 409);
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void givenEmbeddingUnavailable_whenUploadNewVersion_thenJobFailedAndSourceKeepsOldVersionAndChunks() {
        // Arrange
        seedPdfSourceWithFiveChunks();
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));

        // Act
        JsonNode job = awaitFinishedJob(42L, json(uploadVersion(42L, 302L, "3.2", "v3.2.pdf",
                SampleFiles.pdf("Baslik\nmetin"))).get("jobId").asLong());
        JsonNode source = json(api().get("/api/workspaces/42/sources/302"));
        JsonNode hits = search(42L, "{\"query\":\"eskikelime\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(job.get("stage").asString()).isEqualTo("FAILED");
        assertThat(job.get("errorCode").asString()).isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(job.get("progress").asDouble()).isEqualTo(0.1);
        assertThat(source.get("status").asString()).isEqualTo("READY");
        assertThat(source.get("version").asString()).isEqualTo("3.1");
        assertThat(source.get("chunkCount").asInt()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks WHERE source_id = 302", Integer.class))
                .isEqualTo(5);
        assertThat(hits.get("returnedCount").asInt()).isEqualTo(5);
    }

    @Test
    void givenEmbeddingUnavailable_whenCreateSource_thenSourceFailedWithoutChunks() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));

        // Act
        JsonNode job = awaitFinishedJob(42L, json(createSource(42L, "Yeni", "yeni.md", SampleFiles.utf8(MARKDOWN)))
                .get("jobId").asLong());
        JsonNode source = json(api().get("/api/workspaces/42/sources/" + job.get("sourceId").asLong()));

        // Assert
        assertThat(job.get("errorCode").asString()).isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(source.get("status").asString()).isEqualTo("FAILED");
        assertThat(source.get("chunkCount").asInt()).isZero();
        assertThat(source.get("syncedAt").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isZero();
    }

    @Test
    void givenFailedFirstIngestion_whenUploadAgainWithWorkingEmbedding_thenSourceBecomesReady() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));
        JsonNode failed = awaitFinishedJob(42L, json(createSource(42L, "Yeni", "yeni.md",
                SampleFiles.utf8(MARKDOWN))).get("jobId").asLong());
        long sourceId = failed.get("sourceId").asLong();
        embeddingClient.failWith(null);

        // Act
        JsonNode retry = awaitFinishedJob(42L, json(uploadVersion(42L, sourceId, null, "yeni.md",
                SampleFiles.utf8(MARKDOWN))).get("jobId").asLong());

        // Assert
        assertThat(retry.get("stage").asString()).isEqualTo("DONE");
        assertThat(json(api().get("/api/workspaces/42/sources/" + sourceId)).get("status").asString())
                .isEqualTo("READY");
    }

    @Test
    void givenPdfWithoutText_whenCreateSource_thenJobFailedNoTextExtracted() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        JsonNode job = awaitFinishedJob(42L, json(createSource(42L, "Taranmış", "taranmis.pdf",
                SampleFiles.pdf("", ""))).get("jobId").asLong());
        var health = api().get("/actuator/health");

        // Assert
        assertThat(job.get("stage").asString()).isEqualTo("FAILED");
        assertThat(job.get("errorCode").asString()).isEqualTo("NO_TEXT_EXTRACTED");
        assertThat(embeddingClient.batchSizes()).isEmpty();
        assertThat(health.status()).isEqualTo(200);
        assertThat(json(health).get("status").asString()).isEqualTo("UP");
    }

    @Test
    void givenCorruptPdfWithHeader_whenCreateSource_thenJobFailedFileUnreadable() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        byte[] corrupt = new byte[300];
        Arrays.fill(corrupt, (byte) 7);
        System.arraycopy("%PDF-1.7\n".getBytes(), 0, corrupt, 0, 9);

        // Act
        JsonNode job = awaitFinishedJob(42L, json(createSource(42L, "Bozuk", "bozuk.pdf", corrupt))
                .get("jobId").asLong());

        // Assert
        assertThat(job.get("errorCode").asString()).isEqualTo("FILE_UNREADABLE");
    }

    static Stream<Arguments> rejectedUploads() {
        byte[] huge = new byte[25 * 1024 * 1024];
        System.arraycopy("%PDF-".getBytes(), 0, huge, 0, 5);
        return Stream.of(
                Arguments.of("rapor.docx", "docx-bytes".getBytes(), 415, "UNSUPPORTED_FILE_TYPE"),
                Arguments.of("fake.pdf", "merhaba".getBytes(), 415, "UNSUPPORTED_FILE_TYPE"),
                Arguments.of("eski.txt", SampleFiles.windows1254("şifre"), 415, "UNSUPPORTED_FILE_ENCODING"),
                Arguments.of("buyuk.pdf", huge, 413, "FILE_TOO_LARGE"),
                Arguments.of("bos.md", new byte[0], 400, "VALIDATION_FAILED"));
    }

    @ParameterizedTest
    @MethodSource("rejectedUploads")
    void givenRejectedUpload_whenCreateSource_thenProblemDetailAndNoJob(String fileName, byte[] content, int status,
            String errorCode) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        ApiTestClient.Response response = createSource(42L, "Reddedilen", fileName, content);

        // Assert
        assertThat(response.status()).isEqualTo(status);
        JsonNode body = json(response);
        assertThat(body.get("errorCode").asString()).isEqualTo(errorCode);
        assertThat(body.get("type").asString()).isEqualTo(
                "https://embedding-pgvector.example/errors/" + errorCode.toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        assertThat(body.get("status").asInt()).isEqualTo(status);
        assertThat(body.get("title").asString()).isNotBlank();
        assertThat(body.get("detail").asString()).doesNotContain("merhaba").doesNotContain("docx-bytes");
        assertThat(sourceCount()).isZero();
        assertThat(jobCount()).isZero();
    }

    @Test
    void givenFileOver20Mb_whenCreateSource_thenTitleIsContentTooLarge() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        byte[] huge = new byte[21 * 1024 * 1024 + 1024];
        System.arraycopy("%PDF-".getBytes(), 0, huge, 0, 5);

        // Act
        JsonNode body = json(createSource(42L, "Büyük", "buyuk.pdf", huge));

        // Assert
        assertThat(body.get("title").asString()).isEqualTo("Content Too Large");
        assertThat(body.get("status").asInt()).isEqualTo(413);
    }

    @Test
    void givenEmptyFile_whenCreateSource_thenViolationNamesFilePart() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        JsonNode body = json(createSource(42L, "Boş", "bos.md", new byte[0]));

        // Assert
        assertThat(body.get("violations").get(0).get("field").asString()).isEqualTo("file");
        assertThat(body.get("violations").get(0).get("message").asString()).isEqualTo("must not be empty");
    }

    @Test
    void givenFileKindDifferentFromSourceKind_whenUploadSourceVersion_thenReturns409SourceKindMismatch() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(302L, 42L, "Kılavuz", SourceKind.PDF, "3.1", "v.pdf", "READY", 0, true);
        insertSource(301L, 42L, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 0, true);

        // Act
        ApiTestClient.Response pdfWithCsv = uploadVersion(42L, 302L, null, "kodlar.csv", SampleFiles.utf8("a,b\n1,2"));
        ApiTestClient.Response crawlWithMarkdown = uploadVersion(42L, 301L, null, "notlar.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(pdfWithCsv.status()).isEqualTo(409);
        assertThat(json(pdfWithCsv).get("errorCode").asString()).isEqualTo("SOURCE_KIND_MISMATCH");
        assertThat(crawlWithMarkdown.status()).isEqualTo(409);
        assertThat(json(crawlWithMarkdown).get("errorCode").asString()).isEqualTo("SOURCE_KIND_MISMATCH");
        assertThat(jobCount()).isZero();
    }

    @Test
    void givenWorkspaceChunksWithOtherModel_whenUpload_thenReturns409EmbeddingModelMismatch() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Notlar", SourceKind.MARKDOWN, null, "a.md", "READY", 1, true);
        insertChunk(42L, 301L, 0, "h", "c", VectorFixtures.unit(0), "other@1024");

        // Act
        ApiTestClient.Response create = createSource(42L, "Yeni", "y.md", SampleFiles.utf8("# a\nb"));
        ApiTestClient.Response version = uploadVersion(42L, 301L, null, "y.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(create.status()).isEqualTo(409);
        assertThat(json(create).get("errorCode").asString()).isEqualTo("EMBEDDING_MODEL_MISMATCH");
        assertThat(version.status()).isEqualTo(409);
        assertThat(json(version).get("errorCode").asString()).isEqualTo("EMBEDDING_MODEL_MISMATCH");
        assertThat(jobCount()).isZero();
        assertThat(sourceCount()).isEqualTo(1);
    }

    @Test
    void givenJobsLeftActive_whenRecoveryRuns_thenJobsFailedInterruptedAndSourcesRestored() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Eski sürümlü", SourceKind.MARKDOWN, "1", "a.md", "INGESTING", 2, true);
        insertSource(302L, 42L, "İlk sürüm", SourceKind.MARKDOWN, null, "b.md", "INGESTING", 0, false);
        jdbcTemplate.update("""
                INSERT INTO ingestion_jobs (workspace_id, source_id, file_name, file_size_bytes, stage, progress,
                                            created_at)
                VALUES (42, 301, 'a.md', 10, 'EMBEDDING', 0.1, now()), (42, 302, 'b.md', 10, 'QUEUED', 0, now())
                """);

        // Act
        ingestionRecovery.recoverInterruptedJobs();

        // Assert
        assertThat(jdbcTemplate.queryForList("SELECT DISTINCT stage || ':' || error_code FROM ingestion_jobs",
                String.class)).containsExactly("FAILED:INGESTION_INTERRUPTED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sources WHERE id = 301", String.class))
                .isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sources WHERE id = 302", String.class))
                .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ingestion_jobs WHERE finished_at IS NULL",
                Integer.class)).isZero();
    }

    @Test
    void givenFinishedJobs_whenRecoveryRuns_thenNothingChanges() {
        // Arrange
        seedPdfSourceWithFiveChunks();
        jdbcTemplate.update("""
                INSERT INTO ingestion_jobs (workspace_id, source_id, file_name, file_size_bytes, stage, progress,
                                            created_at)
                VALUES (42, 302, 'a.pdf', 10, 'DONE', 1, now())
                """);

        // Act
        ingestionRecovery.recoverInterruptedJobs();

        // Assert
        assertThat(jdbcTemplate.queryForObject("SELECT stage FROM ingestion_jobs", String.class)).isEqualTo("DONE");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sources WHERE id = 302", String.class))
                .isEqualTo("READY");
    }

    @Test
    void givenLargeFileWithManyChunks_whenCreateSource_thenEmbeddedInBatchesOf96AndAllWritten() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        StringBuilder markdown = new StringBuilder();
        for (int index = 0; index < 214; index++) {
            markdown.append("# Başlık ").append(index).append("\nmetin ").append(index).append('\n');
        }

        // Act
        JsonNode job = awaitFinishedJob(42L, json(createSource(42L, "Büyük", "buyuk.md",
                SampleFiles.utf8(markdown.toString()))).get("jobId").asLong());

        // Assert
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(job.get("embeddedChunks").asInt()).isEqualTo(214);
        assertThat(embeddingClient.batchSizes()).containsExactly(96, 96, 22);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isEqualTo(214);
        assertThat(jdbcTemplate.queryForObject("SELECT count(DISTINCT chunk_no) FROM chunks", Integer.class))
                .isEqualTo(214);
    }

    @Test
    void givenTurkishFileName_whenCreateSource_thenFileNameKeptInJobAndSource() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        ApiTestClient.Response accepted = createSource(42L, "Çalışan İzin Notları", "Çalışan İzin Notları.md",
                SampleFiles.utf8(MARKDOWN));
        JsonNode queued = json(accepted);
        JsonNode job = awaitFinishedJob(42L, queued.get("jobId").asLong());
        JsonNode source = json(api().get("/api/workspaces/42/sources/" + job.get("sourceId").asLong()));

        // Assert
        assertThat(accepted.status()).isEqualTo(202);
        assertThat(queued.get("fileName").asString()).isEqualTo("Çalışan İzin Notları.md");
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(source.get("name").asString()).isEqualTo("Çalışan İzin Notları");
        assertThat(source.get("fileName").asString()).isEqualTo("Çalışan İzin Notları.md");
        assertThat(jdbcTemplate.queryForObject("SELECT file_name FROM sources", String.class))
                .isEqualTo("Çalışan İzin Notları.md");
        assertThat(jdbcTemplate.queryForObject("SELECT file_name FROM ingestion_jobs", String.class))
                .isEqualTo("Çalışan İzin Notları.md");
    }

    @Test
    void givenThreeParallelCreatesWithDifferentNames_whenAllFinished_thenThreeReadySourcesWithOwnChunks()
            throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        // Act
        List<Future<ApiTestClient.Response>> futures = Stream.of(1, 2, 3).map(sections -> executor.submit(() -> {
            start.await();
            StringBuilder markdown = new StringBuilder();
            for (int index = 0; index < sections; index++) {
                markdown.append("# Bölüm ").append(index).append("\nmetin ").append(sections).append('-')
                        .append(index).append('\n');
            }
            return createSource(42L, "Eşzamanlı " + sections, "e" + sections + ".md",
                    SampleFiles.utf8(markdown.toString()));
        })).toList();
        start.countDown();
        List<ApiTestClient.Response> responses = List.of(futures.get(0).get(), futures.get(1).get(),
                futures.get(2).get());
        executor.shutdown();
        List<JsonNode> jobs = responses.stream().map(response -> awaitFinishedJob(42L,
                json(response).get("jobId").asLong())).toList();

        // Assert
        assertThat(responses).extracting(ApiTestClient.Response::status).containsOnly(202);
        assertThat(jobs).extracting(job -> job.get("stage").asString()).containsOnly("DONE");
        assertThat(jobs).extracting(job -> job.get("sourceId").asLong()).doesNotHaveDuplicates();
        assertThat(jdbcTemplate.queryForList("""
                SELECT s.name || ':' || s.chunk_count || ':' || (SELECT count(*) FROM chunks c WHERE c.source_id = s.id)
                FROM sources s ORDER BY s.name""", String.class))
                .containsExactly("Eşzamanlı 1:1:1", "Eşzamanlı 2:2:2", "Eşzamanlı 3:3:3");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isEqualTo(6);
    }

    @Test
    void givenJobSubmittedInThisRun_whenRecoveryRuns_thenJobFinishesDone() throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        embeddingClient.blockUntilReleased();
        long jobId = json(createSource(42L, "Süren", "suren.md", SampleFiles.utf8(MARKDOWN))).get("jobId").asLong();
        embeddingClient.awaitBlockedCall(10);
        awaitJob(42L, jobId, node -> node.get("stage").asString().equals("EMBEDDING"));

        // Act
        ingestionRecovery.recoverInterruptedJobs();
        embeddingClient.release();
        JsonNode job = awaitFinishedJob(42L, jobId);

        // Assert
        assertThat(job.get("stage").asString()).isEqualTo("DONE");
        assertThat(job.get("errorCode").isNull()).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM sources", String.class)).isEqualTo("READY");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isEqualTo(3);
    }

    static Stream<Arguments> textlessFiles() {
        return Stream.of(
                Arguments.of("bos.md", "   \n\n  "),
                Arguments.of("baslik.csv", "ad,soyad\n"));
    }

    @ParameterizedTest
    @MethodSource("textlessFiles")
    void givenWhitespaceOnlyMarkdownOrHeaderOnlyCsv_whenCreateSource_thenJobFailedNoTextExtractedAndNoChunks(
            String fileName, String content) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        ApiTestClient.Response accepted = createSource(42L, "Metinsiz", fileName, SampleFiles.utf8(content));
        JsonNode job = awaitFinishedJob(42L, json(accepted).get("jobId").asLong());
        JsonNode source = json(api().get("/api/workspaces/42/sources/" + job.get("sourceId").asLong()));

        // Assert
        assertThat(accepted.status()).isEqualTo(202);
        assertThat(job.get("stage").asString()).isEqualTo("FAILED");
        assertThat(job.get("errorCode").asString()).isEqualTo("NO_TEXT_EXTRACTED");
        assertThat(source.get("status").asString()).isEqualTo("FAILED");
        assertThat(source.get("chunkCount").asInt()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM chunks", Integer.class)).isZero();
    }
}

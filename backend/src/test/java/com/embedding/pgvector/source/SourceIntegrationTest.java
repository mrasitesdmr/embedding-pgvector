package com.embedding.pgvector.source;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.exception.ServiceUnavailableException;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.SampleFiles;
import com.embedding.pgvector.support.VectorFixtures;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import com.embedding.pgvector.ApiTestClient;

/**
 * Kaynak uçlarını gerçek PostgreSQL ve sahte embedding istemcisiyle doğrular: listeleme, getirme, çakışma
 * ve limit hataları, eşzamanlı oluşturma.
 */
@ExtendWith(OutputCaptureExtension.class)
class SourceIntegrationTest extends FakeEmbeddingIntegrationTest {

    private void seedPrimary() {
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");
        insertSource(301L, 42L, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 0, true);
        insertSource(302L, 42L, "Atlaskap-Lojistik-Surec-El-Kitabi", SourceKind.PDF, "3.1",
                "Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf", "READY", 5, true);
    }

    @Test
    void givenSeededPdfSource_whenListSources_thenReturnsAllListedFields() {
        // Arrange
        seedPrimary();

        // Act
        JsonNode page = json(api().get("/api/workspaces/42/sources"));

        // Assert
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        JsonNode manual = page.get("content").get(1);
        assertThat(manual.get("sourceId").asLong()).isEqualTo(302L);
        assertThat(manual.get("workspaceId").asLong()).isEqualTo(42L);
        assertThat(manual.get("name").asString()).isEqualTo("Atlaskap-Lojistik-Surec-El-Kitabi");
        assertThat(manual.get("kind").asString()).isEqualTo("PDF");
        assertThat(manual.get("version").asString()).isEqualTo("3.1");
        assertThat(manual.get("chunkCount").asInt()).isEqualTo(5);
        assertThat(manual.get("status").asString()).isEqualTo("READY");
        assertThat(manual.get("syncedAt").asString()).isEqualTo("2026-10-12T11:31:40Z");
        assertThat(manual.get("fileName").asString()).isEqualTo("Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf");
        assertThat(page.get("content").get(0).get("sourceId").asLong()).isEqualTo(301L);
        assertThat(page.get("content").get(0).get("version").isNull()).isTrue();
    }

    @Test
    void givenSource_whenGetSource_thenReturnsSourceAndUnknownIs404() {
        // Arrange
        seedPrimary();

        // Act
        var found = api().get("/api/workspaces/42/sources/302");
        var missing = api().get("/api/workspaces/42/sources/999");
        var missingWorkspace = api().get("/api/workspaces/999/sources");

        // Assert
        assertThat(found.status()).isEqualTo(200);
        assertThat(json(found).get("name").asString()).isEqualTo("Atlaskap-Lojistik-Surec-El-Kitabi");
        assertThat(missing.status()).isEqualTo(404);
        assertThat(json(missing).get("errorCode").asString()).isEqualTo("SOURCE_NOT_FOUND");
        assertThat(missingWorkspace.status()).isEqualTo(404);
        assertThat(json(missingWorkspace).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    static Stream<String[]> crossWorkspaceRequests() {
        return Stream.of(
                new String[] {"GET", "/api/workspaces/19/sources/301", "SOURCE_NOT_FOUND"},
                new String[] {"GET", "/api/workspaces/19/sources/301/chunks", "SOURCE_NOT_FOUND"},
                new String[] {"POST", "/api/workspaces/19/sources/301/versions", "SOURCE_NOT_FOUND"});
    }

    @ParameterizedTest
    @MethodSource("crossWorkspaceRequests")
    void givenSourceOfOtherWorkspace_whenAccessThroughOwnWorkspace_thenReturns404(String method, String path,
            String errorCode) {
        // Arrange
        seedPrimary();

        // Act
        var response = method.equals("GET") ? api().get(path)
                : api().postMultipart(path, ApiTestClient.Part.file("file", "a.md", SampleFiles.utf8("# a\nb")));

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo(errorCode);
    }

    @Test
    void givenJobOfOtherWorkspace_whenGetIngestionJobThroughOwnWorkspace_thenReturns404IngestionJobNotFound() {
        // Arrange
        seedPrimary();
        long jobId = json(createSource(42L, "Yeni kaynak", "yeni.md", SampleFiles.utf8("# a\nb")))
                .get("jobId").asLong();
        awaitFinishedJob(42L, jobId);

        // Act
        var foreign = api().get("/api/workspaces/19/ingestion-jobs/" + jobId);
        var own = api().get("/api/workspaces/42/ingestion-jobs/" + jobId);
        var unknownWorkspace = api().get("/api/workspaces/999/ingestion-jobs/" + jobId);

        // Assert
        assertThat(foreign.status()).isEqualTo(404);
        assertThat(json(foreign).get("errorCode").asString()).isEqualTo("INGESTION_JOB_NOT_FOUND");
        assertThat(own.status()).isEqualTo(200);
        assertThat(json(unknownWorkspace).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenFiveChunks_whenListChunksPage1Size2_thenReturnsChunkNoOrder() {
        // Arrange
        seedPrimary();
        for (int index : new int[] {3, 0, 4, 1, 2}) {
            insertChunk(42L, 302L, index, "Başlık " + index, "içerik " + index, VectorFixtures.unit(index));
        }

        // Act
        JsonNode page = json(api().get("/api/workspaces/42/sources/302/chunks?page=1&size=2"));
        JsonNode all = json(api().get("/api/workspaces/42/sources/302/chunks"));
        var tooLarge = api().get("/api/workspaces/42/sources/302/chunks?size=101");

        // Assert
        assertThat(page.get("content").get(0).get("chunkNo").asInt()).isEqualTo(2);
        assertThat(page.get("content").get(1).get("chunkNo").asInt()).isEqualTo(3);
        assertThat(page.get("totalElements").asLong()).isEqualTo(5);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);
        assertThat(page.get("page").asInt()).isEqualTo(1);
        assertThat(all.get("content").get(0).get("heading").asString()).isEqualTo("Başlık 0");
        assertThat(all.get("content").get(0).get("embeddingModel").asString()).isEqualTo("bge-m3@1024");
        assertThat(all.get("content").get(0).has("embedding")).isFalse();
        assertThat(tooLarge.status()).isEqualTo(400);
        assertThat(json(tooLarge).get("violations").get(0).get("field").asString()).isEqualTo("size");
    }

    @Test
    void givenExistingNameWithDifferentCase_whenCreateSource_thenReturns409SourceNameTaken() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "Masraf notları", SourceKind.MARKDOWN, null, "f.md", "READY", 0, true);

        // Act
        var response = createSource(42L, "  masraf notları ", "yeni.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(response.status()).isEqualTo(409);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("SOURCE_NAME_TAKEN");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ingestion_jobs", Integer.class)).isZero();
    }

    @Test
    void givenTenSources_whenCreateSource_thenReturns409SourceLimitReached() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        for (int index = 1; index <= 10; index++) {
            insertSource(300L + index, 42L, "Kaynak " + index, SourceKind.MARKDOWN, null, "f.md", "READY", 0, true);
        }

        // Act
        var response = createSource(42L, "On birinci", "yeni.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(response.status()).isEqualTo(409);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("SOURCE_LIMIT_REACHED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sources", Integer.class)).isEqualTo(10);
    }

    @Test
    void givenNineSourcesAndTwoParallelCreates_whenSubmitted_thenOneAcceptedAndOneSourceLimitReached()
            throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        for (int index = 1; index <= 9; index++) {
            insertSource(300L + index, 42L, "Kaynak " + index, SourceKind.MARKDOWN, null, "f.md", "READY", 0, true);
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Callable<ApiTestClient.Response>> calls = List.of(
                () -> {
                    start.await();
                    return createSource(42L, "Paralel bir", "a.md", SampleFiles.utf8("# a\nb"));
                },
                () -> {
                    start.await();
                    return createSource(42L, "Paralel iki", "b.md", SampleFiles.utf8("# a\nb"));
                });

        // Act
        List<Future<ApiTestClient.Response>> futures = calls.stream().map(executor::submit).toList();
        start.countDown();
        List<ApiTestClient.Response> responses = List.of(futures.get(0).get(), futures.get(1).get());
        executor.shutdown();

        // Assert
        assertThat(responses).extracting(ApiTestClient.Response::status).containsExactlyInAnyOrder(202, 409);
        ApiTestClient.Response conflict = responses.stream().filter(r -> r.status() == 409).findFirst().orElseThrow();
        assertThat(json(conflict).get("errorCode").asString()).isEqualTo("SOURCE_LIMIT_REACHED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sources WHERE workspace_id = 42",
                Integer.class)).isEqualTo(10);
    }

    @Test
    void givenSourceWithTwoJobs_whenListAndGetSource_thenLatestJobIsNewest() {
        // Arrange
        seedPrimary();
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));
        JsonNode failed = awaitFinishedJob(42L, json(uploadVersion(42L, 302L, "3.2", "v32.pdf",
                SampleFiles.pdf("Baslik\nmetin"))).get("jobId").asLong());
        embeddingClient.failWith(null);
        long secondJobId = json(uploadVersion(42L, 302L, "3.2", "v32.pdf", SampleFiles.pdf("Baslik\nmetin")))
                .get("jobId").asLong();
        awaitFinishedJob(42L, secondJobId);

        // Act
        JsonNode page = json(api().get("/api/workspaces/42/sources"));
        JsonNode detail = json(api().get("/api/workspaces/42/sources/302"));

        // Assert
        assertThat(failed.get("stage").asString()).isEqualTo("FAILED");
        assertThat(secondJobId).isGreaterThan(failed.get("jobId").asLong());
        JsonNode seedRow = page.get("content").get(0);
        JsonNode pdfRow = page.get("content").get(1);
        assertThat(seedRow.get("sourceId").asLong()).isEqualTo(301L);
        assertThat(seedRow.has("latestJob")).isTrue();
        assertThat(seedRow.get("latestJob").isNull()).isTrue();
        assertThat(pdfRow.get("latestJob").get("jobId").asLong()).isEqualTo(secondJobId);
        assertThat(pdfRow.get("latestJob").get("stage").asString()).isEqualTo("DONE");
        assertThat(pdfRow.get("latestJob").get("version").asString()).isEqualTo("3.2");
        assertThat(detail.get("latestJob").get("jobId").asLong()).isEqualTo(secondJobId);
        assertThat(detail.get("latestJob").get("stage").asString()).isEqualTo("DONE");
    }

    @Test
    void givenJobEmbeddingNinetySixOfTwoHundredFourteen_whenGetSource_thenLatestJobShowsProgress() {
        // Arrange
        seedPrimary();
        jdbcTemplate.update("UPDATE sources SET status = 'INGESTING' WHERE id = 302");
        long jobId = jdbcTemplate.queryForObject("""
                INSERT INTO ingestion_jobs (workspace_id, source_id, version, file_name, file_size_bytes, stage,
                                            progress, embedded_chunks, total_chunks, created_at, started_at)
                VALUES (42, 302, '3.2', 'v32.pdf', 2516582, 'EMBEDDING', 0.1 + 0.8 * 96 / 214.0, 96, 214, now(), now())
                RETURNING id
                """, Long.class);

        // Act
        JsonNode source = json(api().get("/api/workspaces/42/sources/302"));
        jdbcTemplate.update("UPDATE ingestion_jobs SET stage = 'FAILED', error_code = 'INTERNAL_ERROR' WHERE id = ?", jobId);

        // Assert
        assertThat(source.get("status").asString()).isEqualTo("INGESTING");
        JsonNode latestJob = source.get("latestJob");
        assertThat(latestJob.get("stage").asString()).isEqualTo("EMBEDDING");
        assertThat(latestJob.get("progress").asDouble()).isCloseTo(0.458879, org.assertj.core.api.Assertions.within(1e-6));
        assertThat(latestJob.get("embeddedChunks").asInt()).isEqualTo(96);
        assertThat(latestJob.get("totalChunks").asInt()).isEqualTo(214);
        assertThat(latestJob.get("statusUrl").asString()).isEqualTo("/api/workspaces/42/ingestion-jobs/" + jobId);
    }

    @Test
    void givenVersionUploadFailsWithEmbeddingUnavailable_whenGetSource_thenStatusReadyAndLatestJobFailed() {
        // Arrange
        seedPrimary();
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));
        long jobId = json(uploadVersion(42L, 302L, "3.2", "v32.pdf", SampleFiles.pdf("Baslik\nmetin")))
                .get("jobId").asLong();
        awaitFinishedJob(42L, jobId);

        // Act
        JsonNode source = json(api().get("/api/workspaces/42/sources/302"));

        // Assert
        assertThat(source.get("status").asString()).isEqualTo("READY");
        assertThat(source.get("version").asString()).isEqualTo("3.1");
        assertThat(source.get("latestJob").get("jobId").asLong()).isEqualTo(jobId);
        assertThat(source.get("latestJob").get("stage").asString()).isEqualTo("FAILED");
        assertThat(source.get("latestJob").get("errorCode").asString()).isEqualTo("EMBEDDING_UNAVAILABLE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"izin NOTLARI", "İZİN NOTLARİ", "  iZİN notlari "})
    void givenIzinNotlariCreatedViaApi_whenCreateSourceWithTurkishUppercaseName_thenReturns409WithoutErrorLog(
            String name, CapturedOutput output) {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        var first = createSource(42L, "İzin notları", "ilk.md", SampleFiles.utf8("# a\nb"));
        awaitFinishedJob(42L, json(first).get("jobId").asLong());
        int outputLengthBefore = output.getAll().length();

        // Act
        var response = createSource(42L, name, "yeni.md", SampleFiles.utf8("# a\nb"));

        // Assert
        assertThat(first.status()).isEqualTo(202);
        assertThat(response.status()).isEqualTo(409);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(json(response).get("errorCode").asString()).isEqualTo("SOURCE_NAME_TAKEN");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sources WHERE workspace_id = 42", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ingestion_jobs", Integer.class)).isEqualTo(1);
        List<String> actionLines = output.getAll().substring(outputLengthBefore).lines().toList();
        assertThat(actionLines).noneMatch(line -> line.contains(" ERROR "));
        assertThat(actionLines).noneMatch(line -> line.contains("NOTLAR") || line.contains("notlar"));
    }

    @Test
    void givenTwoParallelCreatesWithSameNameDifferentCase_whenSubmitted_thenOneAcceptedAndOneSourceNameTaken()
            throws Exception {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        embeddingClient.blockUntilReleased();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Act
        List<Future<ApiTestClient.Response>> futures = Stream.of("İzin Notları", "izin notları")
                .map(name -> executor.submit(() -> {
                    start.await();
                    return createSource(42L, name, "izin.md", SampleFiles.utf8("# a\nb"));
                })).toList();
        start.countDown();
        List<ApiTestClient.Response> responses = List.of(futures.get(0).get(), futures.get(1).get());
        executor.shutdown();

        // Assert
        assertThat(responses).extracting(ApiTestClient.Response::status).containsExactlyInAnyOrder(202, 409);
        ApiTestClient.Response rejected = responses.stream().filter(response -> response.status() == 409)
                .findFirst().orElseThrow();
        assertThat(json(rejected).get("errorCode").asString()).isEqualTo("SOURCE_NAME_TAKEN");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM sources", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM ingestion_jobs", Integer.class)).isEqualTo(1);
    }
}

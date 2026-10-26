package com.embedding.pgvector.source.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.exception.UnsupportedMediaTypeException;
import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.service.IngestionService;
import com.embedding.pgvector.pagination.PageResponse;
import com.embedding.pgvector.source.domain.dto.SourceResponse;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.source.domain.entity.SourceStatus;
import com.embedding.pgvector.source.service.SourceService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * SourceController'ın kaynak oluşturma ve yeni sürüm yükleme uçlarını, ad ve sürüm doğrulamasını sahte
 * servisle doğrular.
 */
@WebMvcTest(SourceController.class)
class SourceControllerTest {

    private static final String SOURCES_URL = "/api/workspaces/42/sources";

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SourceService sourceService;

    @MockitoBean
    private IngestionService ingestionService;

    private static IngestionJobResponse job() {
        return new IngestionJobResponse(7L, 42L, 10001L, null, "yardim.md", IngestionStage.QUEUED, 0.0, 0, null,
                null, Instant.parse("2026-10-25T10:00:00Z"), null, null, "/api/workspaces/42/ingestion-jobs/7");
    }

    private static MockMultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "yardim.md", "text/markdown", content);
    }

    private MvcTestResult create(String name, byte[] fileContent) {
        var request = mockMvcTester.post().uri(SOURCES_URL).multipart();
        if (name != null) {
            request.part(new MockPart("name", name.getBytes(StandardCharsets.UTF_8)));
        }
        if (fileContent != null) {
            request.file(file(fileContent));
        }
        return request.exchange();
    }

    private void assertViolation(MvcTestResult result, String field, String message) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo(message);
    }

    @Test
    void givenAcceptedUpload_whenCreateSource_thenReturns202WithLocationAndJob() {
        // Arrange
        given(ingestionService.createSource(eq(42L), eq("Masraf notları"), any(MultipartFile.class))).willReturn(job());

        // Act
        MvcTestResult result = create("Masraf notları", "# a".getBytes(StandardCharsets.UTF_8));

        // Assert
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).headers().hasValue("Location", "/api/workspaces/42/ingestion-jobs/7");
        assertThat(result).bodyJson().extractingPath("$.stage").isEqualTo("QUEUED");
        assertThat(result).bodyJson().extractingPath("$.jobId").isEqualTo(7);
        assertThat(result).bodyJson().extractingPath("$.statusUrl").isEqualTo("/api/workspaces/42/ingestion-jobs/7");
    }

    @Test
    void givenUploadWithName_whenCreateSource_thenServiceReceivesNameAndFileBytes() {
        // Arrange
        given(ingestionService.createSource(anyLong(), any(), any())).willReturn(job());

        // Act
        create("Masraf notları", "içerik".getBytes(StandardCharsets.UTF_8));

        // Assert
        ArgumentCaptor<MultipartFile> file = ArgumentCaptor.forClass(MultipartFile.class);
        then(ingestionService).should().createSource(eq(42L), eq("Masraf notları"), file.capture());
        assertThat(file.getValue().getOriginalFilename()).isEqualTo("yardim.md");
    }

    @Test
    void givenMissingNameOrEmptyFile_whenCreateSource_thenReturns400ValidationFailed() {
        // Act
        MvcTestResult missingName = create(null, new byte[] {1});
        MvcTestResult blankName = create("   ", new byte[] {1});
        MvcTestResult emptyFile = create("Masraf notları", new byte[0]);
        MvcTestResult missingFile = create("Masraf notları", null);

        // Assert
        assertViolation(missingName, "name", "must not be blank");
        assertViolation(blankName, "name", "must not be blank");
        assertViolation(emptyFile, "file", "must not be empty");
        assertViolation(missingFile, "file", "must not be empty");
        then(ingestionService).shouldHaveNoInteractions();
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void givenNameOutsideTwoToEightyAfterStrip_whenCreateSource_thenReturns400SizeViolation(String name) {
        // Act
        MvcTestResult result = create(name, new byte[] {1});

        // Assert
        assertViolation(result, "name", "size must be between 2 and 80");
        then(ingestionService).shouldHaveNoInteractions();
    }

    static Stream<String> invalidNames() {
        return Stream.of("a", " a ", "x".repeat(81), "  " + "x".repeat(81) + "  ");
    }

    @ParameterizedTest
    @MethodSource("validNames")
    void givenNameOfBoundaryLength_whenCreateSource_thenAccepted(String name) {
        // Arrange
        given(ingestionService.createSource(anyLong(), any(), any())).willReturn(job());

        // Act
        MvcTestResult result = create(name, new byte[] {1});

        // Assert
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
    }

    static Stream<String> validNames() {
        return Stream.of("ab", "  ab  ", "x".repeat(80), " " + "x".repeat(80) + " ", "a b");
    }

    @Test
    void givenVersionLongerThan40_whenUploadSourceVersion_thenReturns400() {
        // Act
        MvcTestResult result = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1}))
                .part(new MockPart("version", "v".repeat(41).getBytes(StandardCharsets.UTF_8))).exchange();

        // Assert
        assertViolation(result, "version", "size must be at most 40");
        then(ingestionService).shouldHaveNoInteractions();
    }

    @Test
    void givenVersionOf40CharsAndBlankVersion_whenUploadSourceVersion_thenAccepted() {
        // Arrange
        given(ingestionService.uploadSourceVersion(anyLong(), anyLong(), any(), any())).willReturn(job());

        // Act
        MvcTestResult forty = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1}))
                .part(new MockPart("version", "v".repeat(40).getBytes(StandardCharsets.UTF_8))).exchange();
        MvcTestResult blank = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1}))
                .part(new MockPart("version", "   ".getBytes(StandardCharsets.UTF_8))).exchange();
        MvcTestResult absent = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1})).exchange();

        // Assert
        assertThat(forty).hasStatus(HttpStatus.ACCEPTED);
        assertThat(blank).hasStatus(HttpStatus.ACCEPTED);
        assertThat(absent).hasStatus(HttpStatus.ACCEPTED);
    }

    @Test
    void givenEmptyFile_whenUploadSourceVersion_thenReturns400FileViolation() {
        // Act
        MvcTestResult result = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[0])).exchange();

        // Assert
        assertViolation(result, "file", "must not be empty");
    }

    @Test
    void givenVersionAndFile_whenUploadSourceVersion_thenServiceReceivesIds() {
        // Arrange
        given(ingestionService.uploadSourceVersion(eq(42L), eq(302L), eq("3.2"), any())).willReturn(job());

        // Act
        MvcTestResult result = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1})).part(new MockPart("version", "3.2".getBytes(StandardCharsets.UTF_8)))
                .exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.ACCEPTED);
        assertThat(result).headers().hasValue("Location", "/api/workspaces/42/ingestion-jobs/7");
    }

    static Stream<Arguments> domainExceptions() {
        return Stream.of(
                Arguments.of(new NotFoundException("SOURCE_NOT_FOUND", "x"), 404, "source-not-found"),
                Arguments.of(new NotFoundException("WORKSPACE_NOT_FOUND", "x"), 404, "workspace-not-found"),
                Arguments.of(new ConflictException("INGESTION_IN_PROGRESS", "x"), 409, "ingestion-in-progress"),
                Arguments.of(new ConflictException("SOURCE_LIMIT_REACHED", "x"), 409, "source-limit-reached"),
                Arguments.of(new UnsupportedMediaTypeException("UNSUPPORTED_FILE_TYPE", "x"), 415,
                        "unsupported-file-type"));
    }

    @ParameterizedTest
    @MethodSource("domainExceptions")
    void givenDomainException_whenUploadSourceVersion_thenProblemDetailWithErrorCode(RuntimeException exception,
            int status, String typeSuffix) {
        // Arrange
        given(ingestionService.uploadSourceVersion(anyLong(), anyLong(), any(), any())).willThrow(exception);
        String errorCode = ((com.embedding.pgvector.exception.BaseException) exception).getErrorCode();

        // Act
        MvcTestResult result = mockMvcTester.post().uri(SOURCES_URL + "/302/versions").multipart()
                .file(file(new byte[] {1})).exchange();

        // Assert
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(errorCode);
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/" + typeSuffix);
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(SOURCES_URL + "/302/versions");
    }

    @Test
    void givenJsonBody_whenCreateSource_thenReturns415UnsupportedMediaType() {
        // Act
        MvcTestResult result = mockMvcTester.post().uri(SOURCES_URL).contentType(MediaType.APPLICATION_JSON)
                .content("{}").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void givenSources_whenListSources_thenReturnsPageResponseWithDefaultPaging() {
        // Arrange
        SourceResponse source = new SourceResponse(302L, 42L, "Atlaskap-Lojistik-Surec-El-Kitabi", SourceKind.PDF,
                "3.1", 5, SourceStatus.READY, Instant.parse("2026-10-12T11:31:40Z"),
                "Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf", Instant.parse("2026-10-01T09:00:00Z"), null);
        given(sourceService.listSources(42L, 0, 20)).willReturn(new PageImpl<>(List.of(source), PageRequest.of(0, 20), 1));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].sourceId").isEqualTo(302);
        assertThat(result).bodyJson().extractingPath("$.content[0].kind").isEqualTo("PDF");
        assertThat(result).bodyJson().extractingPath("$.content[0].syncedAt").isEqualTo("2026-10-12T11:31:40Z");
        assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(20);
    }

    @ParameterizedTest
    @MethodSource("invalidPaging")
    void givenInvalidPageOrSize_whenListSources_thenReturns400WithFieldName(String query, String field,
            String message) {
        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL + "?" + query).exchange();

        // Assert
        assertViolation(result, field, message);
        then(sourceService).should(never()).listSources(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    static Stream<Arguments> invalidPaging() {
        return Stream.of(
                Arguments.of("page=-1", "page", "must be greater than or equal to 0"),
                Arguments.of("size=0", "size", "must be between 1 and 100"),
                Arguments.of("size=101", "size", "must be between 1 and 100"));
    }

    @Test
    void givenUnknownSource_whenGetSource_thenReturns404SourceNotFound() {
        // Arrange
        given(sourceService.getSource(42L, 999L)).willThrow(new NotFoundException("SOURCE_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL + "/999").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("SOURCE_NOT_FOUND");
    }

    @Test
    void givenBoundarySize100_whenListSources_thenAccepted() {
        // Arrange
        given(sourceService.listSources(42L, 0, 100)).willReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL + "?size=100&page=0").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    private static SourceResponse sourceWithJob(IngestionJobResponse latestJob) {
        return new SourceResponse(302L, 42L, "Atlaskap-Lojistik-Surec-El-Kitabi", SourceKind.PDF, "3.1", 5,
                latestJob == null ? SourceStatus.READY : SourceStatus.INGESTING, Instant.parse("2026-10-12T11:31:40Z"),
                "Atlaskap-Lojistik-Surec-El-Kitabi-v3.1.pdf", Instant.parse("2026-10-01T09:00:00Z"), latestJob);
    }

    @Test
    void givenSourceWithoutJob_whenGetSource_thenLatestJobIsNull() {
        // Arrange
        given(sourceService.getSource(42L, 302L)).willReturn(sourceWithJob(null));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL + "/302").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().hasPath("$.latestJob");
        assertThat(result).bodyJson().extractingPath("$.latestJob").isNull();
    }

    @Test
    void givenSourceWithJob_whenGetSource_thenLatestJobHasContractFields() {
        // Arrange
        IngestionJobResponse embedding = new IngestionJobResponse(7L, 42L, 302L, "3.2",
                "Atlaskap-Lojistik-Surec-El-Kitabi-v3.2.pdf", IngestionStage.EMBEDDING, 0.458879, 96, 214, null,
                Instant.parse("2026-10-25T10:00:00Z"), Instant.parse("2026-10-25T10:00:01Z"), null,
                "/api/workspaces/42/ingestion-jobs/7");
        given(sourceService.getSource(42L, 302L)).willReturn(sourceWithJob(embedding));

        // Act
        MvcTestResult result = mockMvcTester.get().uri(SOURCES_URL + "/302").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("INGESTING");
        assertThat(result).bodyJson().extractingPath("$.latestJob.jobId").isEqualTo(7);
        assertThat(result).bodyJson().extractingPath("$.latestJob.workspaceId").isEqualTo(42);
        assertThat(result).bodyJson().extractingPath("$.latestJob.sourceId").isEqualTo(302);
        assertThat(result).bodyJson().extractingPath("$.latestJob.version").isEqualTo("3.2");
        assertThat(result).bodyJson().extractingPath("$.latestJob.fileName")
                .isEqualTo("Atlaskap-Lojistik-Surec-El-Kitabi-v3.2.pdf");
        assertThat(result).bodyJson().extractingPath("$.latestJob.stage").isEqualTo("EMBEDDING");
        assertThat(result).bodyJson().extractingPath("$.latestJob.progress").isEqualTo(0.458879);
        assertThat(result).bodyJson().extractingPath("$.latestJob.embeddedChunks").isEqualTo(96);
        assertThat(result).bodyJson().extractingPath("$.latestJob.totalChunks").isEqualTo(214);
        assertThat(result).bodyJson().hasPath("$.latestJob.errorCode");
        assertThat(result).bodyJson().extractingPath("$.latestJob.createdAt").isEqualTo("2026-10-25T10:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.latestJob.startedAt").isEqualTo("2026-10-25T10:00:01Z");
        assertThat(result).bodyJson().hasPath("$.latestJob.finishedAt");
        assertThat(result).bodyJson().extractingPath("$.latestJob.statusUrl")
                .isEqualTo("/api/workspaces/42/ingestion-jobs/7");
    }
}

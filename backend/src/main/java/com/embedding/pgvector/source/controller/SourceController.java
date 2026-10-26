package com.embedding.pgvector.source.controller;

import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.service.IngestionService;
import com.embedding.pgvector.pagination.PageParameters;
import com.embedding.pgvector.pagination.PageResponse;
import com.embedding.pgvector.source.domain.dto.NotEmptyFile;
import com.embedding.pgvector.source.domain.dto.SourceResponse;
import com.embedding.pgvector.source.service.SourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Kaynakları listeleme, tek kaynak getirme, dosyayla kaynak ekleme ve yeni sürüm yükleme için REST uçlarını sunar.
 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/sources")
@Tag(name = "Sources", description = "Knowledge sources of a workspace, their file uploads and chunks")
public class SourceController {

    public static final String NAME_SIZE_PATTERN = "(?Us)\\s*|\\s*\\S.{0,78}\\S\\s*";
    public static final String VERSION_SIZE_PATTERN = "(?Us)\\s*(?:\\S(?:.{0,38}\\S)?)?\\s*";

    private static final String PROBLEM_JSON = "application/problem+json";

    private final SourceService sourceService;
    private final IngestionService ingestionService;

    public SourceController(SourceService sourceService, IngestionService ingestionService) {
        this.sourceService = sourceService;
        this.ingestionService = ingestionService;
    }

    @Operation(operationId = "listSources", summary = "List the sources of a workspace, ordered by id")
    @ApiResponse(responseCode = "200", description = "One page of sources")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: page < 0 or size not in 1-100 (violations[].field = page / size)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PageResponse<SourceResponse> listSources(
            @PathVariable long workspaceId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
            @Parameter(description = "Page size")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
            @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
        return PageResponse.from(sourceService.listSources(workspaceId, page, size));
    }

    @Operation(operationId = "getSource", summary = "Get one source of a workspace")
    @ApiResponse(responseCode = "200", description = "The source")
    @ApiResponse(responseCode = "404",
            description = "WORKSPACE_NOT_FOUND: no workspace with this id; SOURCE_NOT_FOUND: no source with this id "
                    + "in this workspace",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{sourceId}")
    public SourceResponse getSource(@PathVariable long workspaceId, @PathVariable long sourceId) {
        return sourceService.getSource(workspaceId, sourceId);
    }

    @Operation(operationId = "createSource",
            summary = "Create a source from an uploaded file and start its first ingestion job")
    @ApiResponse(responseCode = "202", description = "Job accepted; it runs in the background",
            headers = @Header(name = "Location", description = "URL of the ingestion job (same as statusUrl)",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: name missing / blank / not 2-80 characters, file missing or empty",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "SOURCE_NAME_TAKEN, SOURCE_LIMIT_REACHED, EMBEDDING_MODEL_MISMATCH",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE: the file is larger than 20 MB",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415",
            description = "UNSUPPORTED_FILE_TYPE, UNSUPPORTED_FILE_ENCODING, UNSUPPORTED_MEDIA_TYPE",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> createSource(
            @PathVariable long workspaceId,
            @Parameter(description = "Source name. Stripped; 2-80 characters; unique in the workspace ignoring case. "
                    + "Always required here; when the user leaves the name empty, the web client sends the file "
                    + "name without its extension (e.g. Atlaskap-Lojistik-Surec-El-Kitabi).",
                    required = true, schema = @Schema(type = "string", minLength = 2, maxLength = 80,
                    example = "Atlaskap-Lojistik-Surec-El-Kitabi"))
            @RequestPart(name = "name", required = false)
            @NotBlank(message = "must not be blank")
            @Pattern(regexp = NAME_SIZE_PATTERN, message = "size must be between 2 and 80")
            @Nullable String name,
            @Parameter(description = ".pdf, .txt, .md, .markdown or .csv, not empty, at most 20 MB", required = true)
            @RequestPart(name = "file", required = false)
            @NotEmptyFile
            @Nullable MultipartFile file) {
        IngestionJobResponse job = ingestionService.createSource(workspaceId, Objects.requireNonNull(name),
                Objects.requireNonNull(file));
        return accepted(job);
    }

    @Operation(operationId = "uploadSourceVersion",
            summary = "Upload a new version of an existing source and start an ingestion job")
    @ApiResponse(responseCode = "202", description = "Job accepted; it runs in the background",
            headers = @Header(name = "Location", description = "URL of the ingestion job (same as statusUrl)",
                    schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: file missing or empty, version longer than 40 characters",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND, SOURCE_NOT_FOUND",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409",
            description = "INGESTION_IN_PROGRESS, SOURCE_KIND_MISMATCH, EMBEDDING_MODEL_MISMATCH",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "FILE_TOO_LARGE: the file is larger than 20 MB",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "415",
            description = "UNSUPPORTED_FILE_TYPE, UNSUPPORTED_FILE_ENCODING, UNSUPPORTED_MEDIA_TYPE",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping(path = "/{sourceId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionJobResponse> uploadSourceVersion(
            @PathVariable long workspaceId,
            @PathVariable long sourceId,
            @Parameter(description = "Same rules as in createSource.", required = true)
            @RequestPart(name = "file", required = false)
            @NotEmptyFile
            @Nullable MultipartFile file,
            @Parameter(description = "Optional version label. Stripped; 1-40 characters. When missing or blank, the "
                    + "source version becomes null.", schema = @Schema(type = "string", maxLength = 40,
                    example = "3.2"))
            @RequestPart(name = "version", required = false)
            @Pattern(regexp = VERSION_SIZE_PATTERN, message = "size must be at most 40")
            @Nullable String version) {
        IngestionJobResponse job = ingestionService.uploadSourceVersion(workspaceId, sourceId, version,
                Objects.requireNonNull(file));
        return accepted(job);
    }

    private static ResponseEntity<IngestionJobResponse> accepted(IngestionJobResponse job) {
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }
}

package com.embedding.pgvector.ingestion.controller;

import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.service.IngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir içe alma işinin aşamasını ve ilerlemesini sorgulayan REST ucunu sunar.
 */
@RestController
@Tag(name = "Ingestion", description = "Background ingestion jobs (parse, chunk, embed, write)")
public class IngestionJobController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final IngestionService ingestionService;

    public IngestionJobController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @Operation(operationId = "getIngestionJob", summary = "Get the stage and progress of an ingestion job")
    @ApiResponse(responseCode = "200", description = "The job")
    @ApiResponse(responseCode = "404",
            description = "WORKSPACE_NOT_FOUND: no workspace with this id; INGESTION_JOB_NOT_FOUND: no job with this "
                    + "id in this workspace",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/ingestion-jobs/{jobId}")
    public IngestionJobResponse getIngestionJob(@PathVariable long workspaceId, @PathVariable long jobId) {
        return ingestionService.getIngestionJob(workspaceId, jobId);
    }
}

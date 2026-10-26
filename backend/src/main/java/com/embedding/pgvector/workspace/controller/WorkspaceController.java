package com.embedding.pgvector.workspace.controller;

import com.embedding.pgvector.pagination.PageParameters;
import com.embedding.pgvector.pagination.PageResponse;
import com.embedding.pgvector.workspace.domain.dto.WorkspaceResponse;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Çalışma alanlarını kaynak ve parça sayılarıyla listeleyen ve tek bir çalışma alanını getiren REST uçlarını sunar.
 */
@RestController
@RequestMapping("/api/workspaces")
@Tag(name = "Workspaces", description = "Workspaces (tenants). There is no endpoint to create one. A database "
        + "migration of API 0.5.0 runs once per database: it deletes all data of an earlier install (workspaces, "
        + "sources, chunks, ingestion jobs, search events) and creates a single empty workspace (id 42, "
        + "\"Belgelerim\"). On a clean install there is nothing to delete.")
public class WorkspaceController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Operation(operationId = "listWorkspaces",
            summary = "List workspaces with their source and chunk counts, ordered by id")
    @ApiResponse(responseCode = "200", description = "One page of workspaces")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: page < 0 or size not in 1-100 (violations[].field = page / size)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping
    public PageResponse<WorkspaceResponse> listWorkspaces(
            @Parameter(description = "Zero-based page index")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
            @Parameter(description = "Page size")
            @RequestParam(name = "size", defaultValue = "50")
            @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
            @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
        return PageResponse.from(workspaceService.listWorkspaces(page, size));
    }

    @Operation(operationId = "getWorkspace", summary = "Get one workspace with its source and chunk counts")
    @ApiResponse(responseCode = "200", description = "The workspace")
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{workspaceId}")
    public WorkspaceResponse getWorkspace(@PathVariable long workspaceId) {
        return workspaceService.getWorkspace(workspaceId);
    }
}

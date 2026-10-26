package com.embedding.pgvector.search.controller;

import com.embedding.pgvector.search.domain.dto.SearchChunksRequest;
import com.embedding.pgvector.search.domain.dto.SearchResultResponse;
import com.embedding.pgvector.search.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir çalışma alanının parçalarında kelime, vektör ya da hibrit arama yapan REST ucunu sunar.
 */
@RestController
@Tag(name = "Search",
        description = "Keyword, vector and hybrid (RRF) search over the chunks of a workspace, and the search log")
public class SearchController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(operationId = "searchChunks",
            summary = "Search the chunks of a workspace in keyword, vector or hybrid mode")
    @ApiResponse(responseCode = "200", description = "Search result")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: invalid query, mode, filterStrategy, efSearch, limit or channel; "
                    + "BAD_REQUEST: body missing or not valid JSON",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "EMBEDDING_MODEL_MISMATCH: only in VECTOR and HYBRID mode",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "EMBEDDING_UNAVAILABLE, EMBEDDING_MODEL_NOT_FOUND",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @PostMapping("/api/workspaces/{workspaceId}/search")
    public SearchResultResponse searchChunks(@PathVariable long workspaceId,
            @Valid @RequestBody SearchChunksRequest request) {
        return searchService.searchChunks(workspaceId, request);
    }
}

package com.embedding.pgvector.chunk.controller;

import com.embedding.pgvector.chunk.domain.dto.ChunkResponse;
import com.embedding.pgvector.chunk.service.ChunkService;
import com.embedding.pgvector.pagination.PageParameters;
import com.embedding.pgvector.pagination.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bir kaynağın parçalarını sayfa sayfa listeleyen ve tek bir parçayı getiren REST uçlarını sunar.
 */
@RestController
@Tag(name = "Sources", description = "Knowledge sources of a workspace, their file uploads and chunks")
public class ChunkController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final ChunkService chunkService;

    public ChunkController(ChunkService chunkService) {
        this.chunkService = chunkService;
    }

    @Operation(operationId = "listChunks", summary = "List the chunks of a source ordered by chunkNo, then chunkId")
    @ApiResponse(responseCode = "200", description = "One page of chunks")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: page < 0 or size not in 1-100 (violations[].field = page / size)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND, SOURCE_NOT_FOUND",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/sources/{sourceId}/chunks")
    public PageResponse<ChunkResponse> listChunks(
            @PathVariable long workspaceId,
            @PathVariable long sourceId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
            @Parameter(description = "Page size")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
            @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
        return PageResponse.from(chunkService.listChunks(workspaceId, sourceId, page, size));
    }

    @Operation(operationId = "getChunk", summary = "Get one chunk of a source",
            description = "Used to show a chunk that was opened from a search result. A chunk is deleted when a new "
                    + "version of its source is written, so an old chunk id answers CHUNK_NOT_FOUND.")
    @ApiResponse(responseCode = "200", description = "The chunk")
    @ApiResponse(responseCode = "404",
            description = "WORKSPACE_NOT_FOUND: no workspace with this id. SOURCE_NOT_FOUND: no source with this id "
                    + "in this workspace (also when the source belongs to another workspace). CHUNK_NOT_FOUND: no "
                    + "chunk with this id in this source (also when the chunk belongs to another source, or was "
                    + "replaced by a newer version of the source).",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/sources/{sourceId}/chunks/{chunkId}")
    public ChunkResponse getChunk(@PathVariable long workspaceId, @PathVariable long sourceId,
            @Parameter(example = "1380") @PathVariable long chunkId) {
        return chunkService.getChunk(workspaceId, sourceId, chunkId);
    }
}

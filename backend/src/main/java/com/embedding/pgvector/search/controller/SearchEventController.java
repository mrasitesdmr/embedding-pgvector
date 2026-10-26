package com.embedding.pgvector.search.controller;

import com.embedding.pgvector.pagination.PageParameters;
import com.embedding.pgvector.pagination.PageResponse;
import com.embedding.pgvector.search.domain.dto.PopularQueriesResponse;
import com.embedding.pgvector.search.domain.dto.SearchEventResponse;
import com.embedding.pgvector.search.domain.dto.SearchEventStatsResponse;
import com.embedding.pgvector.search.service.SearchService;
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
 * Bir çalışma alanının arama kayıtları, arama istatistikleri ve sık aranan aramaları için REST uçlarını sunar.
 */
@RestController
@Tag(name = "Search",
        description = "Keyword, vector and hybrid (RRF) search over the chunks of a workspace, and the search log")
public class SearchEventController {

    private static final String PROBLEM_JSON = "application/problem+json";

    private final SearchService searchService;

    public SearchEventController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(operationId = "listSearchEvents", summary = "List the search events of a workspace, newest first")
    @ApiResponse(responseCode = "200",
            description = "One page of search events ordered by createdAt descending, then id descending")
    @ApiResponse(responseCode = "400",
            description = "VALIDATION_FAILED: page < 0 or size not in 1-100 (violations[].field = page / size)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/search-events")
    public PageResponse<SearchEventResponse> listSearchEvents(
            @PathVariable long workspaceId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(name = "page", defaultValue = "0")
            @Min(value = 0, message = PageParameters.PAGE_MESSAGE) int page,
            @Parameter(description = "Page size")
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = PageParameters.SIZE_MESSAGE)
            @Max(value = PageParameters.MAX_SIZE, message = PageParameters.SIZE_MESSAGE) int size) {
        return PageResponse.from(searchService.listSearchEvents(workspaceId, page, size));
    }

    @Operation(operationId = "getSearchEventStats",
            summary = "Get the search log indicators of a workspace (today, zero-result rate, low-result rate)",
            description = "Calculated by the backend over the search events of the workspace, all channels. Days are "
                    + "calendar days in timeZone (search.stats-time-zone, Europe/Istanbul). \"Today\" starts at "
                    + "00:00 today in that zone. \"Last 7 days\" starts at 00:00 six days before today (today plus "
                    + "the six previous days). Events newer than the current time do not exist and are not filtered.")
    @ApiResponse(responseCode = "200", description = "The indicators")
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/search-events/stats")
    public SearchEventStatsResponse getSearchEventStats(@PathVariable long workspaceId) {
        return searchService.getSearchEventStats(workspaceId);
    }

    @Operation(operationId = "listPopularQueries",
            summary = "List the most repeated searches of a workspace that found something (\"Sık aranan\")",
            description = "Groups the search events of the workspace (all channels, all modes, any time) by query "
                    + "key and returns at most search.popular-query-limit (5) groups. Query key: the stored query "
                    + "with every run of whitespace replaced by one space, then Unicode NFC, then İ, I, ı replaced "
                    + "by i, then lower case. Counted events: resultCount >= 1 and (keyword branch count >= 1 or "
                    + "the smallest hit distance <= search.popular-max-distance). Order: searchCount descending, "
                    + "then lastSearchedAt descending, then the newest event id descending. query is the query "
                    + "text of the newest counted event of the group. Not paginated.")
    @ApiResponse(responseCode = "200",
            description = "The popular queries; queries is empty when no counted search exists")
    @ApiResponse(responseCode = "404", description = "WORKSPACE_NOT_FOUND: no workspace with this id",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "500", description = "INTERNAL_ERROR: unexpected server error",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/workspaces/{workspaceId}/search-events/popular-queries")
    public PopularQueriesResponse listPopularQueries(@PathVariable long workspaceId) {
        return searchService.listPopularQueries(workspaceId);
    }
}

package com.embedding.pgvector.search.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Parça aramasının istek gövdesini taşır; arama metnini ve kanal adını kırpar, boş kanal adını yok sayar.
 */
public record SearchChunksRequest(
        @Schema(description = "Leading and trailing whitespace is stripped; then 1-500 characters (UTF-16 code units).",
                maxLength = 500, example = "Yıllık izin kaç gün?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "must not be blank")
        @Size(max = 500, message = "size must be at most 500 characters")
        @Nullable String query,

        @Schema(types = {"string", "null"}, pattern = "(?i)keyword|vector|hybrid", example = "HYBRID",
                description = "KEYWORD, VECTOR or HYBRID, case-insensitive. Default: search.default-mode (HYBRID).")
        @Pattern(regexp = "(?i)keyword|vector|hybrid", message = "must be one of KEYWORD, VECTOR, HYBRID")
        @Nullable String mode,

        @Schema(types = {"string", "null"}, pattern = "(?i)post_filter|iterative_scan", example = "ITERATIVE_SCAN",
                description = "POST_FILTER (hnsw.iterative_scan = off) or ITERATIVE_SCAN (hnsw.iterative_scan = "
                        + "relaxed_order), case-insensitive. Ignored in KEYWORD mode. Default: "
                        + "search.default-filter-strategy (ITERATIVE_SCAN).")
        @Pattern(regexp = "(?i)post_filter|iterative_scan", message = "must be one of POST_FILTER, ITERATIVE_SCAN")
        @Nullable String filterStrategy,

        @Schema(types = {"integer", "null"}, format = "int32", minimum = "10", maximum = "1000", example = "40",
                description = "hnsw.ef_search for this query only. Ignored in KEYWORD mode. Default: "
                        + "search.default-ef-search (40).")
        @Min(value = 10, message = "must be between 10 and 1000")
        @Max(value = 1000, message = "must be between 10 and 1000")
        @Nullable Integer efSearch,

        @Schema(types = {"integer", "null"}, format = "int32", minimum = "1", maximum = "50", example = "10",
                description = "Number of results, also the LIMIT of each branch. Default search.default-limit (10).")
        @Min(value = 1, message = "must be between 1 and 50")
        @Max(value = 50, message = "must be between 1 and 50")
        @Nullable Integer limit,

        @Schema(types = {"string", "null"}, maxLength = 60, example = "Temsilci paneli",
                description = "Stored in the search event only. Stripped; blank becomes null.")
        @Size(max = 60, message = "size must be at most 60 characters")
        @Nullable String channel,

        @Schema(types = {"boolean", "null"},
                description = "When true and mode is VECTOR or HYBRID, vectorPlan contains the EXPLAIN (ANALYZE, "
                        + "BUFFERS) output of the vector branch. Default false.")
        @Nullable Boolean explain) {

    public SearchChunksRequest {
        query = query == null ? null : query.strip();
        channel = channel == null || channel.isBlank() ? null : channel.strip();
    }
}

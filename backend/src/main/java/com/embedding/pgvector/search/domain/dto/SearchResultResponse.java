package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Bir aramanın sonuçlarını, uygulanan ayarlar, kol bazında sonuç sayıları ve sürelerle birlikte yanıt gövdesinde taşır.
 */
public record SearchResultResponse(
        @Schema(description = "The stripped query", requiredMode = Schema.RequiredMode.REQUIRED)
        String query,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SearchMode mode,

        @Schema(types = {"string", "null"}, description = "Null in KEYWORD mode")
        @Nullable FilterStrategy filterStrategy,

        @Schema(types = {"integer", "null"}, format = "int32", description = "Null in KEYWORD mode")
        @Nullable Integer efSearch,

        @Schema(description = "Requested number of results", requiredMode = Schema.RequiredMode.REQUIRED)
        int limit,

        @Schema(description = "Number of hits returned", requiredMode = Schema.RequiredMode.REQUIRED)
        int returnedCount,

        @Schema(types = {"integer", "null"}, format = "int32",
                description = "Rows found by the keyword branch (at most limit); null in VECTOR mode")
        @Nullable Integer keywordCount,

        @Schema(types = {"integer", "null"}, format = "int32",
                description = "Rows found by the vector branch after the workspace filter (at most limit); null in "
                        + "KEYWORD mode")
        @Nullable Integer vectorCount,

        @Schema(description = "True when the vector branch returned fewer than limit rows; always false in KEYWORD "
                + "mode", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean vectorShort,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SearchTimingsResponse timings,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<SearchHitResponse> hits,

        @Schema(types = {"string", "null"}, description = "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF) text of the vector "
                + "branch; only when explain was true and mode is not KEYWORD")
        @Nullable String vectorPlan) {

    public SearchResultResponse {
        hits = List.copyOf(hits);
    }
}

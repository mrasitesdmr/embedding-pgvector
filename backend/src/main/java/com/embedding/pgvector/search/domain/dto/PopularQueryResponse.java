package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.repository.PopularQueryView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Sık aranan tek bir arama metnini, kaç kez arandığı ve en son ne zaman arandığıyla birlikte taşır.
 */
public record PopularQueryResponse(
        @Schema(description = "Query text of the newest counted search of the group", example = "Yıllık izin kaç gün?",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String query,

        @Schema(description = "Number of counted searches with this query key", minimum = "1", example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long searchCount,

        @Schema(description = "createdAt of the newest counted search of the group",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant lastSearchedAt) {

    public static PopularQueryResponse from(PopularQueryView view) {
        return new PopularQueryResponse(view.getQuery(), view.getSearchCount(), view.getLastSearchedAt());
    }
}

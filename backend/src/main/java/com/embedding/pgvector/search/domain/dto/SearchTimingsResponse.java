package com.embedding.pgvector.search.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Bir aramanın embedding, SQL ve toplam sürelerini milisaniye olarak taşır.
 */
public record SearchTimingsResponse(
        @Schema(types = {"integer", "null"}, format = "int64",
                description = "Time of the query embedding call; null in KEYWORD mode (no call is made)")
        @Nullable Long embeddingMs,

        @Schema(description = "Time of the search SQL (including SET LOCAL statements), without the EXPLAIN query",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long sqlMs,

        @Schema(description = "Time from the start of the search until the response was built (embedding, SQL, "
                + "highlighting, EXPLAIN)", requiredMode = Schema.RequiredMode.REQUIRED)
        long totalMs) {
}

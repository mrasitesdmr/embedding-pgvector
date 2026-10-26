package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.SearchEvent;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Geçmişte yapılmış tek bir aramanın kaydını yanıt gövdesinde taşır.
 */
public record SearchEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long searchEventId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(types = {"string", "null"})
        @Nullable String channel,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String query,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SearchMode mode,

        @Schema(types = {"string", "null"})
        @Nullable FilterStrategy filterStrategy,

        @Schema(types = {"integer", "null"}, format = "int32", example = "10",
                description = "Requested number of results of the search (SearchResultResponse.limit). Null for "
                        + "events stored before API 0.3.0, when it was not recorded.")
        @Nullable Integer limit,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int resultCount,

        @Schema(types = {"integer", "null"}, format = "int64",
                description = "Chunk id of the first hit; null when there was no hit")
        @Nullable Long topChunkId,

        @Schema(types = {"string", "null"},
                description = "Heading of the first hit at search time (kept even if the chunk is later replaced)")
        @Nullable String topChunkHeading,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long totalMs) {

    public static SearchEventResponse from(SearchEvent event) {
        return new SearchEventResponse(event.getId(), event.getCreatedAt(), event.getChannel(), event.getQuery(),
                event.getMode(), event.getFilterStrategy(), event.getResultLimit(), event.getResultCount(),
                event.getTopChunkId(), event.getTopChunkHeading(), event.getTotalMs());
    }
}

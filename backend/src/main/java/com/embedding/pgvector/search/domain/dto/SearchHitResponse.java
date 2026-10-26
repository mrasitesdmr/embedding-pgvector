package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.domain.entity.SearchBranch;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Tek bir arama sonucunu, onu bulan arama kollarını ve her kolun sıra, skor ve mesafe değerlerini yanıtta taşır.
 */
public record SearchHitResponse(
        @Schema(description = "1-based position in this response", requiredMode = Schema.RequiredMode.REQUIRED)
        int rank,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long chunkId,

        @Schema(description = "Order of the chunk inside its source (same value as ChunkResponse.chunkNo)",
                minimum = "0", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        int chunkNo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long sourceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String sourceName,

        @Schema(types = {"string", "null"})
        @Nullable String heading,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @ArraySchema(arraySchema = @Schema(description = "Branches that found the chunk, in this order when both",
                requiredMode = Schema.RequiredMode.REQUIRED))
        List<SearchBranch> matchedBy,

        @Schema(types = {"integer", "null"}, format = "int32", description = "1-based position in the keyword branch")
        @Nullable Integer keywordRank,

        @Schema(types = {"number", "null"}, format = "double", description = "ts_rank_cd value")
        @Nullable Double keywordScore,

        @Schema(types = {"integer", "null"}, format = "int32", description = "1-based position in the vector branch")
        @Nullable Integer vectorRank,

        @Schema(types = {"number", "null"}, format = "double",
                description = "Cosine distance (<=>) to the query vector; smaller is closer")
        @Nullable Double distance,

        @Schema(types = {"number", "null"}, format = "double",
                description = "Only in HYBRID mode: 1 / (rrfK + keywordRank) + 1 / (rrfK + vectorRank), a missing "
                        + "rank adds 0")
        @Nullable Double rrfScore,

        @ArraySchema(arraySchema = @Schema(description = "Words of heading/content whose stems match the query stems; "
                + "empty unless the keyword branch found the chunk. No HTML.",
                requiredMode = Schema.RequiredMode.REQUIRED))
        List<MatchedTermResponse> matchedTerms) {

    public SearchHitResponse {
        matchedBy = List.copyOf(matchedBy);
        matchedTerms = List.copyOf(matchedTerms);
    }
}

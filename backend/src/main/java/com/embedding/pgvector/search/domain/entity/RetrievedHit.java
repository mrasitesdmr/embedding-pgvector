package com.embedding.pgvector.search.domain.entity;

import org.jspecify.annotations.Nullable;

/**
 * Arama sorgusundan dönen tek bir parçayı, arama kollarının sıra, skor ve mesafe değerleriyle birlikte taşır.
 */
public record RetrievedHit(
        long chunkId,
        int chunkNo,
        long sourceId,
        String sourceName,
        @Nullable String heading,
        String content,
        @Nullable Integer keywordRank,
        @Nullable Double keywordScore,
        @Nullable Integer vectorRank,
        @Nullable Double distance,
        @Nullable Double rrfScore) {
}

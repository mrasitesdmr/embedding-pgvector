package com.embedding.pgvector.search.domain.entity;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Bir arama sorgusundan dönen sonuçları ve arama kollarının kaçar sonuç bulduğunu taşır.
 */
public record RetrievalResult(List<RetrievedHit> hits, @Nullable Integer keywordCount, @Nullable Integer vectorCount) {

    public RetrievalResult {
        hits = List.copyOf(hits);
    }
}

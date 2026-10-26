package com.embedding.pgvector.search.domain.entity;

import java.util.Locale;

/**
 * Vektör aramasında çalışma alanı filtresinin HNSW taramasıyla nasıl birleştirileceğini belirtir.
 */
public enum FilterStrategy {
    POST_FILTER("off"),
    ITERATIVE_SCAN("relaxed_order");

    private final String iterativeScanValue;

    FilterStrategy(String iterativeScanValue) {
        this.iterativeScanValue = iterativeScanValue;
    }

    public String iterativeScanValue() {
        return iterativeScanValue;
    }

    public static FilterStrategy fromInput(String input) {
        return valueOf(input.strip().toUpperCase(Locale.ROOT));
    }
}

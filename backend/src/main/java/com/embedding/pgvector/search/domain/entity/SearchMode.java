package com.embedding.pgvector.search.domain.entity;

import java.util.Locale;

/**
 * Bir aramanın hangi yöntemle yapılacağını belirtir ve seçilen modun sorgu vektörü gerektirip gerektirmediğini söyler.
 */
public enum SearchMode {
    KEYWORD,
    VECTOR,
    HYBRID;

    public boolean usesVector() {
        return this != KEYWORD;
    }

    public static SearchMode fromInput(String input) {
        return valueOf(input.strip().toUpperCase(Locale.ROOT));
    }
}

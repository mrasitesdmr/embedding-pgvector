package com.embedding.pgvector.pagination;

/**
 * Sayfalı liste uçlarında sayfa numarası ve sayfa boyutu için ortak sınırı ve doğrulama mesajlarını tanımlar.
 */
public final class PageParameters {

    public static final int MAX_SIZE = 100;
    public static final String PAGE_MESSAGE = "must be greater than or equal to 0";
    public static final String SIZE_MESSAGE = "must be between 1 and 100";

    private PageParameters() {
    }
}

package com.embedding.pgvector.search.repository;

/**
 * Arama kaydı istatistik sorgusunun sayım satırını taşır.
 */
public interface SearchEventStatsView {

    long getTotalCount();

    long getTodayCount();

    long getLast7DaysCount();

    long getZeroResultCount();

    long getLowResultCount();
}

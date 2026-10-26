package com.embedding.pgvector.search.repository;

/**
 * Aramadan önce veritabanı oturumuna uygulanan HNSW ve sıralama ayarlarının sorgudan dönen değerlerini taşır.
 */
public interface AppliedSearchSettings {

    String getEfSearch();

    String getIterativeScan();

    String getMaxScanTuples();

    String getEnableSort();
}

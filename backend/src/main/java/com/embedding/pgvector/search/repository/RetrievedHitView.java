package com.embedding.pgvector.search.repository;

import org.jspecify.annotations.Nullable;

/**
 * Kelime, vektör ve hibrit arama sorgularının tek bir sonuç satırını taşır.
 */
public interface RetrievedHitView {

    long getChunkId();

    int getChunkNo();

    long getSourceId();

    String getSourceName();

    @Nullable String getHeading();

    String getContent();

    @Nullable Long getKeywordRank();

    @Nullable Double getKeywordScore();

    @Nullable Long getVectorRank();

    @Nullable Double getDistance();

    @Nullable Double getRrfScore();

    @Nullable Long getKeywordCount();

    @Nullable Long getVectorCount();
}

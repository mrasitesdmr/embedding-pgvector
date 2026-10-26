package com.embedding.pgvector.chunk.repository;

import org.jspecify.annotations.Nullable;

/**
 * Parça listeleme ve getirme sorgularının vektör içermeyen satırını taşır.
 */
public interface ChunkView {

    long getId();

    int getChunkNo();

    @Nullable String getHeading();

    String getContent();

    int getTokenCount();

    String getEmbeddingModel();
}

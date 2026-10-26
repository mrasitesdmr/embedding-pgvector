package com.embedding.pgvector.chunk.repository;

/**
 * Her embedding modelinin kaç çalışma alanında kullanıldığını sayan sorgunun satırını taşır.
 */
public interface EmbeddingModelUsage {

    String getEmbeddingModel();

    long getWorkspaceCount();
}

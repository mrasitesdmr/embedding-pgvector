package com.embedding.pgvector.chunk.repository;

/**
 * Bir çalışma alanında kullanılan bir embedding modelini gösteren sorgu satırını taşır.
 */
public interface WorkspaceEmbeddingModelView {

    long getWorkspaceId();

    String getEmbeddingModel();
}

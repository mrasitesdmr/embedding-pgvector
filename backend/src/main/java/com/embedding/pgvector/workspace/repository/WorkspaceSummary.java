package com.embedding.pgvector.workspace.repository;

/**
 * Bir çalışma alanını kaynak ve parça sayılarıyla özetleyen sorgu satırını taşır.
 */
public interface WorkspaceSummary {

    long getWorkspaceId();

    String getName();

    long getSourceCount();

    long getChunkCount();
}

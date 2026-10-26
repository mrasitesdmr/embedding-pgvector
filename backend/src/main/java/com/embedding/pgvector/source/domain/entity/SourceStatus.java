package com.embedding.pgvector.source.domain.entity;

/**
 * Bir kaynağın içe alma sürecine göre hangi durumda olduğunu belirtir.
 */
public enum SourceStatus {
    INGESTING,
    READY,
    FAILED
}

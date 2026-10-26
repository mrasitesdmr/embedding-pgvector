package com.embedding.pgvector.ingestion.domain.entity;

/**
 * Başarısız biten bir içe alma işinin hangi nedenle durduğunu belirtir.
 */
public enum IngestionErrorCode {
    NO_TEXT_EXTRACTED,
    FILE_UNREADABLE,
    EMBEDDING_UNAVAILABLE,
    EMBEDDING_MODEL_NOT_FOUND,
    EMBEDDING_MODEL_MISMATCH,
    INGESTION_INTERRUPTED,
    INTERNAL_ERROR
}

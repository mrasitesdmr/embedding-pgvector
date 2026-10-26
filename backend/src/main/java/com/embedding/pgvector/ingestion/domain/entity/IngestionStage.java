package com.embedding.pgvector.ingestion.domain.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Bir içe alma işinin hangi aşamada olduğunu ve işin bitmiş sayılıp sayılmadığını belirtir.
 */
public enum IngestionStage {
    QUEUED,
    PARSING,
    CHUNKING,
    EMBEDDING,
    WRITING,
    DONE,
    FAILED;

    public static final Set<IngestionStage> FINISHED = EnumSet.of(DONE, FAILED);

    public boolean isActive() {
        return !FINISHED.contains(this);
    }
}

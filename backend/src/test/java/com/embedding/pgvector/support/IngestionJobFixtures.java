package com.embedding.pgvector.support;

import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.source.domain.entity.Source;

/**
 * Testlerde verilen kaynak için kuyruktaki içe alma işleri üreten yardımcıdır.
 */
public final class IngestionJobFixtures {

    private IngestionJobFixtures() {
    }

    public static IngestionJob queued(Source source) {
        return IngestionJob.queued(source, "3.2", "file.pdf", 1024L, WorkspaceFixtures.CREATED_AT);
    }
}

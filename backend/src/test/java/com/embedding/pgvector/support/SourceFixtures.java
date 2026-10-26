package com.embedding.pgvector.support;

import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import java.time.Instant;

/**
 * Testlerde çalışma alanına bağlı, içe alınmakta olan kaynaklar üreten yardımcıdır.
 */
public final class SourceFixtures {

    public static final Instant SYNCED_AT = Instant.parse("2026-10-12T11:31:40Z");

    private SourceFixtures() {
    }

    public static Source readySource(Workspace workspace, long sourceId, SourceKind kind, String version,
            int chunkCount) {
        return readySource(workspace, sourceId, "Source " + sourceId, kind, version, chunkCount);
    }

    public static Source readySource(Workspace workspace, long sourceId, String name, SourceKind kind,
            String version, int chunkCount) {
        Source source = Source.ingesting(workspace, sourceId, name, kind, WorkspaceFixtures.CREATED_AT);
        source.completeIngestion(version, "file-" + sourceId + ".bin", chunkCount, SYNCED_AT);
        return source;
    }

    public static Source ingestingSource(Workspace workspace, long sourceId, String name, SourceKind kind) {
        return Source.ingesting(workspace, sourceId, name, kind, WorkspaceFixtures.CREATED_AT);
    }
}

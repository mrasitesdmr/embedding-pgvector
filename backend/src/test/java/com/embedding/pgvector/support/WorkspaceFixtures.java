package com.embedding.pgvector.support;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import java.time.Instant;

/**
 * Testlerde boş ya da belirli sayıda kaynağı olan çalışma alanları üreten yardımcıdır.
 */
public final class WorkspaceFixtures {

    public static final Instant CREATED_AT = Instant.parse("2026-10-01T09:00:00Z");

    private WorkspaceFixtures() {
    }

    public static Workspace workspace(long id, String name) {
        return Workspace.seeded(id, name, CREATED_AT);
    }

    public static Workspace workspaceWithSources(long id, int sourceCount) {
        Workspace workspace = workspace(id, "Workspace " + id);
        for (int index = 1; index <= sourceCount; index++) {
            workspace.addSource(id * 100 + index, "Source " + index, SourceKind.MARKDOWN, CREATED_AT);
        }
        return workspace;
    }
}

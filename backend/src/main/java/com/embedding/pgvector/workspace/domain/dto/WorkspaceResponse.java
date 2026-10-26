package com.embedding.pgvector.workspace.domain.dto;

import com.embedding.pgvector.workspace.repository.WorkspaceSummary;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Bir çalışma alanını kaynak ve parça sayıları ve kullanılan embedding modelleriyle birlikte yanıt gövdesinde taşır.
 */
public record WorkspaceResponse(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long workspaceId,

        @Schema(example = "Belgelerim", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "Number of sources of the workspace (all statuses)", example = "1",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int sourceCount,

        @Schema(description = "Number of chunks of the workspace", example = "48",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long chunkCount,

        @ArraySchema(arraySchema = @Schema(description = "Distinct embeddingModel tags of the workspace chunks "
                + "(<model>@<dimensions>), sorted ascending. Empty when the workspace has no chunks. Normally one "
                + "tag; more than one only after a model change.", example = "[\"bge-m3@1024\"]",
                requiredMode = Schema.RequiredMode.REQUIRED))
        List<String> embeddingModels) {

    public WorkspaceResponse {
        embeddingModels = List.copyOf(embeddingModels);
    }

    public static WorkspaceResponse from(WorkspaceSummary summary, List<String> embeddingModels) {
        return new WorkspaceResponse(summary.getWorkspaceId(), summary.getName(),
                Math.toIntExact(summary.getSourceCount()), summary.getChunkCount(), embeddingModels);
    }
}

package com.embedding.pgvector.ingestion.domain.dto;

import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Bir içe alma işinin aşamasını, ilerlemesini ve sonucunu, durum adresiyle birlikte yanıt gövdesinde taşır.
 */
public record IngestionJobResponse(
        @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
        long jobId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long workspaceId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long sourceId,

        @Schema(types = {"string", "null"}, description = "Version label given with the upload")
        @Nullable String version,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String fileName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        IngestionStage stage,

        @Schema(minimum = "0", maximum = "1", requiredMode = Schema.RequiredMode.REQUIRED,
                description = "QUEUED 0.0, PARSING 0.05, CHUNKING 0.1, EMBEDDING 0.1 + 0.8 x embeddedChunks / "
                        + "totalChunks, WRITING 0.9, DONE 1.0. FAILED keeps the value it had when the job failed.")
        double progress,

        @Schema(description = "Chunks embedded so far; grows by one batch (at most 96) at a time",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int embeddedChunks,

        @Schema(types = {"integer", "null"}, format = "int32",
                description = "Number of chunks; null until the CHUNKING stage is finished")
        @Nullable Integer totalChunks,

        @Schema(types = {"string", "null"}, description = "Only when stage is FAILED")
        @Nullable IngestionErrorCode errorCode,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(types = {"string", "null"}, format = "date-time")
        @Nullable Instant startedAt,

        @Schema(types = {"string", "null"}, format = "date-time")
        @Nullable Instant finishedAt,

        @Schema(description = "Relative URL of this job", example = "/api/workspaces/42/ingestion-jobs/7",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String statusUrl) {

    public static IngestionJobResponse from(IngestionJob job) {
        return new IngestionJobResponse(job.getId(), job.getWorkspaceId(), job.getSourceId(), job.getVersion(),
                job.getFileName(), job.getStage(), job.getProgress(), job.getEmbeddedChunks(), job.getTotalChunks(),
                job.getErrorCode(), job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt(),
                statusUrl(job.getWorkspaceId(), job.getId()));
    }

    public static String statusUrl(long workspaceId, long jobId) {
        return "/api/workspaces/" + workspaceId + "/ingestion-jobs/" + jobId;
    }
}

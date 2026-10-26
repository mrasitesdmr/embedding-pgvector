package com.embedding.pgvector.source.domain.dto;

import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.source.domain.entity.SourceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Bir kaynağın bilgilerini ve en son içe alma işini yanıt gövdesinde taşır.
 */
public record SourceResponse(
        @Schema(example = "10001", requiredMode = Schema.RequiredMode.REQUIRED)
        long sourceId,

        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        long workspaceId,

        @Schema(example = "Atlaskap-Lojistik-Surec-El-Kitabi", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "MARKDOWN covers .txt, .md, .markdown. Uploads create only PDF, MARKDOWN and CSV. "
                + "WEB_CRAWL and TICKET_EXPORT belonged to the removed demo data; no source has them after the "
                + "0.5.0 migration. They stay in the enum for schema compatibility and cannot receive uploads.",
                example = "PDF", requiredMode = Schema.RequiredMode.REQUIRED)
        SourceKind kind,

        @Schema(types = {"string", "null"},
                description = "Version label of the last successful ingestion; null when none was given",
                example = "2026.1")
        @Nullable String version,

        @Schema(description = "Number of chunks of the current (last successful) version", example = "48",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int chunkCount,

        @Schema(description = "INGESTING while a job is running (old chunks stay searchable), READY when the last "
                + "successful version is in place, FAILED when the source never had a successful version.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        SourceStatus status,

        @Schema(types = {"string", "null"}, format = "date-time",
                description = "Time of the last successful ingestion; null if there was none")
        @Nullable Instant syncedAt,

        @Schema(types = {"string", "null"},
                description = "File name of the last successful version; null when there was none",
                example = "Atlaskap-Lojistik-Surec-El-Kitabi.pdf")
        @Nullable String fileName,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,

        @Schema(description = "The newest ingestion job of the source (highest jobId), in any stage, with the same "
                + "fields as GET /api/workspaces/{workspaceId}/ingestion-jobs/{jobId}. Null only when the source "
                + "never had a job; a source created by an upload always has one. Lets a client find a running job "
                + "after a page reload (stage not DONE / FAILED) and show why the last upload failed (stage = "
                + "FAILED, errorCode); a failed version upload leaves status as READY, so only this field tells that "
                + "it failed.")
        @Nullable IngestionJobResponse latestJob) {

    public static SourceResponse from(Source source, @Nullable IngestionJob latestJob) {
        return new SourceResponse(source.getId(), source.getWorkspaceId(), source.getName(), source.getKind(),
                source.getVersion(), source.getChunkCount(), source.getStatus(), source.getSyncedAt(),
                source.getFileName(), source.getCreatedAt(),
                latestJob == null ? null : IngestionJobResponse.from(latestJob));
    }
}

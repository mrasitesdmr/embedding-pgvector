package com.embedding.pgvector.ingestion.domain.entity;

import com.embedding.pgvector.source.domain.entity.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Bir kaynağa yüklenen tek bir dosyanın içe alma işini temsil eder; aşama geçişlerinde ilerleme oranını hesaplar
 * ve bitmiş işin değişmesine izin vermez.
 */
@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob {

    private static final double PARSING_PROGRESS = 0.05;
    private static final double CHUNKING_PROGRESS = 0.1;
    private static final double EMBEDDING_START_PROGRESS = 0.1;
    private static final double EMBEDDING_SHARE = 0.8;
    private static final double WRITING_PROGRESS = 0.9;
    private static final double DONE_PROGRESS = 1.0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    private @Nullable String version;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IngestionStage stage;

    @Column(nullable = false)
    private double progress;

    @Column(name = "embedded_chunks", nullable = false)
    private int embeddedChunks;

    @Column(name = "total_chunks")
    private @Nullable Integer totalChunks;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_code")
    private @Nullable IngestionErrorCode errorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private @Nullable Instant startedAt;

    @Column(name = "finished_at")
    private @Nullable Instant finishedAt;

    protected IngestionJob() {
    }

    private IngestionJob(Source source, @Nullable String version, String fileName, long fileSizeBytes,
            Instant createdAt) {
        this.workspaceId = source.getWorkspaceId();
        this.source = source;
        this.version = version;
        this.fileName = fileName;
        this.fileSizeBytes = fileSizeBytes;
        this.stage = IngestionStage.QUEUED;
        this.createdAt = createdAt;
    }

    public static IngestionJob queued(Source source, @Nullable String version, String fileName, long fileSizeBytes,
            Instant createdAt) {
        return new IngestionJob(source, version, fileName, fileSizeBytes, createdAt);
    }

    public void start(Instant now) {
        ensureActive();
        stage = IngestionStage.PARSING;
        progress = PARSING_PROGRESS;
        startedAt = now;
    }

    public void moveTo(IngestionStage nextStage) {
        ensureActive();
        progress = switch (nextStage) {
            case CHUNKING -> CHUNKING_PROGRESS;
            case EMBEDDING -> EMBEDDING_START_PROGRESS;
            case WRITING -> WRITING_PROGRESS;
            default -> throw new IllegalArgumentException("Stage cannot be entered directly: " + nextStage);
        };
        stage = nextStage;
    }

    public void recordTotalChunks(int total) {
        ensureActive();
        totalChunks = total;
    }

    public void recordEmbeddingProgress(int embedded) {
        ensureActive();
        if (totalChunks == null || totalChunks <= 0) {
            throw new IllegalStateException("Total chunks are not known for ingestion job: " + id);
        }
        embeddedChunks = embedded;
        progress = EMBEDDING_START_PROGRESS + EMBEDDING_SHARE * embedded / totalChunks;
    }

    public void complete(Instant now) {
        ensureActive();
        stage = IngestionStage.DONE;
        progress = DONE_PROGRESS;
        finishedAt = now;
    }

    public void fail(IngestionErrorCode code, Instant now) {
        ensureActive();
        stage = IngestionStage.FAILED;
        errorCode = code;
        finishedAt = now;
    }

    private void ensureActive() {
        if (!stage.isActive()) {
            throw new IllegalStateException("Ingestion job is already finished: " + id);
        }
    }

    public Long getId() {
        return id;
    }

    public long getWorkspaceId() {
        return workspaceId;
    }

    public long getSourceId() {
        return source.getId();
    }

    public Source getSource() {
        return source;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public String getFileName() {
        return fileName;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public IngestionStage getStage() {
        return stage;
    }

    public double getProgress() {
        return progress;
    }

    public int getEmbeddedChunks() {
        return embeddedChunks;
    }

    public @Nullable Integer getTotalChunks() {
        return totalChunks;
    }

    public @Nullable IngestionErrorCode getErrorCode() {
        return errorCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @Nullable Instant getFinishedAt() {
        return finishedAt;
    }
}

package com.embedding.pgvector.source.domain.entity;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

/**
 * Bir çalışma alanındaki bilgi kaynağını temsil eder; yüklenen dosyanın türünü denetler ve içe alma sonucuna göre
 * durumunu günceller.
 */
@Entity
@Table(name = "sources")
public class Source implements Persistable<Long> {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_key", nullable = false)
    private String nameKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceKind kind;

    private @Nullable String version;

    @Column(name = "file_name")
    private @Nullable String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceStatus status;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "synced_at")
    private @Nullable Instant syncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    protected Source() {
    }

    private Source(Workspace workspace, long id, String name, SourceKind kind, SourceStatus status,
            Instant createdAt) {
        this.workspace = workspace;
        this.id = id;
        this.name = name;
        this.nameKey = toNameKey(name);
        this.kind = kind;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Source ingesting(Workspace workspace, long id, String name, SourceKind kind, Instant createdAt) {
        return new Source(workspace, id, name, kind, SourceStatus.INGESTING, createdAt);
    }

    public static String toNameKey(String name) {
        String composed = Normalizer.normalize(name, Normalizer.Form.NFC);
        return composed.replace('İ', 'i').replace('I', 'i').replace('ı', 'i').toLowerCase(Locale.ROOT);
    }

    public void ensureAcceptsUpload(SourceKind fileKind) {
        if (!kind.isUploadable() || kind != fileKind) {
            throw new ConflictException("SOURCE_KIND_MISMATCH",
                    "File kind " + fileKind + " does not match source kind " + kind);
        }
    }

    public void startIngestion() {
        status = SourceStatus.INGESTING;
    }

    public void completeIngestion(@Nullable String newVersion, String newFileName, int newChunkCount,
            Instant newSyncedAt) {
        version = newVersion;
        fileName = newFileName;
        chunkCount = newChunkCount;
        syncedAt = newSyncedAt;
        status = SourceStatus.READY;
    }

    public void failIngestion() {
        status = hasSuccessfulVersion() ? SourceStatus.READY : SourceStatus.FAILED;
    }

    public boolean hasSuccessfulVersion() {
        return syncedAt != null;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        newEntity = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public long getWorkspaceId() {
        return workspace.getId();
    }

    public String getName() {
        return name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public SourceKind getKind() {
        return kind;
    }

    public @Nullable String getVersion() {
        return version;
    }

    public @Nullable String getFileName() {
        return fileName;
    }

    public SourceStatus getStatus() {
        return status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public @Nullable Instant getSyncedAt() {
        return syncedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

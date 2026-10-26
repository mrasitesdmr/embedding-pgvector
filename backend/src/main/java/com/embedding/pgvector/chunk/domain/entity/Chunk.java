package com.embedding.pgvector.chunk.domain.entity;

import com.embedding.pgvector.ingestion.chunking.ChunkDraft;
import com.embedding.pgvector.source.domain.entity.Source;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/**
 * Bir kaynaktan çıkarılan ve embedding vektörüyle birlikte saklanan tek bir metin parçasını temsil eder.
 */
@Entity
@Table(name = "chunks")
public class Chunk {

    public static final int EMBEDDING_DIMENSIONS = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chunk_id")
    @SequenceGenerator(name = "chunk_id", sequenceName = "chunks_id_seq", allocationSize = 50)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_id", nullable = false)
    private Source source;

    @Column(name = "chunk_no", nullable = false)
    private int chunkNo;

    private @Nullable String heading;

    @Column(nullable = false)
    private String content;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = EMBEDDING_DIMENSIONS)
    @Column(nullable = false)
    private float[] embedding;

    @Column(name = "embedding_model", nullable = false)
    private String embeddingModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Chunk() {
    }

    private Chunk(Source source, ChunkDraft draft, float[] embedding, String embeddingModel, Instant createdAt) {
        this.workspaceId = source.getWorkspaceId();
        this.source = source;
        this.chunkNo = draft.chunkNo();
        this.heading = draft.heading();
        this.content = draft.content();
        this.tokenCount = draft.tokenCount();
        this.embedding = embedding;
        this.embeddingModel = embeddingModel;
        this.createdAt = createdAt;
    }

    public static Chunk create(Source source, ChunkDraft draft, float[] embedding, String embeddingModel,
            Instant createdAt) {
        return new Chunk(source, draft, embedding, embeddingModel, createdAt);
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

    public int getChunkNo() {
        return chunkNo;
    }

    public @Nullable String getHeading() {
        return heading;
    }

    public String getContent() {
        return content;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

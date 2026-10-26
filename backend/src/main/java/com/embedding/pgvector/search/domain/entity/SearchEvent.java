package com.embedding.pgvector.search.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.embedding.pgvector.source.domain.entity.Source;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Yapılmış tek bir aramanın kaydını temsil eder; tekrarlanan aramaları gruplamak için arama metninden normalize bir
 * anahtar üretir.
 */
@Entity
@Table(name = "search_events")
public class SearchEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private long workspaceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    private @Nullable String channel;

    @Column(nullable = false)
    private String query;

    @Column(name = "query_key", nullable = false)
    private String queryKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SearchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "filter_strategy")
    private @Nullable FilterStrategy filterStrategy;

    @Column(name = "ef_search")
    private @Nullable Integer efSearch;

    @Column(name = "result_limit")
    private @Nullable Integer resultLimit;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "top_chunk_id")
    private @Nullable Long topChunkId;

    @Column(name = "top_chunk_heading")
    private @Nullable String topChunkHeading;

    @Column(name = "total_ms", nullable = false)
    private long totalMs;

    @Column(name = "keyword_count")
    private @Nullable Integer keywordCount;

    @Column(name = "best_distance")
    private @Nullable Double bestDistance;

    protected SearchEvent() {
    }

    private SearchEvent(long workspaceId, Instant createdAt, @Nullable String channel, String query, SearchMode mode,
            @Nullable FilterStrategy filterStrategy, @Nullable Integer efSearch, int resultLimit, int resultCount,
            @Nullable Long topChunkId, @Nullable String topChunkHeading, long totalMs, @Nullable Integer keywordCount,
            @Nullable Double bestDistance) {
        this.workspaceId = workspaceId;
        this.createdAt = createdAt;
        this.channel = channel;
        this.query = query;
        this.queryKey = toQueryKey(query);
        this.mode = mode;
        this.filterStrategy = filterStrategy;
        this.efSearch = efSearch;
        this.resultLimit = resultLimit;
        this.resultCount = resultCount;
        this.topChunkId = topChunkId;
        this.topChunkHeading = topChunkHeading;
        this.totalMs = totalMs;
        this.keywordCount = keywordCount;
        this.bestDistance = bestDistance;
    }

    public static SearchEvent record(long workspaceId, Instant createdAt, @Nullable String channel, String query,
            SearchMode mode, @Nullable FilterStrategy filterStrategy, @Nullable Integer efSearch, int resultLimit,
            int resultCount, @Nullable Long topChunkId, @Nullable String topChunkHeading, long totalMs,
            @Nullable Integer keywordCount, @Nullable Double bestDistance) {
        return new SearchEvent(workspaceId, createdAt, channel, query, mode, filterStrategy, efSearch, resultLimit,
                resultCount, topChunkId, topChunkHeading, totalMs, keywordCount, bestDistance);
    }

    public static String toQueryKey(String query) {
        return Source.toNameKey(query.strip().replaceAll("\\s+", " "));
    }

    public Long getId() {
        return id;
    }

    public long getWorkspaceId() {
        return workspaceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable String getChannel() {
        return channel;
    }

    public String getQuery() {
        return query;
    }

    public String getQueryKey() {
        return queryKey;
    }

    public SearchMode getMode() {
        return mode;
    }

    public @Nullable FilterStrategy getFilterStrategy() {
        return filterStrategy;
    }

    public @Nullable Integer getEfSearch() {
        return efSearch;
    }

    public @Nullable Integer getResultLimit() {
        return resultLimit;
    }

    public int getResultCount() {
        return resultCount;
    }

    public @Nullable Long getTopChunkId() {
        return topChunkId;
    }

    public @Nullable String getTopChunkHeading() {
        return topChunkHeading;
    }

    public long getTotalMs() {
        return totalMs;
    }

    public @Nullable Integer getKeywordCount() {
        return keywordCount;
    }

    public @Nullable Double getBestDistance() {
        return bestDistance;
    }
}

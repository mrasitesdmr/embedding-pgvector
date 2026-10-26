package com.embedding.pgvector.search.service;

import com.embedding.pgvector.config.SearchProperties;
import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.RetrievalResult;
import com.embedding.pgvector.search.domain.entity.RetrievedHit;
import com.embedding.pgvector.search.repository.ChunkSearchRepository;
import com.embedding.pgvector.search.repository.RetrievedHitView;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seçilen arama modunun sorgusunu çalıştırır; vektör aramasından önce HNSW ve filtre ayarlarını aynı işlemde uygular.
 */
@Service
public class ChunkRetrievalService {

    private static final String SORT_OFF = "off";
    private static final String SORT_ON = "on";

    private final ChunkSearchRepository chunkSearchRepository;
    private final SearchProperties properties;

    public ChunkRetrievalService(ChunkSearchRepository chunkSearchRepository, SearchProperties properties) {
        this.chunkSearchRepository = chunkSearchRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieveKeyword(long workspaceId, String query, int limit) {
        List<RetrievedHitView> rows = chunkSearchRepository.searchKeyword(workspaceId, query, limit);
        return new RetrievalResult(toHits(rows), rows.size(), null);
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieveVector(long workspaceId, float[] queryEmbedding, int limit, int efSearch,
            FilterStrategy filterStrategy) {
        applySearchSettings(efSearch, filterStrategy);
        List<RetrievedHitView> rows = chunkSearchRepository.searchVector(workspaceId,
                VectorLiteral.format(queryEmbedding), limit);
        return new RetrievalResult(toHits(rows), null, rows.size());
    }

    @Transactional(readOnly = true)
    public RetrievalResult retrieveHybrid(long workspaceId, String query, float[] queryEmbedding, int limit,
            int efSearch, FilterStrategy filterStrategy) {
        applySearchSettings(efSearch, filterStrategy);
        List<RetrievedHitView> rows = chunkSearchRepository.searchHybrid(workspaceId, query,
                VectorLiteral.format(queryEmbedding), limit, properties.rrfK());
        int keywordCount = rows.isEmpty() ? 0 : toInt(rows.getFirst().getKeywordCount());
        int vectorCount = rows.isEmpty() ? 0 : toInt(rows.getFirst().getVectorCount());
        return new RetrievalResult(toHits(rows), keywordCount, vectorCount);
    }

    @Transactional(readOnly = true)
    public String explainVector(long workspaceId, float[] queryEmbedding, int limit, int efSearch,
            FilterStrategy filterStrategy) {
        applySearchSettings(efSearch, filterStrategy);
        return String.join("\n",
                chunkSearchRepository.explainVector(workspaceId, VectorLiteral.format(queryEmbedding), limit));
    }

    private void applySearchSettings(int efSearch, FilterStrategy filterStrategy) {
        chunkSearchRepository.applySearchSettings(String.valueOf(efSearch), filterStrategy.iterativeScanValue(),
                String.valueOf(properties.maxScanTuples()), properties.forceVectorIndex() ? SORT_OFF : SORT_ON);
    }

    private static List<RetrievedHit> toHits(List<RetrievedHitView> rows) {
        return rows.stream()
                .map(row -> new RetrievedHit(row.getChunkId(), row.getChunkNo(), row.getSourceId(),
                        row.getSourceName(), row.getHeading(), row.getContent(), toInteger(row.getKeywordRank()),
                        row.getKeywordScore(), toInteger(row.getVectorRank()), row.getDistance(), row.getRrfScore()))
                .toList();
    }

    private static @Nullable Integer toInteger(@Nullable Long value) {
        return value == null ? null : Math.toIntExact(value);
    }

    private static int toInt(@Nullable Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }
}

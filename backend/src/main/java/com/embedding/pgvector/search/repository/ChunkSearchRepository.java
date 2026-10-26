package com.embedding.pgvector.search.repository;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Kelime, vektör ve hibrit arama sorgularını PostgreSQL ve pgvector üzerinde çalıştırır; HNSW ayarlarını uygular,
 * vektör sorgusunun planını çıkarır ve vurgulama için kelime köklerini bulur.
 */
public interface ChunkSearchRepository extends Repository<Chunk, Long> {

    String KEYWORD_CTE = """
            keyword AS (
                SELECT id,
                       CAST(ts_rank_cd(tsv, plainto_tsquery('turkish', :query)) AS double precision) AS keyword_score,
                       ROW_NUMBER() OVER (ORDER BY ts_rank_cd(tsv, plainto_tsquery('turkish', :query)) DESC, id) AS rank
                FROM chunks
                WHERE workspace_id = :workspaceId
                  AND tsv @@ plainto_tsquery('turkish', :query)
                ORDER BY keyword_score DESC, id
                LIMIT :limit
            )
            """;

    String SEMANTIC_QUERY = """
            SELECT id, embedding <=> CAST(:queryEmbedding AS vector) AS distance
            FROM chunks
            WHERE workspace_id = :workspaceId
            ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
            LIMIT :limit
            """;

    String SEMANTIC_CTES = "semantic AS MATERIALIZED (\n" + SEMANTIC_QUERY + "),\n" + """
            semantic_ranked AS (
                SELECT id, distance, ROW_NUMBER() OVER (ORDER BY distance, id) AS rank
                FROM semantic
            )
            """;

    @Query(nativeQuery = true, value = "WITH " + KEYWORD_CTE + """
            SELECT k.id AS "chunkId", c.chunk_no AS "chunkNo",
                   c.source_id AS "sourceId", s.name AS "sourceName",
                   c.heading AS "heading", c.content AS "content",
                   k.rank AS "keywordRank", k.keyword_score AS "keywordScore",
                   CAST(NULL AS bigint) AS "vectorRank", CAST(NULL AS double precision) AS "distance",
                   CAST(NULL AS double precision) AS "rrfScore",
                   (SELECT count(*) FROM keyword) AS "keywordCount", CAST(NULL AS bigint) AS "vectorCount"
            FROM keyword k
            JOIN chunks c ON c.id = k.id
            JOIN sources s ON s.id = c.source_id
            ORDER BY k.rank
            """)
    List<RetrievedHitView> searchKeyword(@Param("workspaceId") long workspaceId, @Param("query") String query,
            @Param("limit") int limit);

    @Query(nativeQuery = true, value = "WITH " + SEMANTIC_CTES + """
            SELECT r.id AS "chunkId", c.chunk_no AS "chunkNo",
                   c.source_id AS "sourceId", s.name AS "sourceName",
                   c.heading AS "heading", c.content AS "content",
                   CAST(NULL AS bigint) AS "keywordRank", CAST(NULL AS double precision) AS "keywordScore",
                   r.rank AS "vectorRank", r.distance AS "distance", CAST(NULL AS double precision) AS "rrfScore",
                   CAST(NULL AS bigint) AS "keywordCount", (SELECT count(*) FROM semantic) AS "vectorCount"
            FROM semantic_ranked r
            JOIN chunks c ON c.id = r.id
            JOIN sources s ON s.id = c.source_id
            ORDER BY r.rank
            """)
    List<RetrievedHitView> searchVector(@Param("workspaceId") long workspaceId,
            @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    @Query(nativeQuery = true, value = "WITH " + SEMANTIC_CTES + ",\n" + KEYWORD_CTE + """
            SELECT COALESCE(r.id, k.id) AS "chunkId", c.chunk_no AS "chunkNo",
                   c.source_id AS "sourceId", s.name AS "sourceName",
                   c.heading AS "heading", c.content AS "content",
                   k.rank AS "keywordRank", k.keyword_score AS "keywordScore",
                   r.rank AS "vectorRank", r.distance AS "distance",
                   CAST(COALESCE(1.0 / (:rrfK + r.rank), 0) + COALESCE(1.0 / (:rrfK + k.rank), 0) AS double precision)
                       AS "rrfScore",
                   (SELECT count(*) FROM keyword) AS "keywordCount",
                   (SELECT count(*) FROM semantic) AS "vectorCount"
            FROM semantic_ranked r
            FULL OUTER JOIN keyword k ON r.id = k.id
            JOIN chunks c ON c.id = COALESCE(r.id, k.id)
            JOIN sources s ON s.id = c.source_id
            ORDER BY "rrfScore" DESC, r.rank NULLS LAST, "chunkId"
            LIMIT :limit
            """)
    List<RetrievedHitView> searchHybrid(@Param("workspaceId") long workspaceId, @Param("query") String query,
            @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit, @Param("rrfK") int rrfK);

    @Query(nativeQuery = true, value = "EXPLAIN (ANALYZE, BUFFERS, COSTS OFF)\n" + SEMANTIC_QUERY)
    List<String> explainVector(@Param("workspaceId") long workspaceId,
            @Param("queryEmbedding") String queryEmbedding, @Param("limit") int limit);

    @Query(nativeQuery = true, value = """
            SELECT set_config('hnsw.ef_search', :efSearch, true) AS "efSearch",
                   set_config('hnsw.iterative_scan', :iterativeScan, true) AS "iterativeScan",
                   set_config('hnsw.max_scan_tuples', :maxScanTuples, true) AS "maxScanTuples",
                   set_config('enable_sort', :enableSort, true) AS "enableSort"
            """)
    AppliedSearchSettings applySearchSettings(@Param("efSearch") String efSearch,
            @Param("iterativeScan") String iterativeScan, @Param("maxScanTuples") String maxScanTuples,
            @Param("enableSort") String enableSort);

    @Query(nativeQuery = true,
            value = "SELECT DISTINCT unnest(tsvector_to_array(to_tsvector('turkish', :query)))")
    List<String> findQueryLexemes(@Param("query") String query);

    @Query(nativeQuery = true, value = """
            SELECT w AS "word", unnest(tsvector_to_array(to_tsvector('turkish', w))) AS "lexeme"
            FROM unnest(string_to_array(:words, ' ')) AS w
            """)
    List<WordLexemeView> findLexemes(@Param("words") String spaceSeparatedWords);
}

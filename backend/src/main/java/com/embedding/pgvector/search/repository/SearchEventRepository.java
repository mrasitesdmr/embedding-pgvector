package com.embedding.pgvector.search.repository;

import com.embedding.pgvector.search.domain.entity.SearchEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Arama kayıtlarını yazar ve sayfa sayfa okur; arama istatistiklerini ve sonuç bulan en sık aramaları sorgular.
 */
public interface SearchEventRepository extends JpaRepository<SearchEvent, Long> {

    Page<SearchEvent> findByWorkspaceIdOrderByCreatedAtDescIdDesc(long workspaceId, Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT count(*) AS "totalCount",
                   count(*) FILTER (WHERE created_at >= :todayStart) AS "todayCount",
                   count(*) FILTER (WHERE created_at >= :last7DaysStart) AS "last7DaysCount",
                   count(*) FILTER (WHERE created_at >= :last7DaysStart AND result_count = 0) AS "zeroResultCount",
                   count(*) FILTER (WHERE created_at >= :last7DaysStart AND result_count >= 1
                                      AND result_limit IS NOT NULL AND result_count < result_limit)
                       AS "lowResultCount"
            FROM search_events
            WHERE workspace_id = :workspaceId
            """)
    SearchEventStatsView countStats(@Param("workspaceId") long workspaceId, @Param("todayStart") Instant todayStart,
            @Param("last7DaysStart") Instant last7DaysStart);

    @Query(nativeQuery = true, value = """
            WITH counted AS (
                SELECT id, query, query_key, created_at
                FROM search_events
                WHERE workspace_id = :workspaceId
                  AND result_count >= 1
                  AND (keyword_count >= 1 OR best_distance <= :maxDistance)
            ),
            grouped AS (
                SELECT query_key, count(*) AS search_count, max(created_at) AS last_searched_at, max(id) AS last_id
                FROM counted
                GROUP BY query_key
            ),
            latest AS (
                SELECT DISTINCT ON (query_key) query_key, query
                FROM counted
                ORDER BY query_key, created_at DESC, id DESC
            )
            SELECT latest.query AS "query",
                   grouped.search_count AS "searchCount",
                   grouped.last_searched_at AS "lastSearchedAt"
            FROM grouped
            JOIN latest ON latest.query_key = grouped.query_key
            ORDER BY grouped.search_count DESC, grouped.last_searched_at DESC, grouped.last_id DESC
            LIMIT :limit
            """)
    List<PopularQueryView> findPopularQueries(@Param("workspaceId") long workspaceId,
            @Param("maxDistance") double maxDistance, @Param("limit") int limit);
}

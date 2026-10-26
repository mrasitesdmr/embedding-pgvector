package com.embedding.pgvector.chunk.repository;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Parçaları kaynağa göre okur ve siler; çalışma alanlarında hangi embedding modellerinin kullanıldığını sorgular.
 */
public interface ChunkRepository extends JpaRepository<Chunk, Long> {

    @Query(value = """
            select c.id as id, c.chunkNo as chunkNo, c.heading as heading, c.content as content,
                   c.tokenCount as tokenCount, c.embeddingModel as embeddingModel
            from Chunk c
            where c.source.id = :sourceId
            order by c.chunkNo, c.id
            """, countQuery = "select count(c) from Chunk c where c.source.id = :sourceId")
    Page<ChunkView> findBySourceIdOrderByChunkNoAscIdAsc(@Param("sourceId") long sourceId, Pageable pageable);

    @Query("""
            select c.id as id, c.chunkNo as chunkNo, c.heading as heading, c.content as content,
                   c.tokenCount as tokenCount, c.embeddingModel as embeddingModel
            from Chunk c
            where c.source.id = :sourceId and c.id = :chunkId
            """)
    Optional<ChunkView> findViewBySourceIdAndId(@Param("sourceId") long sourceId, @Param("chunkId") long chunkId);

    @Query("""
            select distinct c.workspaceId as workspaceId, c.embeddingModel as embeddingModel
            from Chunk c
            where c.workspaceId in :workspaceIds
            """)
    List<WorkspaceEmbeddingModelView> findEmbeddingModelsByWorkspaceIds(
            @Param("workspaceIds") Collection<Long> workspaceIds);

    @Modifying(flushAutomatically = true)
    @Query("delete from Chunk c where c.source.id = :sourceId")
    int deleteBySourceId(@Param("sourceId") long sourceId);

    @Query("select distinct c.embeddingModel from Chunk c where c.workspaceId = :workspaceId")
    List<String> findEmbeddingModels(@Param("workspaceId") long workspaceId);

    @Query("""
            select c.embeddingModel as embeddingModel, count(distinct c.workspaceId) as workspaceCount
            from Chunk c
            group by c.embeddingModel
            order by c.embeddingModel
            """)
    List<EmbeddingModelUsage> countWorkspacesByEmbeddingModel();
}

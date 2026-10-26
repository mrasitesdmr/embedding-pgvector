package com.embedding.pgvector.ingestion.repository;

import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * İçe alma işlerini okur ve yazar; bitmemiş işleri, kaynağın süren işini ve kaynakların en son işini sorgular.
 */
public interface IngestionJobRepository extends JpaRepository<IngestionJob, Long> {

    Optional<IngestionJob> findByWorkspaceIdAndId(long workspaceId, long id);

    @Query("""
            select count(j) > 0 from IngestionJob j
            where j.source.id = :sourceId and j.stage not in :stages
            """)
    boolean existsBySourceIdAndStageNotIn(@Param("sourceId") long sourceId,
            @Param("stages") Collection<IngestionStage> stages);

    List<IngestionJob> findByStageNotIn(Collection<IngestionStage> stages);

    @Query("""
            select j from IngestionJob j
            where j.source.id in :sourceIds
              and j.id = (select max(j2.id) from IngestionJob j2 where j2.source.id = j.source.id)
            """)
    List<IngestionJob> findLatestBySourceIds(@Param("sourceIds") Collection<Long> sourceIds);
}

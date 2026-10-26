package com.embedding.pgvector.workspace.repository;

import com.embedding.pgvector.workspace.domain.entity.Workspace;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Çalışma alanlarını kaynak ve parça sayılarıyla okur; kaynak eklemeden önce çalışma alanı satırını kilitler.
 */
public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {

    String SUMMARY_SELECT = """
            select w.id as workspaceId, w.name as name,
                   (select count(s) from Source s where s.workspace = w) as sourceCount,
                   (select count(c) from Chunk c where c.workspaceId = w.id) as chunkCount
            from Workspace w
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = :workspaceId")
    Optional<Workspace> findByIdForUpdate(@Param("workspaceId") long workspaceId);

    @Query(value = SUMMARY_SELECT + " order by w.id", countQuery = "select count(w) from Workspace w")
    Page<WorkspaceSummary> findSummaries(Pageable pageable);

    @Query(SUMMARY_SELECT + " where w.id = :workspaceId")
    Optional<WorkspaceSummary> findSummaryById(@Param("workspaceId") long workspaceId);
}

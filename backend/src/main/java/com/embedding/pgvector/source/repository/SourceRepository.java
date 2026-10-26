package com.embedding.pgvector.source.repository;

import com.embedding.pgvector.source.domain.entity.Source;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Kaynakları çalışma alanına göre okur ve yazar; kaynak satırını kilitleyerek getirir ve yeni kaynak kimliğini üretir.
 */
public interface SourceRepository extends JpaRepository<Source, Long> {

    @Query("select s from Source s where s.workspace.id = :workspaceId and s.id = :id")
    Optional<Source> findByWorkspaceIdAndId(@Param("workspaceId") long workspaceId, @Param("id") long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Source s where s.workspace.id = :workspaceId and s.id = :sourceId")
    Optional<Source> findByWorkspaceIdAndIdForUpdate(@Param("workspaceId") long workspaceId,
            @Param("sourceId") long sourceId);

    @Query(value = "select s from Source s where s.workspace.id = :workspaceId order by s.id",
            countQuery = "select count(s) from Source s where s.workspace.id = :workspaceId")
    Page<Source> findByWorkspaceIdOrderByIdAsc(@Param("workspaceId") long workspaceId, Pageable pageable);

    @Query(value = "SELECT nextval('sources_id_seq')", nativeQuery = true)
    long nextSourceId();
}

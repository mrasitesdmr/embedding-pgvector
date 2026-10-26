package com.embedding.pgvector.workspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.ChunkFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.TestcontainersConfiguration;
import com.embedding.pgvector.support.VectorFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * WorkspaceRepository'nin kaynak ve parça sayılı özet sorgularını, sıralı sayfalamayı ve kilitli okumayı
 * gerçek PostgreSQL ile doğrular.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class WorkspaceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private void persistPrimaryWithTwoSourcesAndThreeChunks() {
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        entityManager.persist(primary);
        Source help = SourceFixtures.readySource(primary, 301L, "İzin notları", SourceKind.WEB_CRAWL, null, 2);
        Source manual = SourceFixtures.readySource(primary, 302L, "Kılavuz", SourceKind.PDF, "3.1", 1);
        entityManager.persist(help);
        entityManager.persist(manual);
        entityManager.persist(ChunkFixtures.chunk(help, 0, "a", "içerik a", VectorFixtures.unit(0)));
        entityManager.persist(ChunkFixtures.chunk(help, 1, "b", "içerik b", VectorFixtures.unit(1)));
        entityManager.persist(ChunkFixtures.chunk(manual, 0, "c", "içerik c", VectorFixtures.unit(2)));
    }

    @Test
    void givenWorkspaceWithTwoSourcesAndThreeChunks_whenFindSummaryById_thenReturnsCounts() {
        // Arrange
        persistPrimaryWithTwoSourcesAndThreeChunks();
        Workspace empty = WorkspaceFixtures.workspace(19L, "Deneme alanı");
        entityManager.persist(empty);
        entityManager.flush();
        entityManager.clear();

        // Act
        WorkspaceSummary summary = workspaceRepository.findSummaryById(42L).orElseThrow();
        WorkspaceSummary emptySummary = workspaceRepository.findSummaryById(19L).orElseThrow();

        // Assert
        assertThat(summary.getWorkspaceId()).isEqualTo(42L);
        assertThat(summary.getName()).isEqualTo("Belgelerim");
        assertThat(summary.getSourceCount()).isEqualTo(2);
        assertThat(summary.getChunkCount()).isEqualTo(3);
        assertThat(emptySummary.getSourceCount()).isZero();
        assertThat(emptySummary.getChunkCount()).isZero();
        assertThat(workspaceRepository.findSummaryById(999L)).isEmpty();
    }

    @Test
    void givenSeveralWorkspaces_whenFindSummaries_thenOrderedByIdWithPagingTotals() {
        // Arrange
        persistPrimaryWithTwoSourcesAndThreeChunks();
        entityManager.persist(WorkspaceFixtures.workspace(19L, "Deneme alanı"));
        entityManager.persist(WorkspaceFixtures.workspace(7L, "Lodos Enerji"));
        entityManager.flush();
        entityManager.clear();

        // Act
        var firstPage = workspaceRepository.findSummaries(PageRequest.of(0, 2));
        var secondPage = workspaceRepository.findSummaries(PageRequest.of(1, 2));

        // Assert
        assertThat(firstPage.getContent()).extracting(WorkspaceSummary::getWorkspaceId).containsExactly(7L, 19L);
        assertThat(secondPage.getContent()).extracting(WorkspaceSummary::getWorkspaceId).containsExactly(42L);
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(secondPage.getContent().getFirst().getChunkCount()).isEqualTo(3);
    }

    @Test
    void givenWorkspace_whenFindByIdForUpdate_thenReturnsItAndUnknownIsEmpty() {
        // Arrange
        entityManager.persist(WorkspaceFixtures.workspace(42L, "Belgelerim"));
        entityManager.flush();
        entityManager.clear();

        // Act
        var found = workspaceRepository.findByIdForUpdate(42L);
        var missing = workspaceRepository.findByIdForUpdate(999L);

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().isNew()).isFalse();
        assertThat(missing).isEmpty();
    }
}

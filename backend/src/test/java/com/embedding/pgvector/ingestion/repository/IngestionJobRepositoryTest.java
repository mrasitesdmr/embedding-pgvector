package com.embedding.pgvector.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.IngestionJobFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.TestcontainersConfiguration;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * IngestionJobRepository'nin tek etkin iş kuralını, aşama sorgularını ve çalışma alanı kapsamını gerçek
 * PostgreSQL ile doğrular.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class IngestionJobRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private IngestionJobRepository ingestionJobRepository;

    private Source persistSource(long workspaceId, long sourceId) {
        Workspace workspace = entityManager.find(Workspace.class, workspaceId);
        if (workspace == null) {
            workspace = WorkspaceFixtures.workspace(workspaceId, "Workspace " + workspaceId);
            entityManager.persist(workspace);
        }
        Source source = SourceFixtures.readySource(workspace, sourceId, SourceKind.PDF, "3.1", 5);
        entityManager.persist(source);
        return source;
    }

    @Test
    void givenActiveJobForSource_whenPersistSecondActiveJob_thenPartialUniqueIndexRejects() {
        // Arrange
        Source source = persistSource(42L, 302L);
        entityManager.persist(IngestionJobFixtures.queued(source));

        // Act
        Throwable thrown = catchThrowable(() -> entityManager.persistAndFlush(IngestionJobFixtures.queued(source)));

        // Assert
        assertThat(thrown).isNotNull();
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        assertThat(root.getMessage()).contains("uk_ingestion_jobs_source_id_active");
    }

    @Test
    void givenDoneJob_whenPersistNewActiveJob_thenAccepted() {
        // Arrange
        Source source = persistSource(42L, 302L);
        IngestionJob done = IngestionJobFixtures.queued(source);
        done.complete(NOW);
        IngestionJob failed = IngestionJobFixtures.queued(source);
        failed.fail(IngestionErrorCode.INTERNAL_ERROR, NOW);
        entityManager.persist(done);
        entityManager.persist(failed);
        entityManager.persist(IngestionJobFixtures.queued(source));

        // Act
        Throwable thrown = catchThrowable(() -> entityManager.flush());

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void givenActiveJobsOnDifferentSources_whenFlush_thenBothAccepted() {
        // Arrange
        entityManager.persist(IngestionJobFixtures.queued(persistSource(42L, 302L)));
        entityManager.persist(IngestionJobFixtures.queued(persistSource(42L, 303L)));

        // Act
        Throwable thrown = catchThrowable(() -> entityManager.flush());

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void givenJobs_whenExistsBySourceIdAndStageNotIn_thenTrueOnlyForActiveJob() {
        // Arrange
        Source withActive = persistSource(42L, 302L);
        Source withDoneOnly = persistSource(42L, 303L);
        Source withNoJob = persistSource(42L, 304L);
        entityManager.persist(IngestionJobFixtures.queued(withActive));
        IngestionJob done = IngestionJobFixtures.queued(withDoneOnly);
        done.complete(NOW);
        entityManager.persist(done);
        entityManager.flush();
        entityManager.clear();

        // Act
        boolean active = ingestionJobRepository.existsBySourceIdAndStageNotIn(302L, IngestionStage.FINISHED);
        boolean onlyDone = ingestionJobRepository.existsBySourceIdAndStageNotIn(303L, IngestionStage.FINISHED);
        boolean none = ingestionJobRepository.existsBySourceIdAndStageNotIn(304L, IngestionStage.FINISHED);

        // Assert
        assertThat(withNoJob.getId()).isEqualTo(304L);
        assertThat(active).isTrue();
        assertThat(onlyDone).isFalse();
        assertThat(none).isFalse();
    }

    @Test
    void givenJobs_whenFindByStageNotIn_thenOnlyUnfinishedJobs() {
        // Arrange
        Source first = persistSource(42L, 302L);
        Source second = persistSource(42L, 303L);
        IngestionJob done = IngestionJobFixtures.queued(first);
        done.complete(NOW);
        IngestionJob embedding = IngestionJobFixtures.queued(second);
        embedding.start(NOW);
        embedding.moveTo(IngestionStage.EMBEDDING);
        entityManager.persist(done);
        entityManager.persist(embedding);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<IngestionJob> unfinished = ingestionJobRepository.findByStageNotIn(IngestionStage.FINISHED);

        // Assert
        assertThat(unfinished).hasSize(1);
        assertThat(unfinished.getFirst().getStage()).isEqualTo(IngestionStage.EMBEDDING);
        assertThat(unfinished.getFirst().getSourceId()).isEqualTo(303L);
    }

    @Test
    void givenJobOfWorkspace_whenFindByWorkspaceIdAndId_thenOtherWorkspaceSeesNothing() {
        // Arrange
        Source source = persistSource(42L, 302L);
        persistSource(19L, 999L);
        IngestionJob job = IngestionJobFixtures.queued(source);
        entityManager.persist(job);
        entityManager.flush();
        entityManager.clear();

        // Act
        var own = ingestionJobRepository.findByWorkspaceIdAndId(42L, job.getId());
        var foreign = ingestionJobRepository.findByWorkspaceIdAndId(19L, job.getId());

        // Assert
        assertThat(own).isPresent();
        assertThat(own.get().getFileName()).isEqualTo("file.pdf");
        assertThat(own.get().getStage()).isEqualTo(IngestionStage.QUEUED);
        assertThat(foreign).isEmpty();
    }

    @Test
    void givenJobsOfSeveralSources_whenFindLatestBySourceIds_thenReturnsHighestIdPerSource() {
        // Arrange
        Source sourceA = persistSource(42L, 302L);
        Source sourceB = persistSource(42L, 303L);
        Source sourceC = persistSource(42L, 304L);
        IngestionJob firstOfA = IngestionJobFixtures.queued(sourceA);
        firstOfA.fail(IngestionErrorCode.INTERNAL_ERROR, NOW);
        entityManager.persist(firstOfA);
        IngestionJob onlyOfB = IngestionJobFixtures.queued(sourceB);
        onlyOfB.start(NOW);
        onlyOfB.moveTo(IngestionStage.EMBEDDING);
        entityManager.persist(onlyOfB);
        IngestionJob secondOfA = IngestionJobFixtures.queued(sourceA);
        secondOfA.complete(NOW);
        entityManager.persist(secondOfA);
        IngestionJob thirdOfA = IngestionJobFixtures.queued(sourceA);
        thirdOfA.complete(NOW);
        entityManager.persist(thirdOfA);
        entityManager.flush();
        entityManager.clear();

        // Act
        List<IngestionJob> latest = ingestionJobRepository.findLatestBySourceIds(List.of(302L, 303L, 304L));
        List<IngestionJob> onlyJoblessSource = ingestionJobRepository.findLatestBySourceIds(List.of(304L));

        // Assert
        assertThat(sourceC.getId()).isEqualTo(304L);
        assertThat(latest).extracting(IngestionJob::getId)
                .containsExactlyInAnyOrder(thirdOfA.getId(), onlyOfB.getId());
        assertThat(latest).filteredOn(job -> job.getSourceId() == 302L).singleElement()
                .extracting(IngestionJob::getStage).isEqualTo(IngestionStage.DONE);
        assertThat(onlyJoblessSource).isEmpty();
    }
}

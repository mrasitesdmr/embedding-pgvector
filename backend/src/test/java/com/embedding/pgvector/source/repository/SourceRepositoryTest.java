package com.embedding.pgvector.source.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.TestcontainersConfiguration;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

/**
 * SourceRepository'nin çalışma alanı kapsamını, ad anahtarı tekilliğini, sıra numarasını ve alan kaydını
 * gerçek PostgreSQL ile doğrular.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class SourceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private SourceRepository sourceRepository;

    @Test
    void givenSourceOfOtherWorkspace_whenFindByWorkspaceIdAndIdForUpdate_thenEmpty() {
        // Arrange
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Workspace kiyi = WorkspaceFixtures.workspace(19L, "Deneme alanı");
        entityManager.persist(primary);
        entityManager.persist(kiyi);
        entityManager.persist(SourceFixtures.readySource(primary, 301L, SourceKind.WEB_CRAWL, null, 0));
        entityManager.flush();
        entityManager.clear();

        // Act
        var own = sourceRepository.findByWorkspaceIdAndIdForUpdate(42L, 301L);
        var foreign = sourceRepository.findByWorkspaceIdAndIdForUpdate(19L, 301L);
        var plainForeign = sourceRepository.findByWorkspaceIdAndId(19L, 301L);
        var plainOwn = sourceRepository.findByWorkspaceIdAndId(42L, 301L);

        // Assert
        assertThat(own).isPresent();
        assertThat(own.get().getWorkspaceId()).isEqualTo(42L);
        assertThat(foreign).isEmpty();
        assertThat(plainForeign).isEmpty();
        assertThat(plainOwn).isPresent();
    }

    @Test
    void givenSourceInWorkspace_whenSaveAnotherWithSameNameKey_thenUniqueIndexRejects() {
        // Arrange
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        entityManager.persist(primary);
        entityManager.persist(SourceFixtures.readySource(primary, 301L, "İzin notları", SourceKind.PDF, "1", 0));
        entityManager.flush();
        Source duplicate = Source.ingesting(primary, 302L, "İZİN NOTLARİ", SourceKind.PDF, WorkspaceFixtures.CREATED_AT);

        // Act
        Throwable thrown = catchThrowable(() -> sourceRepository.saveAndFlush(duplicate));

        // Assert
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rootMessage(thrown)).contains("uk_sources_workspace_id_name_key");
    }

    @Test
    void givenSourceInWorkspace_whenSaveSameNameKeyInOtherWorkspace_thenSaved() {
        // Arrange
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Workspace kiyi = WorkspaceFixtures.workspace(19L, "Deneme alanı");
        entityManager.persist(primary);
        entityManager.persist(kiyi);
        entityManager.persist(SourceFixtures.readySource(primary, 301L, "İzin notları", SourceKind.PDF, "1", 0));
        entityManager.flush();

        // Act
        sourceRepository.saveAndFlush(Source.ingesting(kiyi, 302L, "İZİN NOTLARİ", SourceKind.PDF,
                WorkspaceFixtures.CREATED_AT));
        entityManager.clear();

        // Assert
        Source saved = sourceRepository.findById(302L).orElseThrow();
        assertThat(saved.getNameKey()).isEqualTo("izin notlari");
        assertThat(saved.getWorkspaceId()).isEqualTo(19L);
    }

    @Test
    void givenFreshDatabase_whenNextSourceId_thenStartsAt10001AndIncrements() {
        // Act
        long first = sourceRepository.nextSourceId();
        long second = sourceRepository.nextSourceId();

        // Assert
        assertThat(second - first).isEqualTo(1);
        assertThat(first).isGreaterThanOrEqualTo(10001L);
    }

    @Test
    void givenSourcesOfTwoWorkspaces_whenFindByWorkspaceIdOrderByIdAsc_thenOnlyOwnSourcesOrderedById() {
        // Arrange
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Workspace kiyi = WorkspaceFixtures.workspace(19L, "Deneme alanı");
        entityManager.persist(primary);
        entityManager.persist(kiyi);
        entityManager.persist(SourceFixtures.readySource(primary, 303L, SourceKind.CSV, null, 0));
        entityManager.persist(SourceFixtures.readySource(primary, 301L, SourceKind.PDF, null, 0));
        entityManager.persist(SourceFixtures.readySource(primary, 302L, SourceKind.PDF, null, 0));
        entityManager.persist(SourceFixtures.readySource(kiyi, 999L, SourceKind.PDF, null, 0));
        entityManager.flush();
        entityManager.clear();

        // Act
        var page = sourceRepository.findByWorkspaceIdOrderByIdAsc(42L, PageRequest.of(0, 2));

        // Assert
        assertThat(page.getContent()).extracting(Source::getId).containsExactly(301L, 302L);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void givenSeededSource_whenPersistedAndReloaded_thenAllFieldsRoundTrip() {
        // Arrange
        Workspace primary = WorkspaceFixtures.workspace(42L, "Belgelerim");
        entityManager.persist(primary);
        entityManager.persist(SourceFixtures.readySource(primary, 302L, "Kılavuz", SourceKind.PDF, "3.1", 5));
        entityManager.flush();
        entityManager.clear();

        // Act
        Source source = sourceRepository.findById(302L).orElseThrow();

        // Assert
        assertThat(source.getName()).isEqualTo("Kılavuz");
        assertThat(source.getKind()).isEqualTo(SourceKind.PDF);
        assertThat(source.getVersion()).isEqualTo("3.1");
        assertThat(source.getFileName()).isEqualTo("file-302.bin");
        assertThat(source.getChunkCount()).isEqualTo(5);
        assertThat(source.getSyncedAt()).isEqualTo(SourceFixtures.SYNCED_AT);
        assertThat(source.isNew()).isFalse();
    }

    private static String rootMessage(Throwable thrown) {
        Throwable root = thrown;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return String.valueOf(root.getMessage());
    }
}

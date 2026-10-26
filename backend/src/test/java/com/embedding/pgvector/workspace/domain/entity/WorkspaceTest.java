package com.embedding.pgvector.workspace.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.source.domain.entity.SourceStatus;
import com.embedding.pgvector.support.WorkspaceFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Workspace'in kaynak ekleme kurallarını doğrular: en fazla on kaynak ve büyük küçük harf ile Türkçe harf
 * farkından bağımsız tekil ad.
 */
class WorkspaceTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    @Test
    void givenWorkspaceWithTenSources_whenAddSource_thenThrowsSourceLimitReached() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspaceWithSources(42L, 10);

        // Act
        Throwable thrown = catchThrowable(() -> workspace.addSource(999L, "Extra", SourceKind.PDF, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SOURCE_LIMIT_REACHED");
        assertThat(workspace.getSources()).hasSize(10);
    }

    @Test
    void givenWorkspaceWithNineSources_whenAddSource_thenTenthSourceIsAccepted() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspaceWithSources(42L, 9);

        // Act
        Source source = workspace.addSource(999L, "Tenth", SourceKind.PDF, NOW);

        // Assert
        assertThat(workspace.getSources()).hasSize(10).contains(source);
    }

    @Test
    void givenSourceNameUsedWithDifferentCase_whenAddSource_thenThrowsSourceNameTaken() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        workspace.addSource(1L, "Masraf notları", SourceKind.MARKDOWN, NOW);

        // Act
        Throwable thrown = catchThrowable(() -> workspace.addSource(2L, "masraf notları", SourceKind.CSV, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SOURCE_NAME_TAKEN");
        assertThat(workspace.getSources()).hasSize(1);
    }

    @Test
    void givenFreeName_whenAddSource_thenReturnsIngestingSourceInWorkspace() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");

        // Act
        Source source = workspace.addSource(10001L, "Masraf notları", SourceKind.MARKDOWN, NOW);

        // Assert
        assertThat(source.getStatus()).isEqualTo(SourceStatus.INGESTING);
        assertThat(source.getChunkCount()).isZero();
        assertThat(source.getSyncedAt()).isNull();
        assertThat(source.getWorkspaceId()).isEqualTo(42L);
        assertThat(source.getId()).isEqualTo(10001L);
        assertThat(source.getKind()).isEqualTo(SourceKind.MARKDOWN);
        assertThat(source.getCreatedAt()).isEqualTo(NOW);
        assertThat(workspace.getSources()).containsExactly(source);
    }

    @Test
    void givenSeededWorkspace_whenIsNew_thenTrue() {
        // Act
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");

        // Assert
        assertThat(workspace.isNew()).isTrue();
        assertThat(workspace.getId()).isEqualTo(42L);
        assertThat(workspace.getName()).isEqualTo("Belgelerim");
    }

    @ParameterizedTest
    @ValueSource(strings = {"izin NOTLARI", "İZİN NOTLARİ", "iZİN notlari", "Izin Notlari"})
    void givenIzinNotlari_whenAddSourceWithTurkishCaseVariant_thenThrowsSourceNameTaken(String variant) {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        workspace.addSource(1L, "İzin notları", SourceKind.MARKDOWN, NOW);

        // Act
        Throwable thrown = catchThrowable(() -> workspace.addSource(2L, variant, SourceKind.CSV, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SOURCE_NAME_TAKEN");
        assertThat(workspace.getSources()).hasSize(1);
    }

    @Test
    void givenSifreSss_whenAddSourceSifreSssWithoutCedilla_thenAddsSource() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        workspace.addSource(1L, "Şifre SSS", SourceKind.MARKDOWN, NOW);

        // Act
        Source added = workspace.addSource(2L, "Sifre SSS", SourceKind.MARKDOWN, NOW);

        // Assert
        assertThat(added.getNameKey()).isEqualTo("sifre sss");
        assertThat(workspace.getSources()).hasSize(2);
    }
}

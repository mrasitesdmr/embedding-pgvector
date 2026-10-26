package com.embedding.pgvector.source.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Source'un içe alma durum geçişlerini, yükleme türü kontrolünü ve Türkçe harflere duyarlı ad anahtarı
 * üretimini doğrular.
 */
class SourceTest {

    private final Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");

    @Test
    void givenSourceWithSuccessfulVersion_whenFailIngestion_thenReady() {
        // Arrange
        Source source = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);
        source.startIngestion();

        // Act
        source.failIngestion();

        // Assert
        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);
        assertThat(source.getVersion()).isEqualTo("3.1");
        assertThat(source.getChunkCount()).isEqualTo(5);
    }

    @Test
    void givenSourceWithoutSuccessfulVersion_whenFailIngestion_thenFailed() {
        // Arrange
        Source source = SourceFixtures.ingestingSource(workspace, 10001L, "Yeni", SourceKind.MARKDOWN);

        // Act
        source.failIngestion();

        // Assert
        assertThat(source.getStatus()).isEqualTo(SourceStatus.FAILED);
        assertThat(source.hasSuccessfulVersion()).isFalse();
    }

    @Test
    void givenSourceWithSuccessfulVersion_whenStartIngestion_thenIngestingButKeepsOldVersion() {
        // Arrange
        Source source = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);

        // Act
        source.startIngestion();

        // Assert
        assertThat(source.getStatus()).isEqualTo(SourceStatus.INGESTING);
        assertThat(source.getVersion()).isEqualTo("3.1");
        assertThat(source.getChunkCount()).isEqualTo(5);
    }

    @ParameterizedTest
    @CsvSource({"PDF,CSV", "PDF,MARKDOWN", "CSV,PDF", "WEB_CRAWL,MARKDOWN", "TICKET_EXPORT,CSV"})
    void givenUploadOfDifferentKind_whenEnsureAcceptsUpload_thenThrowsSourceKindMismatch(SourceKind sourceKind,
            SourceKind fileKind) {
        // Arrange
        Source source = SourceFixtures.readySource(workspace, 302L, sourceKind, null, 1);

        // Act
        Throwable thrown = catchThrowable(() -> source.ensureAcceptsUpload(fileKind));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("SOURCE_KIND_MISMATCH");
    }

    @Test
    void givenSameUploadableKind_whenEnsureAcceptsUpload_thenNoException() {
        // Arrange
        Source source = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);

        // Act
        Throwable thrown = catchThrowable(() -> source.ensureAcceptsUpload(SourceKind.PDF));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void givenIngestingSource_whenCompleteIngestion_thenReadyWithNewVersionAndCounts() {
        // Arrange
        Source source = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);
        source.startIngestion();
        Instant syncedAt = Instant.parse("2026-10-25T12:00:00Z");

        // Act
        source.completeIngestion("3.2", "v3.2.pdf", 9, syncedAt);

        // Assert
        assertThat(source.getStatus()).isEqualTo(SourceStatus.READY);
        assertThat(source.getVersion()).isEqualTo("3.2");
        assertThat(source.getFileName()).isEqualTo("v3.2.pdf");
        assertThat(source.getChunkCount()).isEqualTo(9);
        assertThat(source.getSyncedAt()).isEqualTo(syncedAt);
    }

    @Test
    void givenSourceKinds_whenIsUploadable_thenOnlyFileKindsAreUploadable() {
        // Assert
        assertThat(SourceKind.PDF.isUploadable()).isTrue();
        assertThat(SourceKind.MARKDOWN.isUploadable()).isTrue();
        assertThat(SourceKind.CSV.isUploadable()).isTrue();
        assertThat(SourceKind.WEB_CRAWL.isUploadable()).isFalse();
        assertThat(SourceKind.TICKET_EXPORT.isUploadable()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "İzin notları, izin notlari",
            "izin NOTLARI, izin notlari",
            "İZİN NOTLARİ, izin notlari",
            "Izin Notlari, izin notlari",
            "İstanbul notları, istanbul notlari",
            "ŞİFRE SSS, şifre sss",
            "şifre sss, şifre sss",
            "ĞÜÖÇ notları, ğüöç notlari"
    })
    void givenNamesDifferingOnlyInCaseOrDottedI_whenToNameKey_thenReturnsSameKey(String name, String expectedKey) {
        // Act
        String key = Source.toNameKey(name);

        // Assert
        assertThat(key).isEqualTo(expectedKey);
    }

    @Test
    void givenDecomposedDottedCapitalI_whenToNameKey_thenEqualsComposedForm() {
        // Act
        String decomposed = Source.toNameKey("I\u0307stanbul notları");

        // Assert
        assertThat(decomposed).isEqualTo("istanbul notlari");
    }

    @ParameterizedTest
    @CsvSource({
            "Şifre SSS, Sifre SSS",
            "Göç, Goc",
            "Ücret, Ucret"
    })
    void givenNamesDifferingInTurkishLetters_whenToNameKey_thenReturnsDifferentKeys(String first, String second) {
        // Act
        String firstKey = Source.toNameKey(first);
        String secondKey = Source.toNameKey(second);

        // Assert
        assertThat(firstKey).isNotEqualTo(secondKey);
    }
}

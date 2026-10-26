package com.embedding.pgvector.ingestion.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.within;

import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.IngestionJobFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * IngestionJob'ın aşama geçişlerini, ilerleme yüzdesi hesabını ve bitmiş işte geçersiz geçişlerin reddini
 * doğrular.
 */
class IngestionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final Source source = SourceFixtures.readySource(WorkspaceFixtures.workspace(42L, "Primary"), 302L,
            SourceKind.PDF, "3.1", 5);

    @Test
    void givenQueuedJob_whenCreated_thenStageQueuedWithZeroProgress() {
        // Act
        IngestionJob job = IngestionJobFixtures.queued(source);

        // Assert
        assertThat(job.getStage()).isEqualTo(IngestionStage.QUEUED);
        assertThat(job.getProgress()).isZero();
        assertThat(job.getWorkspaceId()).isEqualTo(42L);
        assertThat(job.getSourceId()).isEqualTo(302L);
        assertThat(job.getVersion()).isEqualTo("3.2");
        assertThat(job.getFileName()).isEqualTo("file.pdf");
        assertThat(job.getFileSizeBytes()).isEqualTo(1024L);
        assertThat(job.getStartedAt()).isNull();
    }

    @Test
    void givenQueuedJob_whenStart_thenParsingWithFivePercentAndStartedAt() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);

        // Act
        job.start(NOW);

        // Assert
        assertThat(job.getStage()).isEqualTo(IngestionStage.PARSING);
        assertThat(job.getProgress()).isCloseTo(0.05, within(1e-9));
        assertThat(job.getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void givenActiveJob_whenMoveToEachStage_thenProgressFollowsContract() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.start(NOW);

        // Act
        job.moveTo(IngestionStage.CHUNKING);
        double chunking = job.getProgress();
        job.moveTo(IngestionStage.EMBEDDING);
        double embedding = job.getProgress();
        job.moveTo(IngestionStage.WRITING);

        // Assert
        assertThat(chunking).isCloseTo(0.1, within(1e-9));
        assertThat(embedding).isCloseTo(0.1, within(1e-9));
        assertThat(job.getProgress()).isCloseTo(0.9, within(1e-9));
        assertThat(job.getStage()).isEqualTo(IngestionStage.WRITING);
    }

    @ParameterizedTest
    @EnumSource(value = IngestionStage.class, names = {"QUEUED", "PARSING", "DONE", "FAILED"})
    void givenActiveJob_whenMoveToStageThatCannotBeEnteredDirectly_thenThrowsIllegalArgument(IngestionStage stage) {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);

        // Act
        Throwable thrown = catchThrowable(() -> job.moveTo(stage));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenEmbeddingStage_whenRecordEmbeddingProgress_thenProgressFollowsFormula() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.start(NOW);
        job.moveTo(IngestionStage.EMBEDDING);
        job.recordTotalChunks(214);

        // Act
        job.recordEmbeddingProgress(96);

        // Assert
        assertThat(job.getProgress()).isCloseTo(0.4588785, within(1e-6));
        assertThat(job.getEmbeddedChunks()).isEqualTo(96);
        assertThat(job.getTotalChunks()).isEqualTo(214);
    }

    @Test
    void givenUnknownTotal_whenRecordEmbeddingProgress_thenThrowsIllegalState() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.start(NOW);

        // Act
        Throwable thrown = catchThrowable(() -> job.recordEmbeddingProgress(1));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenActiveJob_whenComplete_thenDoneWithFullProgressAndFinishedAt() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.start(NOW);

        // Act
        job.complete(NOW.plusSeconds(5));

        // Assert
        assertThat(job.getStage()).isEqualTo(IngestionStage.DONE);
        assertThat(job.getProgress()).isEqualTo(1.0);
        assertThat(job.getFinishedAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(job.getErrorCode()).isNull();
    }

    @Test
    void givenJobInEmbedding_whenFail_thenFailedKeepsProgressAndSetsErrorCode() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.start(NOW);
        job.moveTo(IngestionStage.EMBEDDING);
        job.recordTotalChunks(10);
        job.recordEmbeddingProgress(5);
        double progressBefore = job.getProgress();

        // Act
        job.fail(IngestionErrorCode.EMBEDDING_UNAVAILABLE, NOW.plusSeconds(1));

        // Assert
        assertThat(job.getStage()).isEqualTo(IngestionStage.FAILED);
        assertThat(job.getProgress()).isEqualTo(progressBefore);
        assertThat(job.getErrorCode()).isEqualTo(IngestionErrorCode.EMBEDDING_UNAVAILABLE);
        assertThat(job.getFinishedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void givenFailedJob_whenAnyTransition_thenThrowsIllegalState() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.fail(IngestionErrorCode.INTERNAL_ERROR, NOW);

        // Act
        Throwable moveThrown = catchThrowable(() -> job.moveTo(IngestionStage.WRITING));
        Throwable startThrown = catchThrowable(() -> job.start(NOW));
        Throwable completeThrown = catchThrowable(() -> job.complete(NOW));
        Throwable failThrown = catchThrowable(() -> job.fail(IngestionErrorCode.INTERNAL_ERROR, NOW));
        Throwable totalThrown = catchThrowable(() -> job.recordTotalChunks(3));

        // Assert
        assertThat(moveThrown).isInstanceOf(IllegalStateException.class);
        assertThat(startThrown).isInstanceOf(IllegalStateException.class);
        assertThat(completeThrown).isInstanceOf(IllegalStateException.class);
        assertThat(failThrown).isInstanceOf(IllegalStateException.class);
        assertThat(totalThrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenDoneJob_whenFail_thenThrowsIllegalState() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.complete(NOW);

        // Act
        Throwable thrown = catchThrowable(() -> job.fail(IngestionErrorCode.INTERNAL_ERROR, NOW));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenStages_whenIsActive_thenOnlyDoneAndFailedAreFinished() {
        // Assert
        assertThat(IngestionStage.DONE.isActive()).isFalse();
        assertThat(IngestionStage.FAILED.isActive()).isFalse();
        assertThat(IngestionStage.QUEUED.isActive()).isTrue();
        assertThat(IngestionStage.WRITING.isActive()).isTrue();
    }

    @Test
    void givenFinishedJob_whenRecordEmbeddingProgress_thenThrowsIllegalState() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.recordTotalChunks(10);
        job.complete(NOW);

        // Act
        Throwable thrown = catchThrowable(() -> job.recordEmbeddingProgress(5));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenZeroTotalChunks_whenRecordEmbeddingProgress_thenThrowsIllegalState() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.recordTotalChunks(0);

        // Act
        Throwable thrown = catchThrowable(() -> job.recordEmbeddingProgress(0));

        // Assert
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void givenAllChunksEmbedded_whenRecordEmbeddingProgress_thenProgressReachesNinetyPercent() {
        // Arrange
        IngestionJob job = IngestionJobFixtures.queued(source);
        job.recordTotalChunks(4);

        // Act
        job.recordEmbeddingProgress(4);

        // Assert
        assertThat(job.getProgress()).isCloseTo(0.9, within(1e-9));
    }
}

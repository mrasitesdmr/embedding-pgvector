package com.embedding.pgvector.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.IngestionJobFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * IngestionRecovery'nin açılışta yarıda kalmış işleri kesintiye uğradı olarak kapattığını ve veritabanı
 * hatasında çökmediğini doğrular.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class IngestionRecoveryTest {

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private IngestionJobLifecycleService lifecycleService;

    @Test
    void givenActiveJobAtStartup_whenRecover_thenFailsJobAsInterruptedAndLogsWarn(CapturedOutput output) {
        // Arrange
        Source source = SourceFixtures.readySource(WorkspaceFixtures.workspace(42L, "Primary"), 302L, SourceKind.PDF,
                "3.1", 5);
        IngestionJob job = IngestionJobFixtures.queued(source);
        ReflectionTestUtils.setField(job, "id", 7L);
        given(ingestionJobRepository.findByStageNotIn(IngestionStage.FINISHED)).willReturn(List.of(job));

        // Act
        new IngestionRecovery(ingestionJobRepository, lifecycleService).recoverInterruptedJobs();

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.INGESTION_INTERRUPTED);
        assertThat(output.getOut()).contains("Ingestion job interrupted jobId=7 sourceId=302");
    }

    @Test
    void givenDatabaseUnavailable_whenRecover_thenDoesNotThrowAndLogsError(CapturedOutput output) {
        // Arrange
        given(ingestionJobRepository.findByStageNotIn(IngestionStage.FINISHED))
                .willThrow(new DataAccessResourceFailureException("down"));

        // Act
        new IngestionRecovery(ingestionJobRepository, lifecycleService).recoverInterruptedJobs();

        // Assert
        assertThat(output.getOut()).contains("Interrupted ingestion jobs could not be recovered");
    }
}

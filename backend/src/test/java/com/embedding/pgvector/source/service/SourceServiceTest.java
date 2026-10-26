package com.embedding.pgvector.source.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import com.embedding.pgvector.source.domain.dto.SourceResponse;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.source.repository.SourceRepository;
import com.embedding.pgvector.support.IngestionJobFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SourceService'in kaynak listeleme ve tek kaynak getirme davranışını sahte depolarla doğrular.
 */
@ExtendWith(MockitoExtension.class)
class SourceServiceTest {

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private SourceRepository sourceRepository;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @InjectMocks
    private SourceService sourceService;

    private static IngestionJob jobWithId(Source source, long jobId) {
        IngestionJob job = IngestionJobFixtures.queued(source);
        ReflectionTestUtils.setField(job, "id", jobId);
        job.start(Instant.parse("2026-10-25T10:00:00Z"));
        job.moveTo(IngestionStage.EMBEDDING);
        return job;
    }

    @Test
    void givenPageOfThreeSources_whenListSources_thenLoadsLatestJobsOnce() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Source first = SourceFixtures.readySource(workspace, 301L, SourceKind.WEB_CRAWL, null, 0);
        Source second = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);
        Source third = SourceFixtures.readySource(workspace, 303L, SourceKind.CSV, null, 3);
        PageRequest paging = PageRequest.of(0, 20);
        given(sourceRepository.findByWorkspaceIdOrderByIdAsc(42L, paging))
                .willReturn(new PageImpl<>(List.of(first, second, third), paging, 3));
        given(ingestionJobRepository.findLatestBySourceIds(List.of(301L, 302L, 303L)))
                .willReturn(List.of(jobWithId(second, 7L)));

        // Act
        Page<SourceResponse> page = sourceService.listSources(42L, 0, 20);

        // Assert
        assertThat(page.getContent()).extracting(SourceResponse::latestJob).satisfiesExactly(
                firstJob -> assertThat(firstJob).isNull(),
                secondJob -> {
                    assertThat(secondJob).isNotNull();
                    assertThat(secondJob.jobId()).isEqualTo(7L);
                    assertThat(secondJob.sourceId()).isEqualTo(302L);
                    assertThat(secondJob.stage()).isEqualTo(IngestionStage.EMBEDDING);
                },
                thirdJob -> assertThat(thirdJob).isNull());
        then(ingestionJobRepository).should().findLatestBySourceIds(List.of(301L, 302L, 303L));
        then(ingestionJobRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    void givenEmptyPage_whenListSources_thenDoesNotQueryJobs() {
        // Arrange
        PageRequest paging = PageRequest.of(0, 20);
        given(sourceRepository.findByWorkspaceIdOrderByIdAsc(42L, paging)).willReturn(new PageImpl<>(List.of(), paging, 0));

        // Act
        Page<SourceResponse> page = sourceService.listSources(42L, 0, 20);

        // Assert
        assertThat(page.getContent()).isEmpty();
        then(ingestionJobRepository).shouldHaveNoInteractions();
    }

    @Test
    void givenSourceWithJob_whenGetSource_thenQueriesBySingleIdAndAttachesJob() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Source source = SourceFixtures.readySource(workspace, 302L, SourceKind.PDF, "3.1", 5);
        given(sourceRepository.findByWorkspaceIdAndId(42L, 302L)).willReturn(Optional.of(source));
        given(ingestionJobRepository.findLatestBySourceIds(List.of(302L))).willReturn(List.of(jobWithId(source, 9L)));

        // Act
        SourceResponse response = sourceService.getSource(42L, 302L);

        // Assert
        assertThat(response.latestJob()).isNotNull();
        assertThat(response.latestJob().jobId()).isEqualTo(9L);
        assertThat(response.latestJob().statusUrl()).isEqualTo("/api/workspaces/42/ingestion-jobs/9");
    }

    @Test
    void givenSourceWithoutJob_whenGetSource_thenLatestJobIsNull() {
        // Arrange
        Workspace workspace = WorkspaceFixtures.workspace(42L, "Belgelerim");
        Source source = SourceFixtures.readySource(workspace, 301L, SourceKind.WEB_CRAWL, null, 0);
        given(sourceRepository.findByWorkspaceIdAndId(42L, 301L)).willReturn(Optional.of(source));
        given(ingestionJobRepository.findLatestBySourceIds(anyCollection())).willReturn(List.of());

        // Act
        SourceResponse response = sourceService.getSource(42L, 301L);

        // Assert
        assertThat(response.latestJob()).isNull();
    }

    @Test
    void givenUnknownWorkspace_whenGetSource_thenThrowsWorkspaceNotFoundWithoutLookingUpSource() {
        // Arrange
        org.mockito.BDDMockito.willThrow(WorkspaceService.workspaceNotFound(999L))
                .given(workspaceService).ensureWorkspaceExists(999L);

        // Act
        Throwable thrown = catchThrowable(() -> sourceService.getSource(999L, 302L));

        // Assert
        assertThat(thrown).isInstanceOf(NotFoundException.class).extracting("errorCode")
                .isEqualTo("WORKSPACE_NOT_FOUND");
        then(sourceRepository).shouldHaveNoInteractions();
        then(ingestionJobRepository).shouldHaveNoInteractions();
    }
}

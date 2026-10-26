package com.embedding.pgvector.source.service;

import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import com.embedding.pgvector.source.domain.dto.SourceResponse;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.repository.SourceRepository;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Çalışma alanındaki kaynakları sayfa sayfa listeler ve tek bir kaynağı en son içe alma işiyle birlikte getirir.
 */
@Service
@Transactional(readOnly = true)
public class SourceService {

    private final WorkspaceService workspaceService;
    private final SourceRepository sourceRepository;
    private final IngestionJobRepository ingestionJobRepository;

    public SourceService(WorkspaceService workspaceService, SourceRepository sourceRepository,
            IngestionJobRepository ingestionJobRepository) {
        this.workspaceService = workspaceService;
        this.sourceRepository = sourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
    }

    public Page<SourceResponse> listSources(long workspaceId, int page, int size) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        Page<Source> sources = sourceRepository.findByWorkspaceIdOrderByIdAsc(workspaceId, PageRequest.of(page, size));
        Map<Long, IngestionJob> latestJobs = findLatestJobs(sources.getContent());
        return sources.map(source -> SourceResponse.from(source, latestJobs.get(source.getId())));
    }

    public SourceResponse getSource(long workspaceId, long sourceId) {
        Source source = getExistingSource(workspaceId, sourceId);
        return SourceResponse.from(source, findLatestJobs(List.of(source)).get(source.getId()));
    }

    public Source getExistingSource(long workspaceId, long sourceId) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        return sourceRepository.findByWorkspaceIdAndId(workspaceId, sourceId)
                .orElseThrow(() -> sourceNotFound(sourceId));
    }

    public static NotFoundException sourceNotFound(long sourceId) {
        return new NotFoundException("SOURCE_NOT_FOUND", "Source not found: " + sourceId);
    }

    private Map<Long, IngestionJob> findLatestJobs(List<Source> sources) {
        if (sources.isEmpty()) {
            return Map.of();
        }
        List<Long> sourceIds = sources.stream().map(Source::getId).toList();
        return ingestionJobRepository.findLatestBySourceIds(sourceIds).stream()
                .collect(Collectors.toMap(IngestionJob::getSourceId, Function.identity()));
    }
}

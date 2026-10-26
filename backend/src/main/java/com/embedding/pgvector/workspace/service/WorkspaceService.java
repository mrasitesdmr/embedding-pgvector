package com.embedding.pgvector.workspace.service;

import com.embedding.pgvector.chunk.repository.ChunkRepository;
import com.embedding.pgvector.chunk.repository.WorkspaceEmbeddingModelView;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.workspace.domain.dto.WorkspaceResponse;
import com.embedding.pgvector.workspace.repository.WorkspaceRepository;
import com.embedding.pgvector.workspace.repository.WorkspaceSummary;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Çalışma alanlarını sayılar ve embedding modelleriyle listeler ve getirir; bir çalışma alanının var olduğunu doğrular.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final ChunkRepository chunkRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository, ChunkRepository chunkRepository) {
        this.workspaceRepository = workspaceRepository;
        this.chunkRepository = chunkRepository;
    }

    public Page<WorkspaceResponse> listWorkspaces(int page, int size) {
        Page<WorkspaceSummary> summaries = workspaceRepository.findSummaries(PageRequest.of(page, size));
        Map<Long, List<String>> embeddingModels = embeddingModelsByWorkspaceId(
                summaries.map(WorkspaceSummary::getWorkspaceId).getContent());
        return summaries.map(summary -> WorkspaceResponse.from(summary,
                embeddingModels.getOrDefault(summary.getWorkspaceId(), List.of())));
    }

    public WorkspaceResponse getWorkspace(long workspaceId) {
        WorkspaceSummary summary = workspaceRepository.findSummaryById(workspaceId)
                .orElseThrow(() -> workspaceNotFound(workspaceId));
        List<String> embeddingModels = embeddingModelsByWorkspaceId(List.of(workspaceId))
                .getOrDefault(workspaceId, List.of());
        return WorkspaceResponse.from(summary, embeddingModels);
    }

    public void ensureWorkspaceExists(long workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw workspaceNotFound(workspaceId);
        }
    }

    public static NotFoundException workspaceNotFound(long workspaceId) {
        return new NotFoundException("WORKSPACE_NOT_FOUND", "Workspace not found: " + workspaceId);
    }

    private Map<Long, List<String>> embeddingModelsByWorkspaceId(Collection<Long> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        return chunkRepository.findEmbeddingModelsByWorkspaceIds(workspaceIds).stream()
                .collect(Collectors.groupingBy(WorkspaceEmbeddingModelView::getWorkspaceId,
                        Collectors.mapping(WorkspaceEmbeddingModelView::getEmbeddingModel,
                                Collectors.collectingAndThen(Collectors.toList(),
                                        models -> models.stream().sorted().toList()))));
    }
}

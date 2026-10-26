package com.embedding.pgvector.chunk.service;

import com.embedding.pgvector.chunk.domain.dto.ChunkResponse;
import com.embedding.pgvector.chunk.repository.ChunkRepository;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.source.service.SourceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kaynağın çalışma alanında var olduğunu doğrulayıp parçalarını sayfa sayfa listeler ve tek bir parçayı getirir.
 */
@Service
@Transactional(readOnly = true)
public class ChunkService {

    private final SourceService sourceService;
    private final ChunkRepository chunkRepository;

    public ChunkService(SourceService sourceService, ChunkRepository chunkRepository) {
        this.sourceService = sourceService;
        this.chunkRepository = chunkRepository;
    }

    public Page<ChunkResponse> listChunks(long workspaceId, long sourceId, int page, int size) {
        sourceService.getExistingSource(workspaceId, sourceId);
        return chunkRepository.findBySourceIdOrderByChunkNoAscIdAsc(sourceId, PageRequest.of(page, size))
                .map(ChunkResponse::from);
    }

    public ChunkResponse getChunk(long workspaceId, long sourceId, long chunkId) {
        sourceService.getExistingSource(workspaceId, sourceId);
        return chunkRepository.findViewBySourceIdAndId(sourceId, chunkId)
                .map(ChunkResponse::from)
                .orElseThrow(() -> new NotFoundException("CHUNK_NOT_FOUND", "Chunk not found: " + chunkId));
    }
}

package com.embedding.pgvector.ingestion.service;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import com.embedding.pgvector.chunk.repository.ChunkRepository;
import com.embedding.pgvector.embedding.client.EmbeddingProperties;
import com.embedding.pgvector.ingestion.chunking.ChunkDraft;
import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.repository.SourceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * İçe alma işinin aşama ve ilerleme değişikliklerini adım adım kaydeder; iş bitince kaynağın parçalarını yenileriyle
 * değiştirir, başarısız işte kaynağın durumunu günceller. Bu süreçte kuyruğa verilmiş ve henüz bitmemiş işleri de bilir.
 */
@Service
public class IngestionJobLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobLifecycleService.class);

    private final IngestionJobRepository ingestionJobRepository;
    private final SourceRepository sourceRepository;
    private final ChunkRepository chunkRepository;
    private final EmbeddingProperties embeddingProperties;
    private final Clock clock;
    private final Set<Long> runningJobIds = ConcurrentHashMap.newKeySet();

    public IngestionJobLifecycleService(IngestionJobRepository ingestionJobRepository,
            SourceRepository sourceRepository, ChunkRepository chunkRepository,
            EmbeddingProperties embeddingProperties, Clock clock) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.sourceRepository = sourceRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingProperties = embeddingProperties;
        this.clock = clock;
    }

    public void markRunning(long jobId) {
        runningJobIds.add(jobId);
    }

    public void markStopped(long jobId) {
        runningJobIds.remove(jobId);
    }

    public boolean isRunningInThisProcess(long jobId) {
        return runningJobIds.contains(jobId);
    }

    @Transactional
    public void startJob(long jobId) {
        IngestionJob job = getJob(jobId);
        job.start(Instant.now(clock));
        log.info("Ingestion job started jobId={} workspaceId={} sourceId={} kind={} fileSizeBytes={}",
                jobId, job.getWorkspaceId(), job.getSourceId(), job.getSource().getKind(), job.getFileSizeBytes());
    }

    @Transactional
    public void moveToStage(long jobId, IngestionStage stage) {
        getJob(jobId).moveTo(stage);
    }

    @Transactional
    public void recordTotalChunks(long jobId, int totalChunks) {
        getJob(jobId).recordTotalChunks(totalChunks);
    }

    @Transactional
    public void recordEmbeddingProgress(long jobId, int embeddedChunks) {
        getJob(jobId).recordEmbeddingProgress(embeddedChunks);
    }

    @Transactional
    public void completeJob(long jobId, List<ChunkDraft> drafts, List<float[]> embeddings) {
        if (drafts.size() != embeddings.size()) {
            throw new IllegalArgumentException("Every chunk needs one embedding: " + jobId);
        }
        IngestionJob job = getJob(jobId);
        Source source = lockSource(job);
        int replacedChunks = chunkRepository.deleteBySourceId(source.getId());
        Instant now = Instant.now(clock);
        String modelTag = embeddingProperties.modelTag();
        List<Chunk> chunks = new ArrayList<>(drafts.size());
        for (int index = 0; index < drafts.size(); index++) {
            chunks.add(Chunk.create(source, drafts.get(index), embeddings.get(index), modelTag, now));
        }
        chunkRepository.saveAll(chunks);
        source.completeIngestion(job.getVersion(), job.getFileName(), chunks.size(), now);
        job.complete(now);
        long durationMs = job.getStartedAt() == null ? 0 : now.toEpochMilli() - job.getStartedAt().toEpochMilli();
        log.info("Ingestion job finished jobId={} sourceId={} replacedChunks={} newChunks={} durationMs={}",
                jobId, source.getId(), replacedChunks, chunks.size(), durationMs);
    }

    @Transactional
    public void failJob(long jobId, IngestionErrorCode errorCode) {
        IngestionJob job = getJob(jobId);
        job.fail(errorCode, Instant.now(clock));
        lockSource(job).failIngestion();
        log.warn("Ingestion job failed jobId={} sourceId={} errorCode={}", jobId, job.getSourceId(), errorCode);
    }

    private Source lockSource(IngestionJob job) {
        return sourceRepository.findByWorkspaceIdAndIdForUpdate(job.getWorkspaceId(), job.getSourceId())
                .orElseThrow(() -> new IllegalStateException("Source of ingestion job is missing: " + job.getId()));
    }

    private IngestionJob getJob(long jobId) {
        return ingestionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Ingestion job is missing: " + jobId));
    }
}

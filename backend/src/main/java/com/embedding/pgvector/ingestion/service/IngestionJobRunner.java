package com.embedding.pgvector.ingestion.service;

import com.embedding.pgvector.config.IngestionExecutorConfig;
import com.embedding.pgvector.config.IngestionProperties;
import com.embedding.pgvector.embedding.client.EmbeddingBatchResult;
import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import com.embedding.pgvector.ingestion.chunking.ChunkDraft;
import com.embedding.pgvector.ingestion.chunking.TextChunker;
import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.parsing.DocumentUnreadableException;
import com.embedding.pgvector.ingestion.parsing.SourceDocumentParser;
import com.embedding.pgvector.ingestion.parsing.TextSection;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

/**
 * Bir içe alma işini yüklenen dosyadan kaydedilmiş parçalara kadar arka planda yürütür; hata olursa işi nedenine
 * uygun hata koduyla kapatır.
 */
@Component
public class IngestionJobRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionJobRunner.class);

    private final TaskExecutor ingestionExecutor;
    private final IngestionJobLifecycleService lifecycleService;
    private final Map<SourceKind, SourceDocumentParser> parsers = new EnumMap<>(SourceKind.class);
    private final TextChunker textChunker;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelGuard embeddingModelGuard;
    private final IngestionProperties properties;
    private final Clock clock;

    public IngestionJobRunner(@Qualifier(IngestionExecutorConfig.INGESTION_EXECUTOR) TaskExecutor ingestionExecutor,
            IngestionJobLifecycleService lifecycleService,
            List<SourceDocumentParser> parsers, TextChunker textChunker, EmbeddingClient embeddingClient,
            EmbeddingModelGuard embeddingModelGuard, IngestionProperties properties, Clock clock) {
        this.ingestionExecutor = ingestionExecutor;
        this.lifecycleService = lifecycleService;
        parsers.forEach(parser -> this.parsers.put(parser.kind(), parser));
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.embeddingModelGuard = embeddingModelGuard;
        this.properties = properties;
        this.clock = clock;
    }

    public void submit(long jobId, byte[] content, SourceKind kind) {
        lifecycleService.markRunning(jobId);
        try {
            ingestionExecutor.execute(() -> runAndMarkStopped(jobId, content, kind));
        } catch (TaskRejectedException exception) {
            log.error("Ingestion job rejected by executor jobId={}", jobId, exception);
            failSafely(jobId, IngestionErrorCode.INTERNAL_ERROR);
            lifecycleService.markStopped(jobId);
        }
    }

    private void runAndMarkStopped(long jobId, byte[] content, SourceKind kind) {
        try {
            run(jobId, content, kind);
        } finally {
            lifecycleService.markStopped(jobId);
        }
    }

    public void run(long jobId, byte[] content, SourceKind kind) {
        try {
            lifecycleService.startJob(jobId);
            log.info("Ingestion stage changed jobId={} stage={}", jobId, IngestionStage.PARSING);
            List<TextSection> sections = parser(kind).parse(content);
            log.info("Document parsed jobId={} sections={}", jobId, sections.size());

            changeStage(jobId, IngestionStage.CHUNKING);
            List<ChunkDraft> drafts = textChunker.chunk(sections);
            log.info("Document chunked jobId={} chunkCount={} avgTokens={}", jobId, drafts.size(),
                    averageTokens(drafts));
            if (drafts.isEmpty()) {
                failSafely(jobId, IngestionErrorCode.NO_TEXT_EXTRACTED);
                return;
            }
            lifecycleService.recordTotalChunks(jobId, drafts.size());

            changeStage(jobId, IngestionStage.EMBEDDING);
            List<float[]> embeddings = embed(jobId, drafts);

            changeStage(jobId, IngestionStage.WRITING);
            lifecycleService.completeJob(jobId, drafts, embeddings);
        } catch (DocumentUnreadableException exception) {
            failSafely(jobId, IngestionErrorCode.FILE_UNREADABLE);
        } catch (ServiceUnavailableException exception) {
            failSafely(jobId, embeddingErrorCode(exception));
        } catch (ConflictException exception) {
            failSafely(jobId, conflictErrorCode(jobId, exception));
        } catch (RuntimeException exception) {
            log.error("Ingestion job crashed jobId={}", jobId, exception);
            failSafely(jobId, IngestionErrorCode.INTERNAL_ERROR);
        }
    }

    private List<float[]> embed(long jobId, List<ChunkDraft> drafts) {
        int batchSize = properties.embeddingBatchSize();
        int batchCount = (drafts.size() + batchSize - 1) / batchSize;
        List<float[]> embeddings = new ArrayList<>(drafts.size());
        for (int batch = 0; batch < batchCount; batch++) {
            List<ChunkDraft> batchDrafts = drafts.subList(batch * batchSize,
                    Math.min(drafts.size(), (batch + 1) * batchSize));
            long startedAt = clock.millis();
            EmbeddingBatchResult result = embeddingClient.embedAll(
                    batchDrafts.stream().map(ChunkDraft::embeddingInput).toList());
            if (result.embeddings().size() != batchDrafts.size()) {
                throw new ServiceUnavailableException("EMBEDDING_UNAVAILABLE",
                        "Embedding service returned no embedding");
            }
            for (float[] embedding : result.embeddings()) {
                embeddingModelGuard.ensureDimensions(embedding);
                embeddings.add(embedding);
            }
            lifecycleService.recordEmbeddingProgress(jobId, embeddings.size());
            log.info("Embedding batch done jobId={} batch={}/{} chunkCount={} durationMs={}",
                    jobId, batch + 1, batchCount, batchDrafts.size(), clock.millis() - startedAt);
        }
        return embeddings;
    }

    private void changeStage(long jobId, IngestionStage stage) {
        lifecycleService.moveToStage(jobId, stage);
        log.info("Ingestion stage changed jobId={} stage={}", jobId, stage);
    }

    private SourceDocumentParser parser(SourceKind kind) {
        SourceDocumentParser parser = parsers.get(kind);
        if (parser == null) {
            throw new IllegalStateException("No parser for source kind " + kind);
        }
        return parser;
    }

    private static long averageTokens(List<ChunkDraft> drafts) {
        return Math.round(drafts.stream().mapToInt(ChunkDraft::tokenCount).average().orElse(0));
    }

    private static IngestionErrorCode embeddingErrorCode(ServiceUnavailableException exception) {
        return "EMBEDDING_MODEL_NOT_FOUND".equals(exception.getErrorCode())
                ? IngestionErrorCode.EMBEDDING_MODEL_NOT_FOUND
                : IngestionErrorCode.EMBEDDING_UNAVAILABLE;
    }

    private static IngestionErrorCode conflictErrorCode(long jobId, ConflictException exception) {
        if ("EMBEDDING_MODEL_MISMATCH".equals(exception.getErrorCode())) {
            return IngestionErrorCode.EMBEDDING_MODEL_MISMATCH;
        }
        log.error("Ingestion job crashed jobId={}", jobId, exception);
        return IngestionErrorCode.INTERNAL_ERROR;
    }

    private void failSafely(long jobId, IngestionErrorCode errorCode) {
        try {
            lifecycleService.failJob(jobId, errorCode);
        } catch (RuntimeException exception) {
            log.error("Ingestion job could not be marked failed jobId={} errorCode={}", jobId, errorCode, exception);
        }
    }
}

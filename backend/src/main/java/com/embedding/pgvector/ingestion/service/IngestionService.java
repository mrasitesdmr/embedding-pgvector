package com.embedding.pgvector.ingestion.service;

import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.ingestion.domain.dto.IngestionJobResponse;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.parsing.FileTypeDetector;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.source.repository.SourceRepository;
import com.embedding.pgvector.source.service.SourceService;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import com.embedding.pgvector.workspace.repository.WorkspaceRepository;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Dosya yükleyerek yeni kaynak ekleme ve var olan kaynağa yeni sürüm yükleme akışlarını yürütür; içe alma işini
 * kayıt tamamlandıktan sonra başlatır ve işin durumunu getirir.
 */
@Service
public class IngestionService {

    private static final String DEFAULT_FILE_NAME = "upload";

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceService workspaceService;
    private final SourceRepository sourceRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final FileTypeDetector fileTypeDetector;
    private final EmbeddingModelGuard embeddingModelGuard;
    private final IngestionJobRunner ingestionJobRunner;
    private final Clock clock;

    public IngestionService(WorkspaceRepository workspaceRepository, WorkspaceService workspaceService,
            SourceRepository sourceRepository, IngestionJobRepository ingestionJobRepository,
            FileTypeDetector fileTypeDetector, EmbeddingModelGuard embeddingModelGuard,
            IngestionJobRunner ingestionJobRunner, Clock clock) {
        this.workspaceRepository = workspaceRepository;
        this.workspaceService = workspaceService;
        this.sourceRepository = sourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.fileTypeDetector = fileTypeDetector;
        this.embeddingModelGuard = embeddingModelGuard;
        this.ingestionJobRunner = ingestionJobRunner;
        this.clock = clock;
    }

    @Transactional
    public IngestionJobResponse createSource(long workspaceId, String name, MultipartFile file) {
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
                .orElseThrow(() -> WorkspaceService.workspaceNotFound(workspaceId));
        byte[] content = readContent(file);
        String fileName = fileName(file);
        SourceKind kind = fileTypeDetector.detect(fileName, content);
        embeddingModelGuard.ensureWorkspaceCompatible(workspaceId);
        Instant now = Instant.now(clock);
        Source source = workspace.addSource(sourceRepository.nextSourceId(), name.strip(), kind, now);
        try {
            sourceRepository.saveAndFlush(source);
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("SOURCE_NAME_TAKEN",
                    "Source name is already used in workspace: " + workspaceId);
        }
        IngestionJob job = ingestionJobRepository.saveAndFlush(
                IngestionJob.queued(source, null, fileName, content.length, now));
        submitAfterCommit(job.getId(), content, kind);
        return IngestionJobResponse.from(job);
    }

    @Transactional
    public IngestionJobResponse uploadSourceVersion(long workspaceId, long sourceId, @Nullable String version,
            MultipartFile file) {
        String versionLabel = normalizeVersion(version);
        workspaceService.ensureWorkspaceExists(workspaceId);
        Source source = sourceRepository.findByWorkspaceIdAndIdForUpdate(workspaceId, sourceId)
                .orElseThrow(() -> SourceService.sourceNotFound(sourceId));
        byte[] content = readContent(file);
        String fileName = fileName(file);
        SourceKind kind = fileTypeDetector.detect(fileName, content);
        source.ensureAcceptsUpload(kind);
        embeddingModelGuard.ensureWorkspaceCompatible(workspaceId);
        if (ingestionJobRepository.existsBySourceIdAndStageNotIn(sourceId, IngestionStage.FINISHED)) {
            throw ingestionInProgress(sourceId);
        }
        IngestionJob job;
        try {
            job = ingestionJobRepository.saveAndFlush(
                    IngestionJob.queued(source, versionLabel, fileName, content.length, Instant.now(clock)));
        } catch (DataIntegrityViolationException exception) {
            throw ingestionInProgress(sourceId);
        }
        source.startIngestion();
        submitAfterCommit(job.getId(), content, kind);
        return IngestionJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public IngestionJobResponse getIngestionJob(long workspaceId, long jobId) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        return ingestionJobRepository.findByWorkspaceIdAndId(workspaceId, jobId)
                .map(IngestionJobResponse::from)
                .orElseThrow(() -> new NotFoundException("INGESTION_JOB_NOT_FOUND",
                        "Ingestion job not found: " + jobId));
    }

    private void submitAfterCommit(long jobId, byte[] content, SourceKind kind) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            ingestionJobRunner.submit(jobId, content, kind);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ingestionJobRunner.submit(jobId, content, kind);
            }
        });
    }

    private static byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("Uploaded file cannot be read", exception);
        }
    }

    private static String fileName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return DEFAULT_FILE_NAME;
        }
        String baseName = originalName.substring(
                Math.max(originalName.lastIndexOf('/'), originalName.lastIndexOf('\\')) + 1).strip();
        return baseName.isEmpty() ? DEFAULT_FILE_NAME : baseName;
    }

    private static @Nullable String normalizeVersion(@Nullable String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        return version.strip();
    }

    private static ConflictException ingestionInProgress(long sourceId) {
        return new ConflictException("INGESTION_IN_PROGRESS", "Source has an active ingestion job: " + sourceId);
    }
}

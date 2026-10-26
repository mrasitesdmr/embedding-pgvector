package com.embedding.pgvector.ingestion.service;

import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionJob;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.repository.IngestionJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * Uygulama açılırken önceki süreçten yarıda kalmış içe alma işlerini bulur ve kesintiye uğradı hatasıyla kapatır;
 * bu süreçte kuyruğa verilmiş işlere dokunmaz.
 */
@Component
public class IngestionRecovery {

    private static final Logger log = LoggerFactory.getLogger(IngestionRecovery.class);

    private final IngestionJobRepository ingestionJobRepository;
    private final IngestionJobLifecycleService lifecycleService;

    public IngestionRecovery(IngestionJobRepository ingestionJobRepository,
            IngestionJobLifecycleService lifecycleService) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.lifecycleService = lifecycleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void recoverInterruptedJobs() {
        try {
            for (IngestionJob job : ingestionJobRepository.findByStageNotIn(IngestionStage.FINISHED)) {
                if (lifecycleService.isRunningInThisProcess(job.getId())) {
                    log.info("Ingestion job running in this process, recovery skipped jobId={} sourceId={}",
                            job.getId(), job.getSourceId());
                    continue;
                }
                lifecycleService.failJob(job.getId(), IngestionErrorCode.INGESTION_INTERRUPTED);
                log.warn("Ingestion job interrupted jobId={} sourceId={}", job.getId(), job.getSourceId());
            }
        } catch (DataAccessException exception) {
            log.error("Interrupted ingestion jobs could not be recovered", exception);
        }
    }
}

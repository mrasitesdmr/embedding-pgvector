package com.embedding.pgvector.embedding.service;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import com.embedding.pgvector.chunk.repository.ChunkRepository;
import com.embedding.pgvector.chunk.repository.EmbeddingModelUsage;
import com.embedding.pgvector.embedding.client.EmbeddingProperties;
import com.embedding.pgvector.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Parçaların ve yeni vektörlerin etkin embedding modeline uyduğunu denetler; uyuşmazlıkta 409 hatası verir.
 * Uygulama açılırken başka modelle üretilmiş parçalar ya da kolondan farklı bir boyut ayarı varsa bunu loglar.
 */
@Service
public class EmbeddingModelGuard {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingModelGuard.class);

    private final ChunkRepository chunkRepository;
    private final EmbeddingProperties properties;

    public EmbeddingModelGuard(ChunkRepository chunkRepository, EmbeddingProperties properties) {
        this.chunkRepository = chunkRepository;
        this.properties = properties;
    }

    public String modelTag() {
        return properties.modelTag();
    }

    public void ensureWorkspaceCompatible(long workspaceId) {
        String modelTag = modelTag();
        boolean mismatch = chunkRepository.findEmbeddingModels(workspaceId).stream()
                .anyMatch(storedModel -> !storedModel.equals(modelTag));
        if (mismatch) {
            throw new ConflictException("EMBEDDING_MODEL_MISMATCH",
                    "Workspace chunks were embedded with another model: " + workspaceId);
        }
    }

    public boolean isWorkspaceCompatible(long workspaceId) {
        String modelTag = modelTag();
        return chunkRepository.findEmbeddingModels(workspaceId).stream().allMatch(modelTag::equals);
    }

    public void ensureDimensions(float[] embedding) {
        if (embedding.length != properties.dimensions()) {
            throw new ConflictException("EMBEDDING_MODEL_MISMATCH",
                    "Embedding model returned " + embedding.length + " dimensions, expected "
                            + properties.dimensions());
        }
        if (embedding.length != Chunk.EMBEDDING_DIMENSIONS) {
            throw new ConflictException("EMBEDDING_MODEL_MISMATCH",
                    "Embedding has " + embedding.length + " dimensions, the embedding column stores "
                            + Chunk.EMBEDDING_DIMENSIONS);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void warnOnMismatch() {
        if (properties.dimensions() != Chunk.EMBEDDING_DIMENSIONS) {
            log.warn("Embedding dimensions differ from embedding column configuredDimensions={} columnDimensions={}",
                    properties.dimensions(), Chunk.EMBEDDING_DIMENSIONS);
        }
        try {
            String modelTag = modelTag();
            for (EmbeddingModelUsage usage : chunkRepository.countWorkspacesByEmbeddingModel()) {
                if (!usage.getEmbeddingModel().equals(modelTag)) {
                    log.warn("Embedding model mismatch configuredModel={} storedModel={} workspaceCount={}",
                            modelTag, usage.getEmbeddingModel(), usage.getWorkspaceCount());
                }
            }
        } catch (DataAccessException exception) {
            log.error("Embedding model check failed at startup", exception);
        }
    }
}

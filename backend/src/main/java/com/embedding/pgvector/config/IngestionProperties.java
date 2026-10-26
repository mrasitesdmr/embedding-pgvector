package com.embedding.pgvector.config;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parça boyutları, embedding toplu istek büyüklüğü ve arka plan iş havuzu için içe alma ayarlarını okur ve doğrular.
 */
@ConfigurationProperties("ingestion")
@Validated
public record IngestionProperties(
        @Positive int embeddingBatchSize,
        @Positive int targetChunkChars,
        @Positive int maxChunkChars,
        @PositiveOrZero int overlapChars,
        @Positive int charsPerToken,
        @Positive int workerThreads,
        @Positive int queueCapacity) {
}

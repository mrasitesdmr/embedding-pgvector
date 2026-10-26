package com.embedding.pgvector.config;

import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Varsayılan arama ayarlarını, RRF sabitini ve arama kaydı istatistiklerinin ayarlarını okur ve doğrular.
 */
@ConfigurationProperties("search")
@Validated
public record SearchProperties(
        @NotNull SearchMode defaultMode,
        @NotNull FilterStrategy defaultFilterStrategy,
        @Min(10) @Max(1000) int defaultEfSearch,
        @Min(1) @Max(50) int defaultLimit,
        @Positive int rrfK,
        @Positive int maxScanTuples,
        boolean forceVectorIndex,
        @NotNull ZoneId statsTimeZone,
        @Min(1) @Max(20) int popularQueryLimit,
        @DecimalMin("0.0") @DecimalMax("2.0") double popularMaxDistance) {
}

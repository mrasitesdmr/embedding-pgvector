package com.embedding.pgvector.ingestion.parsing;

import org.jspecify.annotations.Nullable;

/**
 * Ayrıştırılan belgeden çıkan, başlığı olabilen tek bir metin bölümünü taşır.
 */
public record TextSection(@Nullable String heading, String body) {
}

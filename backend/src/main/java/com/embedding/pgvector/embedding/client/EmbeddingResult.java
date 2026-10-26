package com.embedding.pgvector.embedding.client;

/**
 * Tek bir metin için dönen embedding vektörünü ve onu üreten modeli taşır.
 */
public record EmbeddingResult(String embeddingModel, float[] embedding) {
}

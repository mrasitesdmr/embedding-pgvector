package com.embedding.pgvector.embedding.client;

import java.util.List;

/**
 * Toplu embedding isteğinde her metin için dönen vektörleri ve onları üreten modeli taşır.
 */
public record EmbeddingBatchResult(String embeddingModel, List<float[]> embeddings) {

    public EmbeddingBatchResult {
        embeddings = List.copyOf(embeddings);
    }
}

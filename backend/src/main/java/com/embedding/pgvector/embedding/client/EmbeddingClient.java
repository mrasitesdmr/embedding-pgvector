package com.embedding.pgvector.embedding.client;

import java.util.List;

/**
 * Metinleri tek tek ya da toplu olarak embedding vektörüne çeviren dış model servisinin sözleşmesidir.
 */
public interface EmbeddingClient {

    EmbeddingResult embed(String text);

    EmbeddingBatchResult embedAll(List<String> texts);
}

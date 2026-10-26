package com.embedding.pgvector.embedding.client;

import java.util.List;

/**
 * Birden fazla metni tek istekte Ollama'ya gönderen embedding isteğinin gövdesini taşır.
 */
public record OllamaEmbedBatchRequest(String model, List<String> input) {
}

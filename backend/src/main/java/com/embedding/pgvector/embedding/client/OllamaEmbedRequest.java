package com.embedding.pgvector.embedding.client;

/**
 * Tek bir metni Ollama'ya gönderen embedding isteğinin gövdesini taşır.
 */
public record OllamaEmbedRequest(String model, String input) {
}

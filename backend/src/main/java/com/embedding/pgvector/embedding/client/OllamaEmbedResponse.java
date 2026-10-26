package com.embedding.pgvector.embedding.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jspecify.annotations.Nullable;

/**
 * Ollama'nın embedding yanıtının gövdesini taşır; bilinmeyen alanları yok sayar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaEmbedResponse(@Nullable String model, float @Nullable [] @Nullable [] embeddings) {
}

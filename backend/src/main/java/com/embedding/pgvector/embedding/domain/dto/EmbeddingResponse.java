package com.embedding.pgvector.embedding.domain.dto;

import com.embedding.pgvector.embedding.client.EmbeddingResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Bir metnin embedding vektörünü, onu üreten model ve modelin yanıt süresiyle birlikte yanıt gövdesinde taşır.
 */
public record EmbeddingResponse(
        @Schema(description = "Configured Ollama model name used for this embedding (embedding.model)",
                example = "bge-m3", requiredMode = Schema.RequiredMode.REQUIRED)
        String embeddingModel,

        @Schema(description = "Number of values in embedding", example = "1024",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int dimensions,

        @Schema(description = "The vector returned by the model", requiredMode = Schema.RequiredMode.REQUIRED)
        float[] embedding,

        @Schema(description = "Milliseconds from sending the request to Ollama until its answer was received "
                + "(measured by the backend)", example = "85", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        long durationMs) {

    public static EmbeddingResponse from(EmbeddingResult result, long durationMs) {
        float[] embedding = result.embedding();
        return new EmbeddingResponse(result.embeddingModel(), embedding.length, embedding, durationMs);
    }
}

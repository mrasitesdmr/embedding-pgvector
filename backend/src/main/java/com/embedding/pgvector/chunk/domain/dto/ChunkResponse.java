package com.embedding.pgvector.chunk.domain.dto;

import com.embedding.pgvector.chunk.repository.ChunkView;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Tek bir parçayı, vektörün kendisi olmadan, embedding modeli etiketiyle birlikte yanıt gövdesinde taşır.
 */
public record ChunkResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long chunkId,

        @Schema(description = "Order inside the source, starting at 0", minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int chunkNo,

        @Schema(types = {"string", "null"}, example = "s. 6 · 5. Yıllık izin")
        @Nullable String heading,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String content,

        @Schema(description = "Approximate token count, ceil(characters of heading + content / 4)",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int tokenCount,

        @Schema(description = "Tag of the model that produced the vector (<model>@<dimensions>)",
                example = "bge-m3@1024", requiredMode = Schema.RequiredMode.REQUIRED)
        String embeddingModel) {

    public static ChunkResponse from(ChunkView chunk) {
        return new ChunkResponse(chunk.getId(), chunk.getChunkNo(), chunk.getHeading(), chunk.getContent(),
                chunk.getTokenCount(), chunk.getEmbeddingModel());
    }
}

package com.embedding.pgvector.ingestion.chunking;

import org.jspecify.annotations.Nullable;

/**
 * Parçalamadan çıkan, henüz vektöre çevrilmemiş parçayı taşır; embedding'e başlığıyla birlikte gidecek metni üretir.
 */
public record ChunkDraft(int chunkNo, @Nullable String heading, String content, int tokenCount) {

    public String embeddingInput() {
        return embeddingInput(heading, content);
    }

    public static String embeddingInput(@Nullable String heading, String content) {
        return heading == null ? content : heading + "\n\n" + content;
    }
}

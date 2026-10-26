package com.embedding.pgvector.support;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import com.embedding.pgvector.ingestion.chunking.ChunkDraft;
import com.embedding.pgvector.source.domain.entity.Source;

/**
 * Testlerde verilen kaynağa bağlı, embedding vektörlü parça nesneleri üreten yardımcıdır.
 */
public final class ChunkFixtures {

    public static final String MODEL_TAG = "bge-m3@1024";

    private ChunkFixtures() {
    }

    public static Chunk chunk(Source source, int chunkNo, String heading, String content, float[] embedding) {
        return chunk(source, chunkNo, heading, content, embedding, MODEL_TAG);
    }

    public static Chunk chunk(Source source, int chunkNo, String heading, String content, float[] embedding,
            String modelTag) {
        ChunkDraft draft = new ChunkDraft(chunkNo, heading, content, (content.length() + 3) / 4);
        return Chunk.create(source, draft, embedding, modelTag, WorkspaceFixtures.CREATED_AT);
    }
}

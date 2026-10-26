package com.embedding.pgvector.embedding.service;

import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.client.EmbeddingResult;
import com.embedding.pgvector.embedding.domain.dto.EmbedTextRequest;
import com.embedding.pgvector.embedding.domain.dto.EmbeddingResponse;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tek bir metni embedding vektörüne çevirir ve modelin yanıt süresini ölçer.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingClient embeddingClient;
    private final Clock clock;

    public EmbeddingService(EmbeddingClient embeddingClient, Clock clock) {
        this.embeddingClient = embeddingClient;
        this.clock = clock;
    }

    public EmbeddingResponse embedText(EmbedTextRequest request) {
        String text = Objects.requireNonNull(request.text());
        long startedAt = clock.millis();
        EmbeddingResult result = embeddingClient.embed(text);
        long durationMs = clock.millis() - startedAt;
        EmbeddingResponse response = EmbeddingResponse.from(result, durationMs);
        log.info("Text embedded model={} dimensions={} durationMs={} textLength={}",
                response.embeddingModel(), response.dimensions(), response.durationMs(), text.length());
        return response;
    }
}

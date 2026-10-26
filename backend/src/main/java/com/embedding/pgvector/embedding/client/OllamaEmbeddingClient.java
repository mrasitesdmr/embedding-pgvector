package com.embedding.pgvector.embedding.client;

import com.embedding.pgvector.exception.ServiceUnavailableException;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Metinleri yerel Ollama sunucusunda embedding vektörüne çevirir; ulaşılamayan servisi ya da eksik modeli 503 hatasına
 * dönüştürür.
 */
@Component
public class OllamaEmbeddingClient implements EmbeddingClient {

    private static final String EMBED_PATH = "/api/embed";

    private final RestClient restClient;
    private final EmbeddingProperties properties;

    public OllamaEmbeddingClient(RestClient.Builder builder, EmbeddingProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory(properties))
                .build();
    }

    @Override
    public EmbeddingResult embed(String text) {
        OllamaEmbedResponse response = requestEmbeddings(new OllamaEmbedRequest(properties.model(), text));
        float @Nullable [] embedding = firstEmbedding(response);
        if (embedding == null) {
            throw noEmbedding();
        }
        return new EmbeddingResult(properties.model(), embedding);
    }

    @Override
    public EmbeddingBatchResult embedAll(List<String> texts) {
        if (texts.isEmpty()) {
            return new EmbeddingBatchResult(properties.model(), List.of());
        }
        OllamaEmbedResponse response = requestEmbeddings(new OllamaEmbedBatchRequest(properties.model(), texts));
        return new EmbeddingBatchResult(properties.model(), allEmbeddings(response, texts.size()));
    }

    private @Nullable OllamaEmbedResponse requestEmbeddings(Object requestBody) {
        try {
            return restClient.post()
                    .uri(EMBED_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(OllamaEmbedResponse.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ServiceUnavailableException("EMBEDDING_MODEL_NOT_FOUND",
                    "Embedding model is not available: " + properties.model());
        } catch (RestClientException exception) {
            throw new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service is not reachable");
        }
    }

    private static float @Nullable [] firstEmbedding(@Nullable OllamaEmbedResponse response) {
        if (response == null || response.embeddings() == null || response.embeddings().length == 0) {
            return null;
        }
        float @Nullable [] embedding = response.embeddings()[0];
        return embedding == null || embedding.length == 0 ? null : embedding;
    }

    private static List<float[]> allEmbeddings(@Nullable OllamaEmbedResponse response, int expectedCount) {
        if (response == null || response.embeddings() == null || response.embeddings().length != expectedCount) {
            throw noEmbedding();
        }
        List<float[]> embeddings = new ArrayList<>(expectedCount);
        for (float @Nullable [] embedding : response.embeddings()) {
            if (embedding == null || embedding.length == 0) {
                throw noEmbedding();
            }
            embeddings.add(embedding);
        }
        return embeddings;
    }

    private static ServiceUnavailableException noEmbedding() {
        return new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "Embedding service returned no embedding");
    }

    private static JdkClientHttpRequestFactory requestFactory(EmbeddingProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}

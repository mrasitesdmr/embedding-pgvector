package com.embedding.pgvector.embedding.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Embedding servisinin adresi, model seçimi, vektör boyutu ve zaman aşımı ayarlarını okur ve doğrular.
 */
@ConfigurationProperties("embedding")
@Validated
public record EmbeddingProperties(
        @NotNull URI baseUrl,
        @NotBlank String model,
        @Positive int dimensions,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout) {

    public String modelTag() {
        return model + "@" + dimensions;
    }
}

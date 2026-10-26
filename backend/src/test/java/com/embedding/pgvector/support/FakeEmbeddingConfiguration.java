package com.embedding.pgvector.support;

import com.embedding.pgvector.embedding.client.EmbeddingClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Testlerde gerçek embedding istemcisi yerine sahte istemciyi uygulamaya tanıtan test yapılandırmasıdır.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeEmbeddingConfiguration {

    @Bean
    @Primary
    FakeEmbeddingClient fakeEmbeddingClient() {
        return new FakeEmbeddingClient();
    }
}

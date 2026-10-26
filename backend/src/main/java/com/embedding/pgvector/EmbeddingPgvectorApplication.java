package com.embedding.pgvector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Uygulamanın giriş noktasıdır; Spring uygulamasını başlatır ve ayar kayıtlarını tarar.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class EmbeddingPgvectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmbeddingPgvectorApplication.class, args);
    }
}

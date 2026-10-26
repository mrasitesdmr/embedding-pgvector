package com.embedding.pgvector.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.support.FakeEmbeddingConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boş bir veritabanında uygulama açıldığında yalnızca Belgelerim çalışma alanının oluştuğunu ve hiç veri
 * olmadığını doğrular.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FakeEmbeddingConfiguration.class)
class CleanInstallIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres");

    private static final PostgreSQLContainer FRESH_DATABASE = new PostgreSQLContainer(PGVECTOR_IMAGE);

    static {
        FRESH_DATABASE.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", FRESH_DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", FRESH_DATABASE::getUsername);
        registry.add("spring.datasource.password", FRESH_DATABASE::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @LocalServerPort
    private int port;

    @Test
    void givenFreshDatabase_whenApplicationStarts_thenOnlyBelgelerimWorkspaceAndNoData() {
        // Act
        List<Map<String, Object>> workspaces = jdbcTemplate.queryForList("SELECT id, name FROM workspaces");
        ApiTestClient.Response response = new ApiTestClient(port).get("/api/workspaces");

        // Assert
        assertThat(workspaces).singleElement().satisfies(row -> {
            assertThat(row.get("id")).isEqualTo(42L);
            assertThat(row.get("name")).isEqualTo("Belgelerim");
        });
        for (String table : List.of("sources", "chunks", "ingestion_jobs", "search_events")) {
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class))
                    .as(table).isZero();
        }
        var body = JsonMapper.builder().build().readTree(response.body());
        assertThat(response.status()).isEqualTo(200);
        assertThat(body.get("totalElements").asLong()).isEqualTo(1L);
        assertThat(body.get("content").get(0).get("sourceCount").asLong()).isZero();
    }
}

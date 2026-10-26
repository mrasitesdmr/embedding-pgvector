package com.embedding.pgvector.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V9 göçünün eski demo ve kullanıcı verisini silip yalnızca Belgelerim çalışma alanını bıraktığını, yeniden
 * çalışınca kaynağı koruduğunu doğrular.
 */
class LegacyDataResetMigrationIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres");

    private static Flyway flyway(DataSource dataSource, String target) {
        return Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target(target).load();
    }

    @Test
    void givenDemoAndUserDataBeforeV9_whenMigrate_thenAllDataDeletedAndOnlyBelgelerimRemains() {
        try (PostgreSQLContainer container = new PostgreSQLContainer(PGVECTOR_IMAGE)) {
            // Arrange
            container.start();
            DataSource dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(),
                    container.getPassword());
            flyway(dataSource, "8").migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO workspaces (id, name) VALUES (1, 'Eski alan 1'), (42, 'Eski alan 42')");
            jdbc.update("INSERT INTO sources (id, workspace_id, name, name_key, kind, status) "
                    + "VALUES (301, 42, 'Eski web', 'eski web', 'WEB_CRAWL', 'READY')");
            Long userSourceId = jdbc.queryForObject("SELECT nextval('sources_id_seq')", Long.class);
            assertThat(userSourceId).isEqualTo(10001L);
            jdbc.update("INSERT INTO sources (id, workspace_id, name, name_key, kind, status) "
                    + "VALUES (?, 1, 'Kullanıcı belgesi', 'kullanıcı belgesi', 'PDF', 'INGESTING')", userSourceId);
            for (long sourceId : new long[] {301L, userSourceId}) {
                long workspaceId = sourceId == 301L ? 42L : 1L;
                jdbc.update("INSERT INTO chunks (id, workspace_id, source_id, chunk_no, content, token_count, "
                        + "embedding, embedding_model) VALUES (nextval('chunks_id_seq'), ?, ?, 0, 'metin', 2, "
                        + "array_fill(0.1::real, ARRAY[1024])::vector, 'bge-m3@1024')", workspaceId, sourceId);
            }
            jdbc.update("INSERT INTO ingestion_jobs (workspace_id, source_id, file_name, file_size_bytes, stage, progress, "
                    + "created_at) VALUES (1, ?, 'belge.pdf', 100, 'EMBEDDING', 0.4, now())", userSourceId);
            jdbc.update("INSERT INTO search_events (workspace_id, created_at, query, mode, result_count, total_ms) "
                    + "VALUES (42, now(), 'eski arama', 'KEYWORD', 1, 5)");

            // Act
            MigrateResult result = flyway(dataSource, "latest").migrate();

            // Assert
            assertThat(result.migrationsExecuted).isEqualTo(2);
            List<Map<String, Object>> workspaces = jdbc.queryForList("SELECT id, name FROM workspaces");
            assertThat(workspaces).singleElement().satisfies(row -> {
                assertThat(row.get("id")).isEqualTo(42L);
                assertThat(row.get("name")).isEqualTo("Belgelerim");
            });
            for (String table : List.of("sources", "chunks", "ingestion_jobs", "search_events")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
            }
            assertThat(jdbc.queryForObject("SELECT nextval('sources_id_seq')", Long.class)).isEqualTo(10001L);
            assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IN ('9', '10') "
                    + "AND success", String.class)).containsExactlyInAnyOrder("9", "10");
        }
    }

    @Test
    void givenMigratedDatabaseWithUserSource_whenMigrateAgain_thenNothingAppliedAndSourceKept() {
        try (PostgreSQLContainer container = new PostgreSQLContainer(PGVECTOR_IMAGE)) {
            // Arrange
            container.start();
            DataSource dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(),
                    container.getPassword());
            flyway(dataSource, "latest").migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO sources (id, workspace_id, name, name_key, kind, status) "
                    + "VALUES (10001, 42, 'Belgem', 'belgem', 'PDF', 'READY')");

            // Act
            MigrateResult second = flyway(dataSource, "latest").migrate();

            // Assert
            assertThat(second.migrationsExecuted).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM sources", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workspaces", Long.class)).isEqualTo(1L);
        }
    }
}

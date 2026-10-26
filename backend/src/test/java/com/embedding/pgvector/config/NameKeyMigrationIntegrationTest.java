package com.embedding.pgvector.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.source.domain.entity.Source;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * V8 göçünün eski kaynaklar için ad anahtarını Java hesabıyla aynı sonuçla doldurduğunu doğrular.
 */
class NameKeyMigrationIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.1-pg18")
            .asCompatibleSubstituteFor("postgres");

    @Test
    void givenSourcesCreatedBeforeV8_whenMigrateToV8_thenNameKeyBackfilledLikeJava() {
        try (PostgreSQLContainer container = new PostgreSQLContainer(PGVECTOR_IMAGE)) {
            // Arrange
            container.start();
            DataSource dataSource = new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(),
                    container.getPassword());
            Flyway beforeV8 = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                    .target("7").load();
            beforeV8.migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO workspaces (id, name) VALUES (42, 'Belgelerim')");
            List<String> names = List.of("İzin notları", "Eski destek kayıtları",
                    "Atlaskap-Lojistik-Surec-El-Kitabi");
            long id = 301;
            for (String name : names) {
                jdbc.update("INSERT INTO sources (id, workspace_id, name, kind, status) VALUES (?, 42, ?, 'PDF', 'READY')",
                        id++, name);
            }

            // Act
            Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("8").load().migrate();

            // Assert
            List<String> keys = jdbc.queryForList("SELECT name_key FROM sources ORDER BY id", String.class);
            assertThat(keys).containsExactly("izin notlari", "eski destek kayitlari",
                    "atlaskap-lojistik-surec-el-kitabi");
            assertThat(keys).containsExactlyElementsOf(names.stream().map(Source::toNameKey).toList());
        }
    }
}

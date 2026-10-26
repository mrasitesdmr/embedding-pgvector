package com.embedding.pgvector.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.support.PostgresIntegrationTest;
import com.embedding.pgvector.support.VectorFixtures;
import com.embedding.pgvector.search.service.VectorLiteral;
import com.embedding.pgvector.workspace.repository.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/**
 * Flyway göçlerinden çıkan şemayı doğrular: eklenti, tablolar, indeksler, ad anahtarı, Türkçe tsvector
 * kökleri ve dizi başlangıçları.
 */
class SchemaMigrationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    private Map<String, String> indexDefinitions() {
        return jdbcTemplate.queryForList("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = 'public'")
                .stream().collect(Collectors.toMap(row -> (String) row.get("indexname"),
                        row -> (String) row.get("indexdef")));
    }

    @Test
    void givenMigratedDatabase_whenInspectCatalog_thenExtensionTablesAndIndexesExist() {
        // Act
        Integer vectorExtensions = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'vector'", Integer.class);
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'",
                String.class);
        String embeddingType = jdbcTemplate.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod) FROM pg_attribute a
                WHERE a.attrelid = 'chunks'::regclass AND a.attname = 'embedding'
                """, String.class);
        Map<String, String> indexes = indexDefinitions();

        // Assert
        assertThat(vectorExtensions).isEqualTo(1);
        assertThat(tables).containsExactlyInAnyOrder("workspaces", "sources", "chunks", "ingestion_jobs",
                "search_events");
        assertThat(embeddingType).isEqualTo("vector(1024)");
        assertThat(indexes.get("idx_chunks_embedding")).contains("USING hnsw (embedding vector_cosine_ops)");
        assertThat(indexes.get("idx_chunks_tsv")).contains("USING gin (tsv)");
        assertThat(indexes.get("idx_chunks_workspace_id")).contains("USING btree (workspace_id)");
        assertThat(indexes.get("idx_sources_workspace_id")).contains("USING btree (workspace_id)");
        assertThat(indexes.get("idx_ingestion_jobs_workspace_id")).contains("USING btree (workspace_id)");
        assertThat(indexes.get("idx_search_events_workspace_id_created_at")).contains("USING btree");
        assertThat(indexes.get("uk_ingestion_jobs_source_id_active")).contains("UNIQUE").contains("WHERE");
    }

    @Test
    void givenMigratedSchema_whenInspectSources_thenNameKeyNotNullAndUniqueIndexOnWorkspaceAndNameKey() {
        // Act
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT data_type, is_nullable FROM information_schema.columns
                WHERE table_name = 'sources' AND column_name = 'name_key'
                """);
        Map<String, String> indexes = indexDefinitions();

        // Assert
        assertThat(column.get("data_type")).isEqualTo("text");
        assertThat(column.get("is_nullable")).isEqualTo("NO");
        assertThat(indexes.get("uk_sources_workspace_id_name_key")).contains("UNIQUE INDEX")
                .contains("(workspace_id, name_key)");
        assertThat(indexes).doesNotContainKey("uk_sources_workspace_id_name");
    }

    @Test
    void givenMigratedSchema_whenInspectSearchEvents_thenQueryKeyRequiredAndIndexed() {
        // Act
        Map<String, Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT column_name, data_type, is_nullable FROM information_schema.columns
                WHERE table_name = 'search_events' AND column_name IN ('query_key', 'keyword_count', 'best_distance')
                """).stream().collect(Collectors.toMap(row -> (String) row.get("column_name"), row -> row));
        Map<String, String> indexes = indexDefinitions();

        // Assert
        assertThat(columns.get("query_key")).containsEntry("data_type", "text").containsEntry("is_nullable", "NO");
        assertThat(columns.get("keyword_count")).containsEntry("data_type", "integer")
                .containsEntry("is_nullable", "YES");
        assertThat(columns.get("best_distance")).containsEntry("data_type", "double precision")
                .containsEntry("is_nullable", "YES");
        assertThat(indexes.get("idx_search_events_workspace_id_query_key")).contains("(workspace_id, query_key)");
    }

    @Test
    void givenHeadingAndContent_whenInserted_thenGeneratedTsvHoldsTurkishStems() {
        // Arrange
        jdbcTemplate.update("INSERT INTO workspaces (id, name) VALUES (42, 'Primary')");
        jdbcTemplate.update("""
                INSERT INTO sources (id, workspace_id, name, name_key, kind, status)
                VALUES (301, 42, 'Kaynak', 'kaynak', 'PDF', 'READY')
                """);

        // Act
        String tsv = jdbcTemplate.queryForObject("""
                INSERT INTO chunks (id, workspace_id, source_id, chunk_no, heading, content, token_count, embedding,
                                    embedding_model)
                VALUES (nextval('chunks_id_seq'), 42, 301, 0, 'Parola', 'sıfırlama', 3, CAST(? AS vector), 'bge-m3@1024')
                RETURNING tsv::text
                """, String.class, VectorLiteral.format(VectorFixtures.unit(0)));

        // Assert
        assertThat(tsv).contains("'parol':1").contains("'sıfırla':2");
    }

    @Test
    void givenFlywaySchema_whenContextStarts_thenHibernateValidateSucceeds() {
        // Assert
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(workspaceRepository.count()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",
                Integer.class)).isEqualTo(10);
    }

    @Test
    void givenSequences_whenReadAfterTruncateRestart_thenSourceSequenceStartsAt10001AndChunkSequenceIncrementsBy50() {
        // Act
        Long nextSource = jdbcTemplate.queryForObject("SELECT nextval('sources_id_seq')", Long.class);
        Long increment = jdbcTemplate.queryForObject(
                "SELECT seqincrement FROM pg_sequence WHERE seqrelid = 'chunks_id_seq'::regclass", Long.class);

        // Assert
        assertThat(nextSource).isEqualTo(10001L);
        assertThat(increment).isEqualTo(50L);
    }
}

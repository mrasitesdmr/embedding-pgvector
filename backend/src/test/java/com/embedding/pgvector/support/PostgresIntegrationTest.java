package com.embedding.pgvector.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Gerçek PostgreSQL ile çalışan entegrasyon testlerinin ortak tabanıdır; her testten önce ve sonra
 * tabloları boşaltır; Hibernate'in bellekte tuttuğu id aralığı çakışmasın diye parça id dizisini geri sarmaz.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
public abstract class PostgresIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void truncateTables() {
        var chunkSequence = jdbcTemplate.queryForMap("SELECT last_value, is_called FROM chunks_id_seq");
        jdbcTemplate.execute(
                "TRUNCATE search_events, ingestion_jobs, chunks, sources, workspaces RESTART IDENTITY CASCADE");
        jdbcTemplate.queryForObject("SELECT setval('chunks_id_seq', ?, ?)", Long.class,
                chunkSequence.get("last_value"), chunkSequence.get("is_called"));
    }
}

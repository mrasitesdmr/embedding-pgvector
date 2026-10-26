package com.embedding.pgvector.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.FixedClockTestConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;

/**
 * Arama kaydı istatistiklerinin İstanbul takvim günlerine göre sayıldığını ve kayıt yokken sıfır saydığını
 * doğrular.
 */
@Import(FixedClockTestConfiguration.class)
class SearchEventStatsIntegrationTest extends FakeEmbeddingIntegrationTest {

    private void insertEvent(long workspaceId, String createdAt, int resultCount, Integer resultLimit) {
        jdbcTemplate.update("""
                INSERT INTO search_events (workspace_id, created_at, query, query_key, mode, result_count, result_limit,
                                           total_ms)
                VALUES (?, ?, 'q', 'q', 'KEYWORD', ?, ?, 5)
                """, workspaceId, Timestamp.from(Instant.parse(createdAt)), resultCount, resultLimit);
    }

    @Test
    void givenEventsAroundIstanbulDayBoundaries_whenGetSearchEventStats_thenCountsByIstanbulCalendarDays() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");
        insertEvent(42L, "2026-10-25T09:00:00Z", 10, 10);
        insertEvent(42L, "2026-10-25T08:00:00Z", 0, 10);
        insertEvent(42L, "2026-10-24T21:30:00Z", 3, 10);
        insertEvent(42L, "2026-10-24T20:59:00Z", 10, 10);
        insertEvent(42L, "2026-10-18T21:00:00Z", 0, 5);
        insertEvent(42L, "2026-10-18T20:59:00Z", 0, 10);
        insertEvent(42L, "2026-10-20T12:00:00Z", 4, null);
        insertEvent(19L, "2026-10-25T09:00:00Z", 0, 10);

        // Act
        var response = api().get("/api/workspaces/42/search-events/stats");

        // Assert
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("totalCount").asLong()).isEqualTo(7);
        assertThat(body.get("todayCount").asLong()).isEqualTo(3);
        assertThat(body.get("last7DaysCount").asLong()).isEqualTo(6);
        assertThat(body.get("zeroResultCount").asLong()).isEqualTo(2);
        assertThat(body.get("lowResultCount").asLong()).isEqualTo(1);
        assertThat(body.get("zeroResultRate").asDouble()).isCloseTo(0.333333, within(1e-6));
        assertThat(body.get("lowResultRate").asDouble()).isCloseTo(0.166667, within(1e-6));
        assertThat(Instant.parse(body.get("todayStart").asString())).isEqualTo(Instant.parse("2026-10-24T21:00:00Z"));
        assertThat(Instant.parse(body.get("last7DaysStart").asString()))
                .isEqualTo(Instant.parse("2026-10-18T21:00:00Z"));
        assertThat(body.get("timeZone").asString()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void givenNoEvents_whenGetSearchEventStats_thenZeroCountsAndNullRates() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");

        // Act
        JsonNode body = json(api().get("/api/workspaces/42/search-events/stats"));

        // Assert
        for (String field : new String[] {"totalCount", "todayCount", "last7DaysCount", "zeroResultCount",
                "lowResultCount"}) {
            assertThat(body.get(field).asLong()).isZero();
        }
        assertThat(body.get("zeroResultRate").isNull()).isTrue();
        assertThat(body.get("lowResultRate").isNull()).isTrue();
    }

    @Test
    void givenUnknownWorkspace_whenGetSearchEventStats_thenReturns404WorkspaceNotFound() {
        // Act
        var response = api().get("/api/workspaces/999/search-events/stats");

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }
}

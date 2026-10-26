package com.embedding.pgvector.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.ApiTestClient;
import com.embedding.pgvector.search.domain.entity.SearchEvent;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import com.embedding.pgvector.search.repository.SearchEventRepository;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.SampleFiles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/**
 * Popüler arama metinleri ucunu doğrular: büyük küçük harf gruplaması, ilk beş sıra, mesafe eşiği ve
 * çalışma alanı ayrımı.
 */
@TestPropertySource(properties = "search.popular-max-distance=0.45")
class PopularQueryIntegrationTest extends FakeEmbeddingIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-10-25T09:00:00Z");

    @Autowired
    private SearchEventRepository searchEventRepository;

    private static Instant at(int minute) {
        return T0.plusSeconds(60L * minute);
    }

    private void event(long workspaceId, Instant createdAt, String query, int resultCount, Integer keywordCount,
            Double bestDistance) {
        searchEventRepository.save(SearchEvent.record(workspaceId, createdAt, "Temsilci paneli", query,
                SearchMode.HYBRID, null, null, 10, resultCount, null, null, 5L, keywordCount, bestDistance));
    }

    private JsonNode popular(long workspaceId) {
        ApiTestClient.Response response = api().get("/api/workspaces/" + workspaceId + "/search-events/popular-queries");
        assertThat(response.status()).isEqualTo(200);
        return json(response).get("queries");
    }

    private static List<String> queriesOf(JsonNode queries) {
        List<String> result = new ArrayList<>();
        queries.forEach(node -> result.add(node.get("query").asString()));
        return result;
    }

    private static List<Long> countsOf(JsonNode queries) {
        List<Long> result = new ArrayList<>();
        queries.forEach(node -> result.add(node.get("searchCount").asLong()));
        return result;
    }

    @Test
    void givenRepeatedSearchesWithCaseAndSpaceVariants_whenListPopularQueries_thenGroupsOrdersAndShowsLatestText() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        event(42L, at(1), "Yıllık izin kaç gün?", 5, 1, null);
        event(42L, at(2), "parolamı unuttum hesabım kilitlendi", 10, 0, 0.33);
        event(42L, at(3), "PAROLAMI unuttum hesabım kilitlendi", 10, 0, 0.35);
        event(42L, at(4), "Masraf beyanı kaç gün içinde yapılır?", 5, 2, null);
        event(42L, at(5), "kuantum fiziği", 10, 0, 0.58);
        event(42L, at(6), "boş arama", 0, null, null);

        // Act
        JsonNode queries = popular(42L);

        // Assert
        assertThat(queriesOf(queries)).containsExactly("PAROLAMI unuttum hesabım kilitlendi",
                "Masraf beyanı kaç gün içinde yapılır?", "Yıllık izin kaç gün?");
        assertThat(countsOf(queries)).containsExactly(2L, 1L, 1L);
        assertThat(Instant.parse(queries.get(0).get("lastSearchedAt").asString())).isEqualTo(at(3));
        assertThat(Instant.parse(queries.get(1).get("lastSearchedAt").asString())).isEqualTo(at(4));
        assertThat(Instant.parse(queries.get(2).get("lastSearchedAt").asString())).isEqualTo(at(1));
    }

    @Test
    void givenSevenCountedQueries_whenListPopularQueries_thenReturnsTopFive() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        int minute = 1;
        for (int query = 1; query <= 7; query++) {
            for (int repeat = 0; repeat < 8 - query; repeat++) {
                event(42L, at(minute++), "sorgu " + query, 3, 1, null);
            }
        }

        // Act
        JsonNode queries = popular(42L);

        // Assert
        assertThat(countsOf(queries)).containsExactly(7L, 6L, 5L, 4L, 3L);
        assertThat(queriesOf(queries)).containsExactly("sorgu 1", "sorgu 2", "sorgu 3", "sorgu 4", "sorgu 5");
    }

    @Test
    void givenEventsAroundThreshold_whenListPopularQueries_thenCountsKeywordOrCloseVectorOnly() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        event(42L, at(1), "sayilir kelime", 3, 1, null);
        event(42L, at(2), "sayilir esik", 10, null, 0.45);
        event(42L, at(3), "sayilir yakin", 10, 0, 0.30);
        event(42L, at(4), "sayilmaz uzak", 10, 0, 0.46);
        event(42L, at(5), "sayilmaz bos", 3, null, null);
        event(42L, at(6), "sayilmaz sonucsuz", 0, 0, 0.10);

        // Act
        JsonNode queries = popular(42L);

        // Assert
        assertThat(queriesOf(queries)).containsExactly("sayilir yakin", "sayilir esik", "sayilir kelime");
    }

    @Test
    void givenTiedCounts_whenListPopularQueries_thenNewestFirst() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        event(42L, at(1), "A", 3, 1, null);
        event(42L, at(2), "B", 3, 1, null);
        event(42L, at(3), "C", 3, 1, null);
        event(42L, at(3), "D", 3, 1, null);

        // Act
        JsonNode queries = popular(42L);

        // Assert
        assertThat(queriesOf(queries)).containsExactly("D", "C", "B", "A");
    }

    @Test
    void givenOtherWorkspaceSearches_whenListPopularQueries_thenExcludesThem() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");
        event(19L, at(1), "başka alan", 3, 1, null);
        event(19L, at(2), "başka alan", 3, 1, null);
        event(19L, at(3), "başka alan 2", 3, 1, null);

        // Act
        JsonNode other = popular(19L);
        JsonNode own = popular(42L);

        // Assert
        assertThat(countsOf(other)).containsExactly(2L, 1L);
        assertThat(own.size()).isZero();
    }

    @Test
    void givenUnknownWorkspace_whenListPopularQueries_thenReturns404ProblemDetail() {
        // Act
        ApiTestClient.Response response = api().get("/api/workspaces/999/search-events/popular-queries");

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(response.contentType()).startsWith("application/problem+json");
        assertThat(json(response).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenKeywordSearchesViaApi_whenListPopularQueries_thenReflectsThem() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        ApiTestClient.Response created = createSource(42L, "İzin notları", "izin-notlari.md",
                SampleFiles.utf8("# Yıllık izin\nYıllık izin 14 iş günüdür."));
        assertThat(created.status()).isEqualTo(202);
        JsonNode job = awaitFinishedJob(42L, json(created).get("jobId").asLong());
        assertThat(job.get("stage").asString()).isEqualTo("DONE");

        // Act
        search(42L, "{\"query\":\"Yıllık izin\",\"mode\":\"KEYWORD\"}");
        search(42L, "{\"query\":\"Yıllık izin\",\"mode\":\"KEYWORD\"}");
        search(42L, "{\"query\":\" yıllık İZİN \",\"mode\":\"KEYWORD\"}");
        JsonNode queries = popular(42L);

        // Assert
        assertThat(queries.size()).isEqualTo(1);
        assertThat(queries.get(0).get("query").asString()).isEqualTo("yıllık İZİN");
        assertThat(queries.get(0).get("searchCount").asLong()).isEqualTo(3L);
    }
}

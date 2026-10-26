package com.embedding.pgvector.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.embedding.pgvector.search.service.VectorLiteral;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.VectorFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;

/**
 * Arama akışını gerçek PostgreSQL ve pgvector ile doğrular: kelime, vektör ve hibrit mod, sıra birleştirme
 * ile indeks kullanımı.
 */
class SearchIntegrationTest extends FakeEmbeddingIntegrationTest {

    private static final String PASSWORD_QUERY = "Parolamı unuttum, online işlemlere giremiyorum";

    private void seedWorkspace(long id, String name, long sourceId) {
        insertWorkspace(id, name);
        insertSource(sourceId, id, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 0, true);
    }

    private List<Long> insertBulk(long workspaceId, long sourceId, int count, java.util.function.IntFunction<float[]> vectors,
            String prefix) {
        List<Object[]> rows = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            rows.add(new Object[] {workspaceId, sourceId, index, prefix + " " + index, prefix + " içerik " + index, 5,
                    VectorLiteral.format(vectors.apply(index)), "bge-m3@1024"});
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO chunks (id, workspace_id, source_id, chunk_no, heading, content, token_count, embedding,
                                    embedding_model)
                VALUES (nextval('chunks_id_seq'), ?, ?, ?, ?, ?, ?, CAST(? AS vector), ?)
                """, rows);
        return jdbcTemplate.queryForList("SELECT id FROM chunks WHERE workspace_id = ? ORDER BY id", Long.class,
                workspaceId);
    }

    private static float[] nearQuery(int index) {
        return VectorFixtures.around(0, 0.03, index);
    }

    private void seedSelectiveWorkspaces() {
        seedWorkspace(1L, "Alan A", 101L);
        seedWorkspace(2L, "Alan B", 201L);
        insertBulk(1L, 101L, 20, index -> VectorFixtures.random(1000L + index), "uzak");
        insertBulk(2L, 201L, 300, SearchIntegrationTest::nearQuery, "yakın");
        embeddingClient.register("nakil sorgusu", VectorFixtures.unit(0));
    }

    private String vectorSearch(String strategy, boolean explain) {
        return "{\"query\":\"nakil sorgusu\",\"mode\":\"VECTOR\",\"efSearch\":40,\"limit\":10,\"filterStrategy\":\""
                + strategy + "\",\"explain\":" + explain + "}";
    }

    @Test
    void givenLabPasswordChunk_whenKeywordSearchWithSifre_thenNoHitsButParolaQueryFindsIt() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertChunk(42L, 301L, 0, "Online İşlemler parolanı sıfırlama",
                "Online işlemler parolanı sıfırlamak için giriş ekranındaki bağlantıyı kullan.",
                VectorFixtures.unit(0));

        // Act
        JsonNode miss = search(42L, "{\"query\":\"" + PASSWORD_QUERY + "\",\"mode\":\"KEYWORD\"}");
        JsonNode hit = search(42L, "{\"query\":\"parolanı sıfırlama\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(miss.get("returnedCount").asInt()).isZero();
        assertThat(miss.get("keywordCount").asInt()).isZero();
        assertThat(hit.get("returnedCount").asInt()).isEqualTo(1);
        JsonNode first = hit.get("hits").get(0);
        assertThat(first.get("keywordRank").asInt()).isEqualTo(1);
        assertThat(first.get("keywordScore").asDouble()).isGreaterThan(0);
        assertThat(first.get("matchedBy").get(0).asString()).isEqualTo("KEYWORD");
        assertThat(first.get("matchedBy")).hasSize(1);
        assertThat(first.get("distance").isNull()).isTrue();
        assertThat(first.get("vectorRank").isNull()).isTrue();
        assertThat(hit.get("vectorCount").isNull()).isTrue();
        assertThat(hit.get("filterStrategy").isNull()).isTrue();
        assertThat(hit.get("timings").get("embeddingMs").isNull()).isTrue();
        assertThat(embeddingClient.singleTexts()).isEmpty();
    }

    @Test
    void givenChunkInBothBranches_whenHybridSearch_thenRrfScoreSumsBothRanksAndRanksFirst() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        long closest = insertChunk(42L, 301L, 0, "Alakasız", "tamamen başka bir konu", VectorFixtures.unit(0));
        long both = insertChunk(42L, 301L, 1, "İzin", "fiberhat izin kurulumu", VectorFixtures.mix(0, 1.0, 1, 1.0));
        insertChunk(42L, 301L, 2, "Uzak", "ilgisiz", VectorFixtures.unit(7));
        embeddingClient.register("fiberhat", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"fiberhat\",\"mode\":\"HYBRID\",\"limit\":10}");

        // Assert
        JsonNode first = result.get("hits").get(0);
        JsonNode second = result.get("hits").get(1);
        assertThat(first.get("chunkId").asLong()).isEqualTo(both);
        assertThat(first.get("rank").asInt()).isEqualTo(1);
        assertThat(first.get("rrfScore").asDouble()).isCloseTo(1.0 / 61 + 1.0 / 62, within(1e-6));
        assertThat(first.get("keywordRank").asInt()).isEqualTo(1);
        assertThat(first.get("vectorRank").asInt()).isEqualTo(2);
        assertThat(first.get("matchedBy")).extracting(JsonNode::asString).containsExactly("KEYWORD", "SEMANTIC");
        assertThat(second.get("chunkId").asLong()).isEqualTo(closest);
        assertThat(second.get("rank").asInt()).isEqualTo(2);
        assertThat(second.get("rrfScore").asDouble()).isCloseTo(1.0 / 61, within(1e-6));
        assertThat(second.get("matchedBy")).extracting(JsonNode::asString).containsExactly("SEMANTIC");
        assertThat(second.get("keywordRank").isNull()).isTrue();
        assertThat(result.get("keywordCount").asInt()).isEqualTo(1);
        assertThat(result.get("vectorCount").asInt()).isEqualTo(3);
        assertThat(result.get("mode").asString()).isEqualTo("HYBRID");
        assertThat(result.get("filterStrategy").asString()).isEqualTo("ITERATIVE_SCAN");
        assertThat(result.get("efSearch").asInt()).isEqualTo(40);
        assertThat(embeddingClient.singleTexts()).containsExactly("fiberhat");
    }

    @Test
    void givenStopWordOnlyQuery_whenHybridSearch_thenResultEqualsVectorBranch() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        long nearest = insertChunk(42L, 301L, 0, "Bir", "birinci içerik", VectorFixtures.unit(0));
        long middle = insertChunk(42L, 301L, 1, "İki", "ikinci içerik", VectorFixtures.mix(0, 1.0, 1, 0.5));
        long farthest = insertChunk(42L, 301L, 2, "Üç", "üçüncü içerik", VectorFixtures.mix(0, 1.0, 1, 2.0));
        embeddingClient.register("ve ile", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"ve ile\",\"mode\":\"HYBRID\"}");

        // Assert
        assertThat(result.get("keywordCount").asInt()).isZero();
        assertThat(result.get("hits")).extracting(hit -> hit.get("chunkId").asLong())
                .containsExactly(nearest, middle, farthest);
        for (int index = 0; index < 3; index++) {
            JsonNode hit = result.get("hits").get(index);
            assertThat(hit.get("vectorRank").asInt()).isEqualTo(index + 1);
            assertThat(hit.get("rrfScore").asDouble()).isCloseTo(1.0 / (60 + index + 1), within(1e-9));
            assertThat(hit.get("keywordRank").isNull()).isTrue();
        }
    }

    @Test
    void givenVectorMode_whenSearch_thenHitsOrderedByCosineDistanceWithoutRrf() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        long far = insertChunk(42L, 301L, 0, "Uzak", "uzak içerik", VectorFixtures.unit(9));
        long near = insertChunk(42L, 301L, 1, "Yakın", "yakın içerik", VectorFixtures.mix(0, 1.0, 1, 1.0));
        embeddingClient.register("soru", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"soru\",\"mode\":\"vector\"}");

        // Assert
        assertThat(result.get("hits")).extracting(hit -> hit.get("chunkId").asLong()).containsExactly(near, far);
        assertThat(result.get("hits").get(0).get("distance").asDouble()).isCloseTo(1 - Math.sqrt(0.5), within(1e-6));
        assertThat(result.get("hits").get(1).get("distance").asDouble()).isCloseTo(1.0, within(1e-6));
        assertThat(result.get("hits").get(0).get("rrfScore").isNull()).isTrue();
        assertThat(result.get("hits").get(0).get("matchedTerms")).isEmpty();
        assertThat(result.get("keywordCount").isNull()).isTrue();
        assertThat(result.get("vectorCount").asInt()).isEqualTo(2);
        assertThat(result.get("vectorShort").asBoolean()).isTrue();
    }

    @Test
    void givenLimitBelowRowCount_whenSearchVector_thenReturnsOnlyLimitAndNotShort() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertBulk(42L, 301L, 6, SearchIntegrationTest::nearQuery, "yakın");
        embeddingClient.register("soru", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"soru\",\"mode\":\"VECTOR\",\"limit\":3}");

        // Assert
        assertThat(result.get("returnedCount").asInt()).isEqualTo(3);
        assertThat(result.get("limit").asInt()).isEqualTo(3);
        assertThat(result.get("vectorShort").asBoolean()).isFalse();
    }

    @Test
    void givenSelectiveWorkspaceFilter_whenPostFilterWithEf40_thenShortVectorBranch() {
        // Arrange
        seedSelectiveWorkspaces();

        // Act
        JsonNode result = search(1L, vectorSearch("POST_FILTER", false));

        // Assert
        assertThat(result.get("vectorCount").asInt()).isLessThan(10);
        assertThat(result.get("vectorShort").asBoolean()).isTrue();
        assertThat(result.get("filterStrategy").asString()).isEqualTo("POST_FILTER");
    }

    @Test
    void givenSelectiveWorkspaceFilter_whenIterativeScan_thenFullVectorBranch() {
        // Arrange
        seedSelectiveWorkspaces();

        // Act
        JsonNode result = search(1L, vectorSearch("ITERATIVE_SCAN", false));

        // Assert
        assertThat(result.get("vectorCount").asInt()).isEqualTo(10);
        assertThat(result.get("vectorShort").asBoolean()).isFalse();
        assertThat(result.get("returnedCount").asInt()).isEqualTo(10);
    }

    @Test
    void givenSearchWithEf100_whenNextTransactionReadsSetting_thenDefaultRestored() {
        // Arrange
        seedSelectiveWorkspaces();

        // Act
        search(1L, "{\"query\":\"nakil sorgusu\",\"mode\":\"VECTOR\",\"efSearch\":100,\"filterStrategy\":\"POST_FILTER\"}");
        String efSearch = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT current_setting('hnsw.ef_search')", String.class));
        String enableSort = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT current_setting('enable_sort')", String.class));
        String iterativeScan = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT current_setting('hnsw.iterative_scan')", String.class));

        // Assert
        assertThat(efSearch).isEqualTo("40");
        assertThat(enableSort).isEqualTo("on");
        assertThat(iterativeScan).isEqualTo("off");
    }

    @Test
    void givenExplainTrue_whenVectorSearch_thenPlanShowsHnswIndexScanAndRowsRemoved() {
        // Arrange
        seedSelectiveWorkspaces();

        // Act
        JsonNode explained = search(1L, vectorSearch("POST_FILTER", true));
        JsonNode plain = search(1L, vectorSearch("POST_FILTER", false));
        JsonNode keywordExplained = search(1L, "{\"query\":\"uzak\",\"mode\":\"KEYWORD\",\"explain\":true}");

        // Assert
        String plan = explained.get("vectorPlan").asString();
        assertThat(plan).contains("Index Scan using idx_chunks_embedding").contains("Rows Removed by Filter");
        assertThat(plain.get("vectorPlan").isNull()).isTrue();
        assertThat(keywordExplained.get("vectorPlan").isNull()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"KEYWORD", "VECTOR", "HYBRID"})
    void givenTwoWorkspacesWithSimilarChunks_whenSearchInEachMode_thenOnlyOwnWorkspaceChunks(String mode) {
        // Arrange
        seedWorkspace(19L, "Deneme alanı", 191L);
        seedWorkspace(42L, "Belgelerim", 301L);
        for (int index = 0; index < 3; index++) {
            insertChunk(19L, 191L, index, "Parolamı unuttum " + index, "şifre sıfırlama adımları " + index,
                    VectorFixtures.mix(0, 1.0, 1 + index, 0.1));
            insertChunk(42L, 301L, index, "Parolamı unuttum " + index, "şifre sıfırlama adımları " + index,
                    VectorFixtures.mix(0, 1.0, 10 + index, 0.1));
        }
        embeddingClient.register("şifre sıfırlama", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(19L, "{\"query\":\"şifre sıfırlama\",\"mode\":\"" + mode + "\",\"limit\":10}");

        // Assert
        assertThat(result.get("hits")).isNotEmpty();
        for (JsonNode hit : result.get("hits")) {
            assertThat(hit.get("sourceId").asLong()).isEqualTo(191L);
        }
        assertThat(result.get("returnedCount").asInt()).isEqualTo(3);
    }

    @Test
    void givenThreeSearches_whenListSearchEvents_thenNewestFirstWithCountsAndTopHeading() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertChunk(42L, 301L, 0, "Online İşlemler parolanı sıfırlama",
                "Online işlemler parolanı sıfırlamak için bağlantıyı kullan.", VectorFixtures.unit(0));
        insertChunk(42L, 301L, 1, "IZN-99 hata kodu", "IZN-99 hata kodu PPPoE sorunudur.", VectorFixtures.unit(1));
        embeddingClient.register("IZN-99", VectorFixtures.unit(1));

        // Act
        search(42L, "{\"query\":\"parolanı sıfırlama\",\"mode\":\"KEYWORD\",\"channel\":\"İzin notları\"}");
        search(42L, "{\"query\":\"netflix\",\"mode\":\"KEYWORD\"}");
        search(42L, "{\"query\":\"IZN-99\",\"mode\":\"HYBRID\"}");
        JsonNode all = json(api().get("/api/workspaces/42/search-events"));
        JsonNode firstPage = json(api().get("/api/workspaces/42/search-events?size=2"));
        JsonNode other = json(api().get("/api/workspaces/999/search-events"));

        // Assert
        JsonNode events = all.get("content");
        assertThat(events.get(0).get("query").asString()).isEqualTo("IZN-99");
        assertThat(events.get(0).get("mode").asString()).isEqualTo("HYBRID");
        assertThat(events.get(0).get("filterStrategy").asString()).isEqualTo("ITERATIVE_SCAN");
        assertThat(events.get(0).get("topChunkHeading").asString()).isEqualTo("IZN-99 hata kodu");
        assertThat(events.get(1).get("query").asString()).isEqualTo("netflix");
        assertThat(events.get(1).get("resultCount").asInt()).isZero();
        assertThat(events.get(1).get("topChunkId").isNull()).isTrue();
        assertThat(events.get(1).get("topChunkHeading").isNull()).isTrue();
        assertThat(events.get(2).get("topChunkHeading").asString()).isEqualTo("Online İşlemler parolanı sıfırlama");
        assertThat(events.get(2).get("channel").asString()).isEqualTo("İzin notları");
        assertThat(events.get(2).get("resultCount").asInt()).isEqualTo(1);
        assertThat(events.get(2).get("filterStrategy").isNull()).isTrue();
        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(3);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(other.get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenFailedSearch_whenListSearchEvents_thenNothingRecorded() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertChunk(42L, 301L, 0, "h", "c", VectorFixtures.unit(0), "other@1024");

        // Act
        var rejected = api().postJson("/api/workspaces/42/search", "{\"query\":\"x\",\"mode\":\"VECTOR\"}");
        var invalid = api().postJson("/api/workspaces/42/search", "{\"query\":\"\"}");

        // Assert
        assertThat(rejected.status()).isEqualTo(409);
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM search_events", Integer.class)).isZero();
    }

    @Test
    void givenTs999Chunk_whenHybridSearch_thenMatchedTermsPointToMatchingWords() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertChunk(42L, 301L, 0, "Hata kodları", "IZN-99 hata kodu nedir", VectorFixtures.mix(0, 1.0, 1, 1.0));
        insertChunk(42L, 301L, 1, "Başka", "alakasız metin", VectorFixtures.unit(0));
        embeddingClient.register("IZN-99 hata kodu", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"IZN-99 hata kodu\",\"mode\":\"HYBRID\"}");

        // Assert
        JsonNode keywordHit = result.get("hits").get(0);
        List<String> terms = new ArrayList<>();
        keywordHit.get("matchedTerms").forEach(term -> terms.add(term.get("field").asString() + ":"
                + term.get("start").asInt() + "-" + term.get("end").asInt()));
        assertThat(keywordHit.get("heading").asString()).isEqualTo("Hata kodları");
        assertThat(terms).contains("CONTENT:0-6", "CONTENT:7-11", "CONTENT:12-16", "HEADING:0-4")
                .doesNotContain("CONTENT:17-22");
        JsonNode vectorOnly = result.get("hits").get(1);
        assertThat(vectorOnly.get("matchedTerms")).isEmpty();
        assertThat(vectorOnly.get("matchedBy")).extracting(JsonNode::asString).containsExactly("SEMANTIC");
    }

    @Test
    void givenUnknownWorkspace_whenSearch_thenReturns404AndEmbeddingNotCalled() {
        // Act
        var response = api().postJson("/api/workspaces/999/search", "{\"query\":\"x\",\"mode\":\"VECTOR\"}");

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
        assertThat(embeddingClient.singleTexts()).isEmpty();
    }

    @Test
    void givenEmbeddingUnavailable_whenVectorSearch_thenReturns503ButKeywordStillWorks() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        insertChunk(42L, 301L, 0, "h", "izinkelime", VectorFixtures.unit(0));
        embeddingClient.failWith(new com.embedding.pgvector.exception.ServiceUnavailableException(
                "EMBEDDING_UNAVAILABLE", "down"));

        // Act
        var vector = api().postJson("/api/workspaces/42/search", "{\"query\":\"x\",\"mode\":\"VECTOR\"}");
        var keyword = api().postJson("/api/workspaces/42/search", "{\"query\":\"izinkelime\",\"mode\":\"KEYWORD\"}");

        // Assert
        assertThat(vector.status()).isEqualTo(503);
        assertThat(json(vector).get("errorCode").asString()).isEqualTo("EMBEDDING_UNAVAILABLE");
        assertThat(keyword.status()).isEqualTo(200);
        assertThat(json(keyword).get("returnedCount").asInt()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"KEYWORD", "VECTOR", "HYBRID"})
    void givenChunkNoSeven_whenSearchChunksInEachMode_thenHitCarriesChunkNoSeven(String mode) {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        long chunkId = insertChunk(42L, 301L, 7, "IZN-99 hata kodu", "IZN-99 hata kodu PPPoE sorunudur.",
                VectorFixtures.unit(0));
        insertChunk(42L, 301L, 3, "Alakasız", "tamamen başka konu", VectorFixtures.unit(9));
        embeddingClient.register("IZN-99 hata kodu", VectorFixtures.unit(0));

        // Act
        JsonNode result = search(42L, "{\"query\":\"IZN-99 hata kodu\",\"mode\":\"" + mode + "\"}");

        // Assert
        JsonNode first = result.get("hits").get(0);
        assertThat(first.get("chunkId").asLong()).isEqualTo(chunkId);
        assertThat(first.get("chunkNo").asInt()).isEqualTo(7);
    }

    @Test
    void givenSearchWithLimitFive_whenListSearchEvents_thenEventLimitIsFive() {
        // Arrange
        seedWorkspace(42L, "Belgelerim", 301L);
        for (int index = 0; index < 8; index++) {
            insertChunk(42L, 301L, index, "İzin " + index, "izinkelime içerik " + index, VectorFixtures.unit(index));
        }

        // Act
        JsonNode result = search(42L, "{\"query\":\"izinkelime\",\"mode\":\"KEYWORD\",\"limit\":5}");
        JsonNode events = json(api().get("/api/workspaces/42/search-events")).get("content");

        // Assert
        assertThat(events.get(0).get("limit").asInt()).isEqualTo(5);
        assertThat(events.get(0).get("resultCount").asInt()).isEqualTo(result.get("returnedCount").asInt()).isEqualTo(5);
    }

    @Test
    void givenEventStoredWithoutLimit_whenListSearchEvents_thenLimitIsNull() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        jdbcTemplate.update("""
                INSERT INTO search_events (workspace_id, created_at, query, query_key, mode, result_count, total_ms)
                VALUES (42, now(), 'eski arama', 'eski arama', 'KEYWORD', 4, 12)
                """);

        // Act
        JsonNode events = json(api().get("/api/workspaces/42/search-events")).get("content");

        // Assert
        assertThat(events).hasSize(1);
        assertThat(events.get(0).has("limit")).isTrue();
        assertThat(events.get(0).get("limit").isNull()).isTrue();
        assertThat(events.get(0).get("resultCount").asInt()).isEqualTo(4);
    }
}

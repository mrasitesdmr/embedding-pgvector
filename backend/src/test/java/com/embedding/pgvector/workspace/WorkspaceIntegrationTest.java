package com.embedding.pgvector.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.search.service.VectorLiteral;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingIntegrationTest;
import com.embedding.pgvector.support.VectorFixtures;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Çalışma alanı uçlarını gerçek PostgreSQL ile doğrular: sayılar, sayfalama, 404 ve parçalardaki embedding
 * modeli listesi.
 */
class WorkspaceIntegrationTest extends FakeEmbeddingIntegrationTest {

    @Test
    void givenWorkspaceWithTwoSourcesAndFiveChunks_whenGetWorkspace_thenReturnsCounts() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertSource(301L, 42L, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 3, true);
        insertSource(302L, 42L, "Kılavuz", SourceKind.PDF, "3.1", "k.pdf", "READY", 2, true);
        for (int index = 0; index < 3; index++) {
            insertChunk(42L, 301L, index, "h", "c" + index, VectorFixtures.unit(index));
        }
        for (int index = 0; index < 2; index++) {
            insertChunk(42L, 302L, index, "h", "k" + index, VectorFixtures.unit(10 + index));
        }

        // Act
        var response = api().get("/api/workspaces/42");

        // Assert
        assertThat(response.status()).isEqualTo(200);
        JsonNode body = json(response);
        assertThat(body.get("workspaceId").asLong()).isEqualTo(42L);
        assertThat(body.get("name").asString()).isEqualTo("Belgelerim");
        assertThat(body.get("sourceCount").asInt()).isEqualTo(2);
        assertThat(body.get("chunkCount").asLong()).isEqualTo(5L);
    }

    @Test
    void givenUnknownWorkspace_whenGetWorkspace_thenReturns404WorkspaceNotFound() {
        // Act
        var response = api().get("/api/workspaces/999");

        // Assert
        assertThat(response.status()).isEqualTo(404);
        assertThat(json(response).get("errorCode").asString()).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenTwoWorkspaces_whenListWithSizeOne_thenTotalElementsIsTwoAndOrderedById() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");

        // Act
        JsonNode firstPage = json(api().get("/api/workspaces?size=1"));
        JsonNode secondPage = json(api().get("/api/workspaces?size=1&page=1"));
        JsonNode defaultPage = json(api().get("/api/workspaces"));

        // Assert
        assertThat(firstPage.get("totalElements").asLong()).isEqualTo(2);
        assertThat(firstPage.get("totalPages").asInt()).isEqualTo(2);
        assertThat(firstPage.get("content").get(0).get("workspaceId").asLong()).isEqualTo(19L);
        assertThat(secondPage.get("content").get(0).get("workspaceId").asLong()).isEqualTo(42L);
        assertThat(defaultPage.get("size").asInt()).isEqualTo(50);
        assertThat(defaultPage.get("content")).hasSize(2);
        assertThat(VectorLiteral.format(new float[0])).isEqualTo("[]");
    }

    @Test
    void givenChunksWithTwoModelTags_whenListWorkspaces_thenEmbeddingModelsSortedDistinct() {
        // Arrange
        insertWorkspace(42L, "Belgelerim");
        insertWorkspace(19L, "Deneme alanı");
        insertSource(301L, 42L, "İzin notları", SourceKind.WEB_CRAWL, null, null, "READY", 4, true);
        for (int index = 0; index < 3; index++) {
            insertChunk(42L, 301L, index, "h", "c" + index, VectorFixtures.unit(index));
        }
        insertChunk(42L, 301L, 3, "h", "c3", VectorFixtures.unit(3), "aaa@8");

        // Act
        JsonNode list = json(api().get("/api/workspaces")).get("content");
        JsonNode single = json(api().get("/api/workspaces/42"));

        // Assert
        JsonNode kiyi = list.get(0);
        JsonNode primary = list.get(1);
        assertThat(kiyi.get("workspaceId").asLong()).isEqualTo(19L);
        assertThat(kiyi.get("embeddingModels").isArray()).isTrue();
        assertThat(kiyi.get("embeddingModels")).isEmpty();
        assertThat(primary.get("embeddingModels")).extracting(JsonNode::asString).containsExactly("aaa@8", "bge-m3@1024");
        assertThat(single.get("embeddingModels")).extracting(JsonNode::asString)
                .containsExactly("aaa@8", "bge-m3@1024");
    }
}

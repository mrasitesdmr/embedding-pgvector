package com.embedding.pgvector.chunk.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.embedding.pgvector.chunk.domain.entity.Chunk;
import com.embedding.pgvector.search.service.VectorLiteral;
import com.embedding.pgvector.source.domain.entity.Source;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.ChunkFixtures;
import com.embedding.pgvector.support.SourceFixtures;
import com.embedding.pgvector.support.TestcontainersConfiguration;
import com.embedding.pgvector.support.VectorFixtures;
import com.embedding.pgvector.support.WorkspaceFixtures;
import com.embedding.pgvector.workspace.domain.entity.Workspace;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/**
 * ChunkRepository'nin vektör kaydını, parça sıralamasını, sayfalamayı, silmeyi ve embedding modeli
 * sorgularını gerçek pgvector ile doğrular.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class ChunkRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChunkRepository chunkRepository;

    private Source persistSource(long workspaceId, long sourceId) {
        Workspace workspace = entityManager.find(Workspace.class, workspaceId);
        if (workspace == null) {
            workspace = WorkspaceFixtures.workspace(workspaceId, "Workspace " + workspaceId);
            entityManager.persist(workspace);
        }
        Source source = SourceFixtures.readySource(workspace, sourceId, SourceKind.MARKDOWN, null, 0);
        entityManager.persist(source);
        return source;
    }

    private double distanceOf(long chunkId, float[] query) {
        Number distance = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT embedding <=> CAST(:q AS vector) FROM chunks WHERE id = :id")
                .setParameter("q", VectorLiteral.format(query)).setParameter("id", chunkId).getSingleResult();
        return distance.doubleValue();
    }

    @Test
    void givenChunkPersistedWithFloatArray_whenReadDistanceNatively_thenVectorRoundTrips() {
        // Arrange
        Source source = persistSource(42L, 301L);
        Chunk chunk = ChunkFixtures.chunk(source, 0, "Başlık", "içerik", VectorFixtures.unit(3));
        entityManager.persist(chunk);
        entityManager.flush();
        entityManager.clear();

        // Act
        double sameDistance = distanceOf(chunk.getId(), VectorFixtures.unit(3));
        double orthogonalDistance = distanceOf(chunk.getId(), VectorFixtures.unit(4));
        String model = (String) entityManager.getEntityManager()
                .createNativeQuery("SELECT embedding_model FROM chunks WHERE id = :id")
                .setParameter("id", chunk.getId()).getSingleResult();
        Number dimensions = (Number) entityManager.getEntityManager()
                .createNativeQuery("SELECT vector_dims(embedding) FROM chunks WHERE id = :id")
                .setParameter("id", chunk.getId()).getSingleResult();

        // Assert
        assertThat(sameDistance).isCloseTo(0.0, within(1e-6));
        assertThat(orthogonalDistance).isCloseTo(1.0, within(1e-6));
        assertThat(model).isEqualTo("bge-m3@1024");
        assertThat(dimensions.intValue()).isEqualTo(1024);
    }

    @Test
    void givenMixedVector_whenReadDistanceNatively_thenCosineDistanceMatchesHandComputation() {
        // Arrange
        Source source = persistSource(42L, 301L);
        Chunk chunk = ChunkFixtures.chunk(source, 0, null, "içerik", VectorFixtures.mix(0, 1.0, 1, 1.0));
        entityManager.persist(chunk);
        entityManager.flush();
        entityManager.clear();

        // Act
        double distance = distanceOf(chunk.getId(), VectorFixtures.unit(0));

        // Assert
        assertThat(distance).isCloseTo(1 - Math.sqrt(0.5), within(1e-6));
    }

    @Test
    void givenChunksWithRepeatedChunkNo_whenFindBySourceIdPage_thenOrderedByChunkNoThenId() {
        // Arrange
        Source source = persistSource(42L, 301L);
        Chunk lateFirst = ChunkFixtures.chunk(source, 1, "üç", "c", VectorFixtures.unit(0));
        Chunk zeroA = ChunkFixtures.chunk(source, 0, "bir", "a", VectorFixtures.unit(1));
        Chunk zeroB = ChunkFixtures.chunk(source, 0, "iki", "b", VectorFixtures.unit(2));
        entityManager.persist(lateFirst);
        entityManager.persist(zeroA);
        entityManager.persist(zeroB);
        entityManager.flush();
        entityManager.clear();

        // Act
        var page = chunkRepository.findBySourceIdOrderByChunkNoAscIdAsc(301L, PageRequest.of(0, 10));

        // Assert
        assertThat(page.getContent()).extracting(ChunkView::getHeading).containsExactly("bir", "iki", "üç");
        assertThat(page.getContent().get(0).getId()).isLessThan(page.getContent().get(1).getId());
        assertThat(page.getContent().getFirst().getEmbeddingModel()).isEqualTo("bge-m3@1024");
        assertThat(page.getContent().getFirst().getTokenCount()).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    void givenFiveChunks_whenFindBySourceIdSecondPage_thenSlicesWithoutOverlap() {
        // Arrange
        Source source = persistSource(42L, 301L);
        for (int index = 0; index < 5; index++) {
            entityManager.persist(ChunkFixtures.chunk(source, index, "h" + index, "c" + index, VectorFixtures.unit(index)));
        }
        entityManager.flush();
        entityManager.clear();

        // Act
        var page = chunkRepository.findBySourceIdOrderByChunkNoAscIdAsc(301L, PageRequest.of(1, 2));

        // Assert
        assertThat(page.getContent()).extracting(ChunkView::getChunkNo).containsExactly(2, 3);
        assertThat(page.getTotalPages()).isEqualTo(3);
    }

    @Test
    void givenTwoSources_whenDeleteBySourceId_thenOnlyThatSourceChunksDeleted() {
        // Arrange
        Source first = persistSource(42L, 301L);
        Source second = persistSource(42L, 302L);
        entityManager.persist(ChunkFixtures.chunk(first, 0, "a", "a", VectorFixtures.unit(0)));
        entityManager.persist(ChunkFixtures.chunk(first, 1, "b", "b", VectorFixtures.unit(1)));
        entityManager.persist(ChunkFixtures.chunk(second, 0, "c", "c", VectorFixtures.unit(2)));
        entityManager.flush();
        entityManager.clear();

        // Act
        int deleted = chunkRepository.deleteBySourceId(301L);
        entityManager.clear();

        // Assert
        assertThat(deleted).isEqualTo(2);
        assertThat(chunkRepository.count()).isEqualTo(1);
        assertThat(chunkRepository.findAll().getFirst().getSourceId()).isEqualTo(302L);
    }

    @Test
    void givenChunksWithTwoModelTags_whenFindEmbeddingModels_thenReturnsDistinctTagsOfWorkspaceOnly() {
        // Arrange
        Source primary = persistSource(42L, 301L);
        Source other = persistSource(19L, 302L);
        entityManager.persist(ChunkFixtures.chunk(primary, 0, "a", "a", VectorFixtures.unit(0), "bge-m3@1024"));
        entityManager.persist(ChunkFixtures.chunk(primary, 1, "b", "b", VectorFixtures.unit(1), "bge-m3@1024"));
        entityManager.persist(ChunkFixtures.chunk(primary, 2, "c", "c", VectorFixtures.unit(2), "other@1024"));
        entityManager.persist(ChunkFixtures.chunk(other, 0, "d", "d", VectorFixtures.unit(3), "third@1024"));
        entityManager.flush();
        entityManager.clear();

        // Act
        List<String> models = chunkRepository.findEmbeddingModels(42L);
        List<String> emptyWorkspace = chunkRepository.findEmbeddingModels(999L);

        // Assert
        assertThat(models).containsExactlyInAnyOrder("bge-m3@1024", "other@1024");
        assertThat(emptyWorkspace).isEmpty();
    }

    @Test
    void givenChunksInThreeWorkspaces_whenCountWorkspacesByEmbeddingModel_thenCountsDistinctWorkspacesPerTag() {
        // Arrange
        Source first = persistSource(1L, 101L);
        Source second = persistSource(2L, 102L);
        Source third = persistSource(3L, 103L);
        entityManager.persist(ChunkFixtures.chunk(first, 0, "a", "a", VectorFixtures.unit(0), "bge-m3@1024"));
        entityManager.persist(ChunkFixtures.chunk(first, 1, "b", "b", VectorFixtures.unit(1), "bge-m3@1024"));
        entityManager.persist(ChunkFixtures.chunk(second, 0, "c", "c", VectorFixtures.unit(2), "bge-m3@1024"));
        entityManager.persist(ChunkFixtures.chunk(third, 0, "d", "d", VectorFixtures.unit(3), "other@1024"));
        entityManager.flush();
        entityManager.clear();

        // Act
        List<EmbeddingModelUsage> usages = chunkRepository.countWorkspacesByEmbeddingModel();

        // Assert
        assertThat(usages).extracting(EmbeddingModelUsage::getEmbeddingModel, EmbeddingModelUsage::getWorkspaceCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("bge-m3@1024", 2L),
                        org.assertj.core.groups.Tuple.tuple("other@1024", 1L));
    }
}

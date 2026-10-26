package com.embedding.pgvector.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.embedding.pgvector.chunk.repository.ChunkRepository;
import com.embedding.pgvector.config.IngestionProperties;
import com.embedding.pgvector.embedding.client.EmbeddingProperties;
import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import com.embedding.pgvector.ingestion.chunking.ChunkDraft;
import com.embedding.pgvector.ingestion.chunking.TextChunker;
import com.embedding.pgvector.ingestion.domain.entity.IngestionErrorCode;
import com.embedding.pgvector.ingestion.domain.entity.IngestionStage;
import com.embedding.pgvector.ingestion.parsing.FileTypeDetector;
import com.embedding.pgvector.ingestion.parsing.MarkdownDocumentParser;
import com.embedding.pgvector.ingestion.parsing.SourceDocumentParser;
import com.embedding.pgvector.ingestion.parsing.TextSection;
import com.embedding.pgvector.source.domain.entity.SourceKind;
import com.embedding.pgvector.support.FakeEmbeddingClient;
import com.embedding.pgvector.support.SampleFiles;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;

/**
 * IngestionJobRunner'ın parçaları gruplar halinde embedding'e çevirmesini, ilerlemeyi kaydetmesini ve
 * hataları işe yazmasını doğrular.
 */
@ExtendWith(MockitoExtension.class)
class IngestionJobRunnerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T10:00:00Z"), ZoneOffset.UTC);
    private static final IngestionProperties PROPERTIES = new IngestionProperties(96, 1600, 2000, 200, 4, 2, 100);

    @Mock
    private IngestionJobLifecycleService lifecycleService;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private TaskExecutor executor;

    private final FakeEmbeddingClient embeddingClient = new FakeEmbeddingClient();
    private final MarkdownDocumentParser markdownParser = new MarkdownDocumentParser(new FileTypeDetector());
    private IngestionJobRunner runner;

    @BeforeEach
    void setUp() {
        runner = runnerWith(markdownParser);
    }

    private IngestionJobRunner runnerWith(SourceDocumentParser parser) {
        EmbeddingProperties embeddingProperties = new EmbeddingProperties(URI.create("http://127.0.0.1:9"),
                "bge-m3", 1024, Duration.ofSeconds(1), Duration.ofSeconds(1));
        EmbeddingModelGuard guard = new EmbeddingModelGuard(chunkRepository, embeddingProperties);
        return new IngestionJobRunner(executor, lifecycleService, List.of(parser), new TextChunker(PROPERTIES),
                embeddingClient, guard, PROPERTIES, CLOCK);
    }

    private static byte[] markdownWithSections(int count) {
        return SampleFiles.utf8(IntStream.range(0, count)
                .mapToObj(index -> "# Başlık " + index + "\nmetin " + index)
                .collect(Collectors.joining("\n")));
    }

    @Test
    void given214Chunks_whenRun_thenEmbedsInBatchesOf96AndRecordsProgressAfterEachBatch() {
        // Arrange
        byte[] content = markdownWithSections(214);

        // Act
        runner.run(7L, content, SourceKind.MARKDOWN);

        // Assert
        assertThat(embeddingClient.batchSizes()).containsExactly(96, 96, 22);
        InOrder order = inOrder(lifecycleService);
        order.verify(lifecycleService).startJob(7L);
        order.verify(lifecycleService).moveToStage(7L, IngestionStage.CHUNKING);
        order.verify(lifecycleService).recordTotalChunks(7L, 214);
        order.verify(lifecycleService).moveToStage(7L, IngestionStage.EMBEDDING);
        order.verify(lifecycleService).recordEmbeddingProgress(7L, 96);
        order.verify(lifecycleService).recordEmbeddingProgress(7L, 192);
        order.verify(lifecycleService).recordEmbeddingProgress(7L, 214);
        order.verify(lifecycleService).moveToStage(7L, IngestionStage.WRITING);
        ArgumentCaptor<List<ChunkDraft>> drafts = ArgumentCaptor.captor();
        ArgumentCaptor<List<float[]>> embeddings = ArgumentCaptor.captor();
        order.verify(lifecycleService).completeJob(eq(7L), drafts.capture(), embeddings.capture());
        assertThat(drafts.getValue()).hasSize(214);
        assertThat(embeddings.getValue()).hasSize(214);
        then(lifecycleService).should(never()).failJob(anyLong(), any());
    }

    @Test
    void givenChunks_whenRun_thenEmbedsHeadingAndContentInput() {
        // Arrange
        embeddingClient.register("Başlık 0\n\nmetin 0", new float[1024]);

        // Act
        runner.run(7L, markdownWithSections(1), SourceKind.MARKDOWN);

        // Assert
        ArgumentCaptor<List<float[]>> embeddings = ArgumentCaptor.captor();
        then(lifecycleService).should().completeJob(eq(7L), anyList(), embeddings.capture());
        assertThat(embeddings.getValue().getFirst()).isEqualTo(new float[1024]);
    }

    @Test
    void givenEmbeddingUnavailable_whenRun_thenFailsJobWithEmbeddingUnavailableAndDoesNotComplete() {
        // Arrange
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));

        // Act
        runner.run(7L, markdownWithSections(3), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.EMBEDDING_UNAVAILABLE);
        then(lifecycleService).should(never()).completeJob(anyLong(), anyList(), anyList());
    }

    @Test
    void givenEmbeddingModelMissing_whenRun_thenFailsJobWithEmbeddingModelNotFound() {
        // Arrange
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_MODEL_NOT_FOUND", "missing"));

        // Act
        runner.run(7L, markdownWithSections(3), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.EMBEDDING_MODEL_NOT_FOUND);
    }

    @Test
    void givenModelReturns768Dimensions_whenRun_thenFailsJobWithEmbeddingModelMismatch() {
        // Arrange
        embeddingClient.useDimensions(768);

        // Act
        runner.run(7L, markdownWithSections(2), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.EMBEDDING_MODEL_MISMATCH);
        then(lifecycleService).should(never()).completeJob(anyLong(), anyList(), anyList());
    }

    @Test
    void givenParserThrowsUnexpectedException_whenRun_thenFailsJobWithInternalErrorAndDoesNotRethrow() {
        // Arrange
        SourceDocumentParser exploding = new SourceDocumentParser() {
            @Override
            public SourceKind kind() {
                return SourceKind.MARKDOWN;
            }

            @Override
            public List<TextSection> parse(byte[] content) {
                throw new IllegalStateException("boom");
            }
        };
        IngestionJobRunner explodingRunner = runnerWith(exploding);

        // Act
        Throwable thrown = catchThrowable(() -> explodingRunner.run(7L, new byte[] {1}, SourceKind.MARKDOWN));

        // Assert
        assertThat(thrown).isNull();
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.INTERNAL_ERROR);
    }

    @Test
    void givenUnexpectedConflictFromLifecycle_whenRun_thenFailsJobWithInternalError() {
        // Arrange
        willThrow(new ConflictException("SOURCE_NAME_TAKEN", "x")).given(lifecycleService)
                .completeJob(anyLong(), anyList(), anyList());

        // Act
        runner.run(7L, markdownWithSections(1), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.INTERNAL_ERROR);
    }

    @Test
    void givenContentWithoutText_whenRun_thenFailsJobWithNoTextExtractedWithoutEmbedding() {
        // Act
        runner.run(7L, SampleFiles.utf8("   \n"), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.NO_TEXT_EXTRACTED);
        assertThat(embeddingClient.batchSizes()).isEmpty();
        then(lifecycleService).should(never()).recordTotalChunks(anyLong(), anyInt());
    }

    @Test
    void givenKindWithoutParser_whenRun_thenFailsJobWithInternalError() {
        // Act
        runner.run(7L, new byte[] {1}, SourceKind.CSV);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.INTERNAL_ERROR);
    }

    @Test
    void givenFailJobItselfThrows_whenRun_thenExceptionDoesNotEscape() {
        // Arrange
        embeddingClient.failWith(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "down"));
        willThrow(new IllegalStateException("db down")).given(lifecycleService).failJob(anyLong(), any());

        // Act
        Throwable thrown = catchThrowable(() -> runner.run(7L, markdownWithSections(1), SourceKind.MARKDOWN));

        // Assert
        assertThat(thrown).isNull();
    }

    @Test
    void givenExecutorRejectsTask_whenSubmit_thenFailsJobWithInternalError() {
        // Arrange
        willThrow(new TaskRejectedException("full")).given(executor).execute(any());

        // Act
        runner.submit(7L, new byte[] {1}, SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should().failJob(7L, IngestionErrorCode.INTERNAL_ERROR);
    }

    @Test
    void givenExecutorAccepts_whenSubmit_thenRunsJobOnExecutor() {
        // Arrange
        willAnswerRunImmediately();

        // Act
        runner.submit(7L, markdownWithSections(1), SourceKind.MARKDOWN);

        // Assert
        then(lifecycleService).should(times(1)).completeJob(eq(7L), anyList(), anyList());
    }

    private void willAnswerRunImmediately() {
        org.mockito.BDDMockito.willAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).given(executor).execute(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"1,1", "95,95", "96,96", "97,96;1", "192,96;96", "193,96;96;1"})
    void givenChunkCountAroundBatchBoundary_whenRun_thenBatchesAreFullThenRemainder(int chunkCount, String expected) {
        // Arrange
        List<Integer> expectedSizes = java.util.Arrays.stream(expected.split(";")).map(Integer::parseInt).toList();

        // Act
        runner.run(7L, markdownWithSections(chunkCount), SourceKind.MARKDOWN);

        // Assert
        assertThat(embeddingClient.batchSizes()).isEqualTo(expectedSizes);
        then(lifecycleService).should().recordEmbeddingProgress(7L, chunkCount);
    }
}

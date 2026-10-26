package com.embedding.pgvector.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.embedding.pgvector.config.SearchProperties;
import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.client.EmbeddingResult;
import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.search.domain.dto.SearchChunksRequest;
import com.embedding.pgvector.search.domain.dto.SearchResultResponse;
import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.MatchedTermField;
import com.embedding.pgvector.search.domain.entity.RetrievalResult;
import com.embedding.pgvector.search.domain.entity.RetrievedHit;
import com.embedding.pgvector.search.domain.entity.SearchBranch;
import com.embedding.pgvector.search.domain.entity.SearchEvent;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import com.embedding.pgvector.search.repository.ChunkSearchRepository;
import com.embedding.pgvector.search.repository.SearchEventRepository;
import com.embedding.pgvector.search.repository.WordLexemeView;
import com.embedding.pgvector.support.SteppingClock;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.time.Clock;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

/**
 * SearchService'in varsayılan ayarları, arama modlarını, süre ölçümünü ve model uyuşmazlığı hatalarını
 * sahte bağımlılıklarla doğrular.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class SearchServiceTest {

    private static final float[] QUERY_VECTOR = {0.5f, 0.25f};
    private static final SearchProperties DEFAULTS = new SearchProperties(SearchMode.HYBRID,
            FilterStrategy.ITERATIVE_SCAN, 40, 10, 60, 20000, true, java.time.ZoneId.of("Europe/Istanbul"), 5, 0.49);

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private EmbeddingModelGuard embeddingModelGuard;

    @Mock
    private ChunkRetrievalService chunkRetrievalService;

    @Mock
    private ChunkSearchRepository chunkSearchRepository;

    @Mock
    private SearchEventRepository searchEventRepository;

    private SearchService searchService(SearchProperties properties, Clock clock) {
        return new SearchService(workspaceService, embeddingClient, embeddingModelGuard, chunkRetrievalService,
                chunkSearchRepository, new KeywordHighlighter(), searchEventRepository, properties, clock);
    }

    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = searchService(DEFAULTS, new SteppingClock(10));
    }

    private static RetrievedHit hit(long chunkId, String heading, String content, Integer keywordRank,
            Integer vectorRank) {
        return new RetrievedHit(chunkId, 1, 301L, "İzin notları", heading, content, keywordRank,
                keywordRank == null ? null : 0.5, vectorRank, vectorRank == null ? null : 0.25, null);
    }

    private static RetrievalResult vectorResult(int count) {
        List<RetrievedHit> hits = IntStream.rangeClosed(1, count)
                .mapToObj(rank -> hit(rank, "Başlık " + rank, "içerik " + rank, null, rank)).toList();
        return new RetrievalResult(hits, null, count);
    }

    @Test
    void givenNonDefaultSearchProperties_whenSearchWithOnlyQuery_thenUsesConfiguredDefaults() {
        // Arrange
        SearchProperties configured = new SearchProperties(SearchMode.VECTOR, FilterStrategy.POST_FILTER, 64, 7, 60,
                 20000, true, java.time.ZoneId.of("Europe/Istanbul"), 5, 0.49);
        SearchService service = searchService(configured, new SteppingClock(10));
        given(embeddingClient.embed("izin")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 7, 64, FilterStrategy.POST_FILTER))
                .willReturn(vectorResult(7));

        // Act
        SearchResultResponse response = service.searchChunks(42L,
                new SearchChunksRequest("izin", null, null, null, null, null, null));

        // Assert
        assertThat(response.mode()).isEqualTo(SearchMode.VECTOR);
        assertThat(response.efSearch()).isEqualTo(64);
        assertThat(response.limit()).isEqualTo(7);
        assertThat(response.filterStrategy()).isEqualTo(FilterStrategy.POST_FILTER);
        assertThat(response.returnedCount()).isEqualTo(7);
        assertThat(response.vectorShort()).isFalse();
    }

    @Test
    void givenExplicitRequestValues_whenSearch_thenOverrideDefaultsCaseInsensitively() {
        // Arrange
        given(embeddingClient.embed("izin")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveHybrid(42L, "izin", QUERY_VECTOR, 5, 100, FilterStrategy.POST_FILTER))
                .willReturn(new RetrievalResult(List.of(), 0, 0));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("izin", "hybrid", "post_filter", 100, 5, null, null));

        // Assert
        assertThat(response.mode()).isEqualTo(SearchMode.HYBRID);
        assertThat(response.filterStrategy()).isEqualTo(FilterStrategy.POST_FILTER);
        assertThat(response.efSearch()).isEqualTo(100);
        assertThat(response.limit()).isEqualTo(5);
    }

    @Test
    void givenKeywordMode_whenSearch_thenDoesNotCallEmbeddingClient() {
        // Arrange
        given(chunkRetrievalService.retrieveKeyword(42L, "IZN-99", 10))
                .willReturn(new RetrievalResult(List.of(), 0, null));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("IZN-99", "KEYWORD", "POST_FILTER", 500, null, null, true));

        // Assert
        then(embeddingClient).shouldHaveNoInteractions();
        then(embeddingModelGuard).should(never()).ensureWorkspaceCompatible(anyLong());
        assertThat(response.timings().embeddingMs()).isNull();
        assertThat(response.filterStrategy()).isNull();
        assertThat(response.efSearch()).isNull();
        assertThat(response.vectorCount()).isNull();
        assertThat(response.vectorShort()).isFalse();
        assertThat(response.vectorPlan()).isNull();
        then(chunkRetrievalService).should(never()).explainVector(anyLong(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void givenKeywordMode_whenSearch_thenTimingsFollowClockCallsOfEachPhase() {
        // Arrange
        given(chunkRetrievalService.retrieveKeyword(42L, "x", 10)).willReturn(new RetrievalResult(List.of(), 0, null));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "KEYWORD", null, null, null, null, null));

        // Assert
        assertThat(response.timings().sqlMs()).isEqualTo(10);
        assertThat(response.timings().totalMs()).isEqualTo(30);
    }

    @Test
    void givenVectorMode_whenSearch_thenEmbeddingTimeIsSeparateFromSqlTime() {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn(vectorResult(10));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "VECTOR", null, null, null, null, null));

        // Assert
        assertThat(response.timings().embeddingMs()).isEqualTo(10);
        assertThat(response.timings().sqlMs()).isEqualTo(10);
        assertThat(response.timings().totalMs()).isEqualTo(50);
    }

    @Test
    void givenWorkspaceChunksWithOtherModelTag_whenSearchVectorOrHybrid_thenThrowsEmbeddingModelMismatchWithoutEmbedding() {
        // Arrange
        willThrow(new ConflictException("EMBEDDING_MODEL_MISMATCH", "other")).given(embeddingModelGuard)
                .ensureWorkspaceCompatible(42L);

        // Act
        Throwable vector = catchThrowable(() -> searchService.searchChunks(42L,
                new SearchChunksRequest("x", "VECTOR", null, null, null, null, null)));
        Throwable hybrid = catchThrowable(() -> searchService.searchChunks(42L,
                new SearchChunksRequest("x", "HYBRID", null, null, null, null, null)));

        // Assert
        assertThat(vector).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("EMBEDDING_MODEL_MISMATCH");
        assertThat(hybrid).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("EMBEDDING_MODEL_MISMATCH");
        then(embeddingClient).shouldHaveNoInteractions();
        then(searchEventRepository).shouldHaveNoInteractions();
    }

    @Test
    void givenWorkspaceChunksWithOtherModelTag_whenSearchKeyword_thenReturnsResult() {
        // Arrange
        org.mockito.Mockito.lenient().doThrow(new ConflictException("EMBEDDING_MODEL_MISMATCH", "other"))
                .when(embeddingModelGuard).ensureWorkspaceCompatible(42L);
        given(chunkRetrievalService.retrieveKeyword(42L, "x", 10)).willReturn(
                new RetrievalResult(List.of(hit(9L, "Başlık", "içerik", 1, null)), 1, null));
        given(chunkSearchRepository.findQueryLexemes("x")).willReturn(List.of("x"));
        given(chunkSearchRepository.findLexemes(any())).willReturn(List.of());

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "KEYWORD", null, null, null, null, null));

        // Assert
        assertThat(response.returnedCount()).isEqualTo(1);
        assertThat(response.hits().getFirst().chunkId()).isEqualTo(9L);
    }

    @Test
    void givenUnknownWorkspace_whenSearch_thenThrowsWorkspaceNotFoundBeforeAnyWork() {
        // Arrange
        willThrow(new NotFoundException("WORKSPACE_NOT_FOUND", "x")).given(workspaceService)
                .ensureWorkspaceExists(999L);

        // Act
        Throwable thrown = catchThrowable(() -> searchService.searchChunks(999L,
                new SearchChunksRequest("x", null, null, null, null, null, null)));

        // Assert
        assertThat(thrown).isInstanceOf(NotFoundException.class)
                .extracting("errorCode").isEqualTo("WORKSPACE_NOT_FOUND");
        then(embeddingClient).shouldHaveNoInteractions();
        then(searchEventRepository).shouldHaveNoInteractions();
    }

    @Test
    void givenQueryEmbeddingWithWrongDimensions_whenSearchVector_thenThrowsEmbeddingModelMismatch() {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        willThrow(new ConflictException("EMBEDDING_MODEL_MISMATCH", "dims")).given(embeddingModelGuard)
                .ensureDimensions(QUERY_VECTOR);

        // Act
        Throwable thrown = catchThrowable(() -> searchService.searchChunks(42L,
                new SearchChunksRequest("x", "VECTOR", null, null, null, null, null)));

        // Assert
        assertThat(thrown).isInstanceOf(ConflictException.class)
                .extracting("errorCode").isEqualTo("EMBEDDING_MODEL_MISMATCH");
        then(chunkRetrievalService).should(never()).retrieveVector(anyLong(), any(), anyInt(), anyInt(), any());
    }

    @Test
    void givenSearchEventSaveFails_whenSearch_thenReturnsResultAndLogsError(CapturedOutput output) {
        // Arrange
        given(chunkRetrievalService.retrieveKeyword(42L, "x", 10)).willReturn(new RetrievalResult(List.of(), 0, null));
        willThrow(new DataAccessResourceFailureException("down")).given(searchEventRepository)
                .save(any(SearchEvent.class));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "KEYWORD", null, null, null, null, null));

        // Assert
        assertThat(response.returnedCount()).isZero();
        assertThat(output.getOut()).contains("Search event could not be saved workspaceId=42");
    }

    @Test
    void givenVectorBranchReturnsFewerThanLimit_whenSearch_thenMarksShortAndLogsWarn(CapturedOutput output) {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.POST_FILTER))
                .willReturn(vectorResult(3));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "VECTOR", "POST_FILTER", null, null, null, null));

        // Assert
        assertThat(response.vectorShort()).isTrue();
        assertThat(response.vectorCount()).isEqualTo(3);
        assertThat(output.getOut()).contains(
                "Short vector branch workspaceId=42 requested=10 returned=3 efSearch=40 filterStrategy=POST_FILTER");
    }

    @Test
    void givenVectorBranchReturnsExactlyLimit_whenSearch_thenNotShortAndNoWarn(CapturedOutput output) {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn(vectorResult(10));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("x", "VECTOR", null, null, null, null, null));

        // Assert
        assertThat(response.vectorShort()).isFalse();
        assertThat(output.getOut()).doesNotContain("Short vector branch");
    }

    @Test
    void givenNoHits_whenSearch_thenLogsWarnAndSavesEventWithZeroResults(CapturedOutput output) {
        // Arrange
        given(chunkRetrievalService.retrieveKeyword(42L, "netflix", 10))
                .willReturn(new RetrievalResult(List.of(), 0, null));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("netflix", "KEYWORD", null, null, null, "Web", null));

        // Assert
        assertThat(output.getOut()).contains("Search returned no results workspaceId=42 mode=KEYWORD");
        ArgumentCaptor<SearchEvent> event = ArgumentCaptor.forClass(SearchEvent.class);
        then(searchEventRepository).should().save(event.capture());
        assertThat(event.getValue().getResultCount()).isZero();
        assertThat(event.getValue().getTopChunkId()).isNull();
        assertThat(event.getValue().getTopChunkHeading()).isNull();
        assertThat(event.getValue().getChannel()).isEqualTo("Web");
        assertThat(event.getValue().getQuery()).isEqualTo("netflix");
        assertThat(event.getValue().getMode()).isEqualTo(SearchMode.KEYWORD);
        assertThat(event.getValue().getWorkspaceId()).isEqualTo(42L);
    }

    @Test
    void givenHits_whenSearch_thenSavesEventWithTopHitAndFilterSettings() {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn(vectorResult(2));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("x", "VECTOR", null, null, null, null, null));

        // Assert
        ArgumentCaptor<SearchEvent> event = ArgumentCaptor.forClass(SearchEvent.class);
        then(searchEventRepository).should().save(event.capture());
        assertThat(event.getValue().getResultCount()).isEqualTo(2);
        assertThat(event.getValue().getTopChunkId()).isEqualTo(1L);
        assertThat(event.getValue().getTopChunkHeading()).isEqualTo("Başlık 1");
        assertThat(event.getValue().getFilterStrategy()).isEqualTo(FilterStrategy.ITERATIVE_SCAN);
        assertThat(event.getValue().getEfSearch()).isEqualTo(40);
        assertThat(event.getValue().getTotalMs()).isEqualTo(50);
    }

    @Test
    void givenHybridHitsWithDistances_whenSearchChunks_thenEventStoresKeywordCountAndSmallestDistance() {
        // Arrange
        given(embeddingClient.embed("Yıllık izin kaç gün?")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        List<RetrievedHit> hits = List.of(
                new RetrievedHit(1L, 1, 10001L, "İzin notları", "a", "içerik a", null, null, 1, 0.40, null),
                new RetrievedHit(2L, 2, 10001L, "İzin notları", "b", "içerik b", null, null, 2, 0.31, null),
                new RetrievedHit(3L, 3, 10001L, "İzin notları", "c", "içerik c", 1, 0.5, null, null, null));
        given(chunkRetrievalService.retrieveHybrid(42L, "Yıllık izin kaç gün?", QUERY_VECTOR, 10, 40,
                FilterStrategy.ITERATIVE_SCAN)).willReturn(new RetrievalResult(hits, 0, 2));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("Yıllık izin kaç gün?", "HYBRID", null, null, null,
                null, null));

        // Assert
        ArgumentCaptor<SearchEvent> event = ArgumentCaptor.forClass(SearchEvent.class);
        then(searchEventRepository).should().save(event.capture());
        assertThat(event.getValue().getKeywordCount()).isZero();
        assertThat(event.getValue().getBestDistance()).isCloseTo(0.31, within(1e-9));
        assertThat(event.getValue().getQueryKey()).isEqualTo("yillik izin kaç gün?");
    }

    @Test
    void givenKeywordMode_whenSearchChunks_thenEventStoresNullDistance() {
        // Arrange
        given(chunkRetrievalService.retrieveKeyword(42L, "izin", 10)).willReturn(new RetrievalResult(
                List.of(hit(1, "a", "izin içerik", 1, null)), 3, null));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("izin", "KEYWORD", null, null, null, null, null));

        // Assert
        ArgumentCaptor<SearchEvent> event = ArgumentCaptor.forClass(SearchEvent.class);
        then(searchEventRepository).should().save(event.capture());
        assertThat(event.getValue().getKeywordCount()).isEqualTo(3);
        assertThat(event.getValue().getBestDistance()).isNull();
    }

    @Test
    void givenVectorModeWithoutHits_whenSearchChunks_thenEventStoresNullKeywordCountAndDistance() {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn(new RetrievalResult(List.of(), null, 0));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("x", "VECTOR", null, null, null, null, null));

        // Assert
        ArgumentCaptor<SearchEvent> event = ArgumentCaptor.forClass(SearchEvent.class);
        then(searchEventRepository).should().save(event.capture());
        assertThat(event.getValue().getKeywordCount()).isNull();
        assertThat(event.getValue().getBestDistance()).isNull();
    }

    @Test
    void givenPopularQueryRows_whenListPopularQueries_thenChecksWorkspaceUsesConfiguredLimitsAndMapsRows() {
        // Arrange
        com.embedding.pgvector.search.repository.PopularQueryView row =
                org.mockito.Mockito.mock(com.embedding.pgvector.search.repository.PopularQueryView.class);
        given(row.getQuery()).willReturn("Yıllık izin kaç gün?");
        given(row.getSearchCount()).willReturn(2L);
        given(row.getLastSearchedAt()).willReturn(java.time.Instant.parse("2026-10-25T09:20:11Z"));
        given(searchEventRepository.findPopularQueries(42L, 0.49, 5)).willReturn(List.of(row));

        // Act
        com.embedding.pgvector.search.domain.dto.PopularQueriesResponse response =
                searchService.listPopularQueries(42L);

        // Assert
        then(workspaceService).should().ensureWorkspaceExists(42L);
        assertThat(response.queries()).singleElement().satisfies(item -> {
            assertThat(item.query()).isEqualTo("Yıllık izin kaç gün?");
            assertThat(item.searchCount()).isEqualTo(2L);
            assertThat(item.lastSearchedAt()).isEqualTo(java.time.Instant.parse("2026-10-25T09:20:11Z"));
        });
    }

    @Test
    void givenTurkishQuery_whenSearch_thenSummaryLogHasCountsAndSettingsButNoQueryText(CapturedOutput output) {
        // Arrange
        String query = "Parolamı unuttum, online işlemlere giremiyorum";
        given(embeddingClient.embed(query)).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveHybrid(42L, query, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn(new RetrievalResult(
                        IntStream.rangeClosed(1, 10).mapToObj(rank -> hit(rank, "Gizli başlık " + rank,
                                "gizli-icerik-" + rank, null, rank)).toList(), 0, 10));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest(query, null, null, null, null, null, null));

        // Assert
        assertThat(output.getOut()).contains("Search completed workspaceId=42 mode=HYBRID keywordCount=0 vectorCount=10 "
                + "returned=10/10 efSearch=40 filterStrategy=ITERATIVE_SCAN").contains("queryLength=46");
        assertThat(output.getAll()).doesNotContain("Parolamı").doesNotContain("gizli-icerik").doesNotContain("Gizli");
    }

    @Test
    void givenKeywordHitsAndExplainFlag_whenSearchHybrid_thenHighlightsFromLexemesAndAddsPlan() {
        // Arrange
        given(embeddingClient.embed("IZN-99 hata kodu")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        RetrievedHit keywordHit = new RetrievedHit(5L, 1, 301L, "İzin notları", "Hata kodları",
                "IZN-99 hata kodu nedir", 1, 0.9, 2, 0.3, 0.0325);
        RetrievedHit vectorOnly = new RetrievedHit(6L, 2, 301L, "İzin notları", "Başka", "alakasız metin", null, null,
                1, 0.2, 0.0164);
        given(chunkRetrievalService.retrieveHybrid(42L, "IZN-99 hata kodu", QUERY_VECTOR, 10, 40,
                FilterStrategy.ITERATIVE_SCAN)).willReturn(new RetrievalResult(List.of(keywordHit, vectorOnly), 1, 2));
        given(chunkRetrievalService.explainVector(42L, QUERY_VECTOR, 10, 40, FilterStrategy.ITERATIVE_SCAN))
                .willReturn("Index Scan");
        given(chunkSearchRepository.findQueryLexemes("IZN-99 hata kodu")).willReturn(List.of("izn-99", "hata", "kod"));
        given(chunkSearchRepository.findLexemes("Hata kodları IZN-99 hata kodu nedir")).willReturn(List.of(
                word("Hata", "hata"), word("kodları", "kod"), word("IZN-99", "izn-99"), word("hata", "hata"),
                word("kodu", "kod"), word("nedir", "nedir")));

        // Act
        SearchResultResponse response = searchService.searchChunks(42L,
                new SearchChunksRequest("IZN-99 hata kodu", "HYBRID", null, null, null, null, true));

        // Assert
        assertThat(response.vectorPlan()).isEqualTo("Index Scan");
        assertThat(response.hits().getFirst().rank()).isEqualTo(1);
        assertThat(response.hits().getFirst().matchedBy()).containsExactly(SearchBranch.KEYWORD, SearchBranch.SEMANTIC);
        assertThat(response.hits().getFirst().matchedTerms()).extracting("field", "start", "end").contains(
                org.assertj.core.groups.Tuple.tuple(MatchedTermField.CONTENT, 0, 6),
                org.assertj.core.groups.Tuple.tuple(MatchedTermField.CONTENT, 7, 11),
                org.assertj.core.groups.Tuple.tuple(MatchedTermField.CONTENT, 12, 16),
                org.assertj.core.groups.Tuple.tuple(MatchedTermField.HEADING, 0, 4));
        assertThat(response.hits().get(1).rank()).isEqualTo(2);
        assertThat(response.hits().get(1).matchedBy()).containsExactly(SearchBranch.SEMANTIC);
        assertThat(response.hits().get(1).matchedTerms()).isEmpty();
        assertThat(response.keywordCount()).isEqualTo(1);
    }

    @Test
    void givenVectorOnlyHits_whenSearch_thenLexemeQueriesAreSkipped() {
        // Arrange
        given(embeddingClient.embed("x")).willReturn(new EmbeddingResult("bge-m3", QUERY_VECTOR));
        given(chunkRetrievalService.retrieveVector(eq(42L), any(), anyInt(), anyInt(), any()))
                .willReturn(vectorResult(2));

        // Act
        searchService.searchChunks(42L, new SearchChunksRequest("x", "VECTOR", null, null, null, null, null));

        // Assert
        then(chunkSearchRepository).shouldHaveNoInteractions();
    }

    @Test
    void givenPageOfEvents_whenListSearchEvents_thenChecksWorkspaceAndMapsEvents() {
        // Arrange
        SearchEvent event = SearchEvent.record(42L, java.time.Instant.parse("2026-10-25T10:00:00Z"), "Web", "q",
                SearchMode.KEYWORD, null, null, 10, 0, null, null, 3L, null, null);
        org.springframework.test.util.ReflectionTestUtils.setField(event, "id", 77L);
        given(searchEventRepository.findByWorkspaceIdOrderByCreatedAtDescIdDesc(42L,
                org.springframework.data.domain.PageRequest.of(1, 2)))
                .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(event),
                        org.springframework.data.domain.PageRequest.of(1, 2), 3));

        // Act
        var page = searchService.listSearchEvents(42L, 1, 2);

        // Assert
        then(workspaceService).should().ensureWorkspaceExists(42L);
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().query()).isEqualTo("q");
    }

    @Test
    void givenClockAtIstanbulLateEvening_whenGetSearchEventStats_thenTodayStartsAtPreviousUtcEvening() {
        // Arrange
        Clock clock = Clock.fixed(java.time.Instant.parse("2026-10-24T20:59:59Z"), java.time.ZoneOffset.UTC);
        SearchService service = searchService(DEFAULTS, clock);
        given(searchEventRepository.countStats(eq(42L), any(), any())).willReturn(statsView(0, 0, 0, 0, 0));
        ArgumentCaptor<java.time.Instant> todayStart = ArgumentCaptor.forClass(java.time.Instant.class);
        ArgumentCaptor<java.time.Instant> last7DaysStart = ArgumentCaptor.forClass(java.time.Instant.class);

        // Act
        var response = service.getSearchEventStats(42L);

        // Assert
        then(searchEventRepository).should().countStats(eq(42L), todayStart.capture(), last7DaysStart.capture());
        assertThat(todayStart.getValue()).isEqualTo(java.time.Instant.parse("2026-10-23T21:00:00Z"));
        assertThat(last7DaysStart.getValue()).isEqualTo(java.time.Instant.parse("2026-10-17T21:00:00Z"));
        assertThat(response.todayStart()).isEqualTo(todayStart.getValue());
        assertThat(response.timeZone()).isEqualTo("Europe/Istanbul");
    }

    @Test
    void givenUnknownWorkspace_whenGetSearchEventStats_thenThrowsWorkspaceNotFoundWithoutQuery() {
        // Arrange
        willThrow(new NotFoundException("WORKSPACE_NOT_FOUND", "x")).given(workspaceService)
                .ensureWorkspaceExists(999L);

        // Act
        Throwable thrown = catchThrowable(() -> searchService.getSearchEventStats(999L));

        // Assert
        assertThat(thrown).isInstanceOf(NotFoundException.class).extracting("errorCode")
                .isEqualTo("WORKSPACE_NOT_FOUND");
        then(searchEventRepository).should(never()).countStats(anyLong(), any(), any());
    }

    @Test
    void givenSevenEventsInWindow_whenGetSearchEventStats_thenRatesAreCountsOverWindowAndNullWhenEmpty() {
        // Arrange
        given(searchEventRepository.countStats(eq(42L), any(), any())).willReturn(statsView(9, 2, 4, 1, 2));
        given(searchEventRepository.countStats(eq(19L), any(), any())).willReturn(statsView(5, 0, 0, 0, 0));

        // Act
        var busy = searchService.getSearchEventStats(42L);
        var empty = searchService.getSearchEventStats(19L);

        // Assert
        assertThat(busy.zeroResultRate()).isCloseTo(0.25, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(busy.lowResultRate()).isCloseTo(0.5, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(empty.zeroResultRate()).isNull();
        assertThat(empty.lowResultRate()).isNull();
        assertThat(empty.totalCount()).isEqualTo(5);
    }

    private static com.embedding.pgvector.search.repository.SearchEventStatsView statsView(long total, long today,
            long last7Days, long zero, long low) {
        return new com.embedding.pgvector.search.repository.SearchEventStatsView() {
            @Override
            public long getTotalCount() {
                return total;
            }

            @Override
            public long getTodayCount() {
                return today;
            }

            @Override
            public long getLast7DaysCount() {
                return last7Days;
            }

            @Override
            public long getZeroResultCount() {
                return zero;
            }

            @Override
            public long getLowResultCount() {
                return low;
            }
        };
    }

    private static WordLexemeView word(String word, String lexeme) {
        return new WordLexemeView() {
            @Override
            public String getWord() {
                return word;
            }

            @Override
            public String getLexeme() {
                return lexeme;
            }
        };
    }
}

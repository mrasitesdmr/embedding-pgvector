package com.embedding.pgvector.search.service;

import com.embedding.pgvector.config.SearchProperties;
import com.embedding.pgvector.embedding.client.EmbeddingClient;
import com.embedding.pgvector.embedding.client.EmbeddingResult;
import com.embedding.pgvector.embedding.service.EmbeddingModelGuard;
import com.embedding.pgvector.search.domain.dto.MatchedTermResponse;
import com.embedding.pgvector.search.domain.dto.PopularQueriesResponse;
import com.embedding.pgvector.search.domain.dto.PopularQueryResponse;
import com.embedding.pgvector.search.domain.dto.SearchChunksRequest;
import com.embedding.pgvector.search.domain.dto.SearchEventResponse;
import com.embedding.pgvector.search.domain.dto.SearchEventStatsResponse;
import com.embedding.pgvector.search.domain.dto.SearchHitResponse;
import com.embedding.pgvector.search.domain.dto.SearchResultResponse;
import com.embedding.pgvector.search.domain.dto.SearchTimingsResponse;
import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.RetrievalResult;
import com.embedding.pgvector.search.domain.entity.RetrievedHit;
import com.embedding.pgvector.search.domain.entity.SearchBranch;
import com.embedding.pgvector.search.domain.entity.SearchEvent;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import com.embedding.pgvector.search.repository.ChunkSearchRepository;
import com.embedding.pgvector.search.repository.WordLexemeView;
import com.embedding.pgvector.search.repository.SearchEventRepository;
import com.embedding.pgvector.workspace.service.WorkspaceService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Bir çalışma alanında parça arama akışını yürütür ve her aramayı kaydeder; arama kayıtlarını, istatistiklerini
 * ve sık aranan aramaları da getirir.
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int STATS_WINDOW_DAYS = 7;

    private final WorkspaceService workspaceService;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingModelGuard embeddingModelGuard;
    private final ChunkRetrievalService chunkRetrievalService;
    private final ChunkSearchRepository chunkSearchRepository;
    private final KeywordHighlighter keywordHighlighter;
    private final SearchEventRepository searchEventRepository;
    private final SearchProperties properties;
    private final Clock clock;

    public SearchService(WorkspaceService workspaceService, EmbeddingClient embeddingClient,
            EmbeddingModelGuard embeddingModelGuard, ChunkRetrievalService chunkRetrievalService,
            ChunkSearchRepository chunkSearchRepository, KeywordHighlighter keywordHighlighter,
            SearchEventRepository searchEventRepository, SearchProperties properties, Clock clock) {
        this.workspaceService = workspaceService;
        this.embeddingClient = embeddingClient;
        this.embeddingModelGuard = embeddingModelGuard;
        this.chunkRetrievalService = chunkRetrievalService;
        this.chunkSearchRepository = chunkSearchRepository;
        this.keywordHighlighter = keywordHighlighter;
        this.searchEventRepository = searchEventRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public SearchResultResponse searchChunks(long workspaceId, SearchChunksRequest request) {
        long startedAt = clock.millis();
        String query = Objects.requireNonNull(request.query());
        workspaceService.ensureWorkspaceExists(workspaceId);
        SearchMode mode = request.mode() == null ? properties.defaultMode() : SearchMode.fromInput(request.mode());
        int limit = Objects.requireNonNullElse(request.limit(), properties.defaultLimit());
        @Nullable FilterStrategy filterStrategy = mode.usesVector() ? filterStrategy(request) : null;
        @Nullable Integer efSearch = mode.usesVector()
                ? Objects.requireNonNullElse(request.efSearch(), properties.defaultEfSearch())
                : null;

        float @Nullable [] queryEmbedding = null;
        @Nullable Long embeddingMs = null;
        if (mode.usesVector()) {
            embeddingModelGuard.ensureWorkspaceCompatible(workspaceId);
            long embeddingStartedAt = clock.millis();
            EmbeddingResult embeddingResult = embeddingClient.embed(query);
            embeddingMs = clock.millis() - embeddingStartedAt;
            embeddingModelGuard.ensureDimensions(embeddingResult.embedding());
            queryEmbedding = embeddingResult.embedding();
        }

        long sqlStartedAt = clock.millis();
        RetrievalResult result = retrieve(workspaceId, mode, query, queryEmbedding, limit, efSearch, filterStrategy);
        long sqlMs = clock.millis() - sqlStartedAt;

        List<SearchHitResponse> hits = toHits(query, result.hits());
        @Nullable String vectorPlan = null;
        if (queryEmbedding != null && Boolean.TRUE.equals(request.explain())) {
            vectorPlan = chunkRetrievalService.explainVector(workspaceId, queryEmbedding, limit,
                    Objects.requireNonNull(efSearch), Objects.requireNonNull(filterStrategy));
        }
        boolean vectorShort = result.vectorCount() != null && result.vectorCount() < limit;
        long totalMs = clock.millis() - startedAt;
        SearchResultResponse response = new SearchResultResponse(query, mode, filterStrategy, efSearch, limit,
                hits.size(), result.keywordCount(), result.vectorCount(), vectorShort,
                new SearchTimingsResponse(embeddingMs, sqlMs, totalMs), hits, vectorPlan);

        logSearch(workspaceId, response);
        saveSearchEvent(workspaceId, request.channel(), response);
        return response;
    }

    public Page<SearchEventResponse> listSearchEvents(long workspaceId, int page, int size) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        return searchEventRepository
                .findByWorkspaceIdOrderByCreatedAtDescIdDesc(workspaceId, PageRequest.of(page, size))
                .map(SearchEventResponse::from);
    }

    public SearchEventStatsResponse getSearchEventStats(long workspaceId) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        ZoneId zone = properties.statsTimeZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Instant todayStart = today.atStartOfDay(zone).toInstant();
        Instant last7DaysStart = today.minusDays(STATS_WINDOW_DAYS - 1).atStartOfDay(zone).toInstant();
        return SearchEventStatsResponse.from(
                searchEventRepository.countStats(workspaceId, todayStart, last7DaysStart), zone, todayStart,
                last7DaysStart);
    }

    public PopularQueriesResponse listPopularQueries(long workspaceId) {
        workspaceService.ensureWorkspaceExists(workspaceId);
        return new PopularQueriesResponse(searchEventRepository
                .findPopularQueries(workspaceId, properties.popularMaxDistance(), properties.popularQueryLimit())
                .stream()
                .map(PopularQueryResponse::from)
                .toList());
    }

    private FilterStrategy filterStrategy(SearchChunksRequest request) {
        return request.filterStrategy() == null
                ? properties.defaultFilterStrategy()
                : FilterStrategy.fromInput(request.filterStrategy());
    }

    private RetrievalResult retrieve(long workspaceId, SearchMode mode, String query, float @Nullable [] queryEmbedding,
            int limit, @Nullable Integer efSearch, @Nullable FilterStrategy filterStrategy) {
        return switch (mode) {
            case KEYWORD -> chunkRetrievalService.retrieveKeyword(workspaceId, query, limit);
            case VECTOR -> chunkRetrievalService.retrieveVector(workspaceId, Objects.requireNonNull(queryEmbedding),
                    limit, Objects.requireNonNull(efSearch), Objects.requireNonNull(filterStrategy));
            case HYBRID -> chunkRetrievalService.retrieveHybrid(workspaceId, query,
                    Objects.requireNonNull(queryEmbedding), limit, Objects.requireNonNull(efSearch),
                    Objects.requireNonNull(filterStrategy));
        };
    }

    private List<SearchHitResponse> toHits(String query, List<RetrievedHit> retrievedHits) {
        List<RetrievedHit> keywordHits = retrievedHits.stream().filter(hit -> hit.keywordRank() != null).toList();
        Set<String> queryLexemes = Set.of();
        Map<String, Set<String>> wordLexemes = Map.of();
        if (!keywordHits.isEmpty()) {
            queryLexemes = new HashSet<>(chunkSearchRepository.findQueryLexemes(query));
            Set<String> words = new LinkedHashSet<>();
            keywordHits.forEach(hit -> words.addAll(keywordHighlighter.words(hit.heading(), hit.content())));
            wordLexemes = lexemesByWord(chunkSearchRepository.findLexemes(String.join(" ", words)));
        }
        List<SearchHitResponse> hits = new ArrayList<>(retrievedHits.size());
        for (RetrievedHit hit : retrievedHits) {
            List<MatchedTermResponse> matchedTerms = hit.keywordRank() == null
                    ? List.of()
                    : keywordHighlighter.highlight(queryLexemes, wordLexemes, hit.heading(), hit.content());
            hits.add(new SearchHitResponse(hits.size() + 1, hit.chunkId(), hit.chunkNo(), hit.sourceId(),
                    hit.sourceName(), hit.heading(), hit.content(), matchedBy(hit), hit.keywordRank(),
                    hit.keywordScore(), hit.vectorRank(), hit.distance(), hit.rrfScore(), matchedTerms));
        }
        return hits;
    }

    private static Map<String, Set<String>> lexemesByWord(List<WordLexemeView> rows) {
        Map<String, Set<String>> lexemes = new HashMap<>();
        rows.forEach(row -> lexemes.computeIfAbsent(row.getWord(), word -> new HashSet<>()).add(row.getLexeme()));
        return lexemes;
    }

    private static List<SearchBranch> matchedBy(RetrievedHit hit) {
        List<SearchBranch> branches = new ArrayList<>(2);
        if (hit.keywordRank() != null) {
            branches.add(SearchBranch.KEYWORD);
        }
        if (hit.vectorRank() != null) {
            branches.add(SearchBranch.SEMANTIC);
        }
        return branches;
    }

    private static void logSearch(long workspaceId, SearchResultResponse response) {
        log.info("Search completed workspaceId={} mode={} keywordCount={} vectorCount={} returned={}/{} efSearch={} "
                        + "filterStrategy={} embeddingMs={} sqlMs={} totalMs={} queryLength={}",
                workspaceId, response.mode(), response.keywordCount(), response.vectorCount(),
                response.returnedCount(), response.limit(), response.efSearch(), response.filterStrategy(),
                response.timings().embeddingMs(), response.timings().sqlMs(), response.timings().totalMs(),
                response.query().length());
        if (response.vectorShort()) {
            log.warn("Short vector branch workspaceId={} requested={} returned={} efSearch={} filterStrategy={}",
                    workspaceId, response.limit(), response.vectorCount(), response.efSearch(),
                    response.filterStrategy());
        }
        if (response.returnedCount() == 0) {
            log.warn("Search returned no results workspaceId={} mode={}", workspaceId, response.mode());
        }
    }

    private void saveSearchEvent(long workspaceId, @Nullable String channel, SearchResultResponse response) {
        @Nullable SearchHitResponse topHit = response.hits().isEmpty() ? null : response.hits().getFirst();
        try {
            searchEventRepository.save(SearchEvent.record(workspaceId, Instant.now(clock), channel,
                    response.query(), response.mode(), response.filterStrategy(), response.efSearch(),
                    response.limit(), response.returnedCount(), topHit == null ? null : topHit.chunkId(),
                    topHit == null ? null : topHit.heading(), response.timings().totalMs(), response.keywordCount(),
                    bestDistance(response.hits())));
        } catch (DataAccessException exception) {
            log.error("Search event could not be saved workspaceId={}", workspaceId, exception);
        }
    }

    private static @Nullable Double bestDistance(List<SearchHitResponse> hits) {
        return hits.stream()
                .map(SearchHitResponse::distance)
                .filter(Objects::nonNull)
                .min(Double::compare)
                .orElse(null);
    }
}

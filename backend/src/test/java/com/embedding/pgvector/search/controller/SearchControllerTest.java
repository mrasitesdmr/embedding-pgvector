package com.embedding.pgvector.search.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.embedding.pgvector.exception.ConflictException;
import com.embedding.pgvector.exception.NotFoundException;
import com.embedding.pgvector.exception.ServiceUnavailableException;
import com.embedding.pgvector.search.domain.dto.MatchedTermResponse;
import com.embedding.pgvector.search.domain.dto.SearchChunksRequest;
import com.embedding.pgvector.search.domain.dto.SearchEventResponse;
import com.embedding.pgvector.search.domain.dto.SearchHitResponse;
import com.embedding.pgvector.search.domain.dto.SearchResultResponse;
import com.embedding.pgvector.search.domain.dto.SearchTimingsResponse;
import com.embedding.pgvector.search.domain.entity.FilterStrategy;
import com.embedding.pgvector.search.domain.entity.MatchedTermField;
import com.embedding.pgvector.search.domain.entity.SearchBranch;
import com.embedding.pgvector.search.domain.entity.SearchMode;
import com.embedding.pgvector.search.service.SearchService;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * SearchController ve SearchEventController'ın arama isteği doğrulamasını, varsayılanları ve yanıt
 * alanlarını sahte servisle doğrular.
 */
@WebMvcTest({SearchController.class, SearchEventController.class})
class SearchControllerTest {

    private static final String SEARCH_URL = "/api/workspaces/42/search";

    @Autowired
    private MockMvcTester mockMvcTester;

    @MockitoBean
    private SearchService searchService;

    private MvcTestResult search(String body) {
        return mockMvcTester.post().uri(SEARCH_URL).contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private static SearchResultResponse emptyResult() {
        return new SearchResultResponse("q", SearchMode.KEYWORD, null, null, 10, 0, 0, null, false,
                new SearchTimingsResponse(null, 1L, 2L), List.of(), null);
    }

    private void assertViolation(MvcTestResult result, String field, String message) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("VALIDATION_FAILED");
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo(message);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Validation Failed");
        assertThat(result).bodyJson().extractingPath("$.type")
                .isEqualTo("https://embedding-pgvector.example/errors/validation-failed");
        then(searchService).should(never()).searchChunks(anyLong(), any());
    }

    @Test
    void givenBlankOrTooLongQuery_whenSearch_thenReturns400ValidationFailed() {
        // Act
        MvcTestResult blank = search("{\"query\":\"   \"}");
        MvcTestResult tooLong = search("{\"query\":\"" + "a".repeat(501) + "\"}");
        MvcTestResult missing = search("{}");

        // Assert
        assertViolation(blank, "query", "must not be blank");
        assertViolation(tooLong, "query", "size must be at most 500 characters");
        assertViolation(missing, "query", "must not be blank");
    }

    @Test
    void givenQueryOf500CharsWithSpaces_whenSearch_thenServiceReceivesStrippedQuery() {
        // Arrange
        given(searchService.searchChunks(eq(42L), any())).willReturn(emptyResult());
        String query = "ş".repeat(500);

        // Act
        MvcTestResult result = search("{\"query\":\"  " + query + "  \"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<SearchChunksRequest> request = ArgumentCaptor.forClass(SearchChunksRequest.class);
        then(searchService).should().searchChunks(eq(42L), request.capture());
        assertThat(request.getValue().query()).isEqualTo(query);
    }

    static Stream<Arguments> invalidParameters() {
        return Stream.of(
                Arguments.of("\"mode\":\"fuzzy\"", "mode", "must be one of KEYWORD, VECTOR, HYBRID"),
                Arguments.of("\"filterStrategy\":\"x\"", "filterStrategy",
                        "must be one of POST_FILTER, ITERATIVE_SCAN"),
                Arguments.of("\"efSearch\":9", "efSearch", "must be between 10 and 1000"),
                Arguments.of("\"efSearch\":1001", "efSearch", "must be between 10 and 1000"),
                Arguments.of("\"limit\":0", "limit", "must be between 1 and 50"),
                Arguments.of("\"limit\":51", "limit", "must be between 1 and 50"),
                Arguments.of("\"channel\":\"" + "c".repeat(61) + "\"", "channel",
                        "size must be at most 60 characters"));
    }

    @ParameterizedTest
    @MethodSource("invalidParameters")
    void givenInvalidSearchParameter_whenSearch_thenReturns400WithFieldName(String json, String field,
            String message) {
        // Act
        MvcTestResult result = search("{\"query\":\"izin\"," + json + "}");

        // Assert
        assertViolation(result, field, message);
    }

    static Stream<String> boundaryParameters() {
        return Stream.of("\"efSearch\":10", "\"efSearch\":1000", "\"limit\":1", "\"limit\":50",
                "\"channel\":\"" + "c".repeat(60) + "\"", "\"mode\":\"Vector\"", "\"filterStrategy\":\"Iterative_Scan\"");
    }

    @ParameterizedTest
    @MethodSource("boundaryParameters")
    void givenBoundaryParameter_whenSearch_thenAccepted(String json) {
        // Arrange
        given(searchService.searchChunks(eq(42L), any())).willReturn(emptyResult());

        // Act
        MvcTestResult result = search("{\"query\":\"izin\"," + json + "}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
    }

    @Test
    void givenLowercaseModeAndFilter_whenSearch_thenAccepted() {
        // Arrange
        given(searchService.searchChunks(eq(42L), any())).willReturn(emptyResult());

        // Act
        MvcTestResult result = search("{\"query\":\"IZN-99\",\"mode\":\"keyword\",\"filterStrategy\":\"post_filter\"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        ArgumentCaptor<SearchChunksRequest> request = ArgumentCaptor.forClass(SearchChunksRequest.class);
        then(searchService).should().searchChunks(eq(42L), request.capture());
        assertThat(SearchMode.fromInput(request.getValue().mode())).isEqualTo(SearchMode.KEYWORD);
        assertThat(FilterStrategy.fromInput(request.getValue().filterStrategy())).isEqualTo(FilterStrategy.POST_FILTER);
    }

    @Test
    void givenBlankChannel_whenSearch_thenChannelBecomesNull() {
        // Arrange
        given(searchService.searchChunks(eq(42L), any())).willReturn(emptyResult());

        // Act
        search("{\"query\":\"x\",\"channel\":\"   \"}");

        // Assert
        ArgumentCaptor<SearchChunksRequest> request = ArgumentCaptor.forClass(SearchChunksRequest.class);
        then(searchService).should().searchChunks(eq(42L), request.capture());
        assertThat(request.getValue().channel()).isNull();
    }

    @Test
    void givenHybridResult_whenSearch_thenResponseCarriesEveryContractField() {
        // Arrange
        SearchHitResponse hit = new SearchHitResponse(1, 5L, 31, 301L, "İzin notları", "Hata kodları",
                "IZN-99 hata kodu nedir", List.of(SearchBranch.KEYWORD, SearchBranch.SEMANTIC), 1, 0.9, 2, 0.25,
                0.0325, List.of(new MatchedTermResponse(MatchedTermField.CONTENT, 0, 6)));
        given(searchService.searchChunks(eq(42L), any())).willReturn(new SearchResultResponse("IZN-99",
                SearchMode.HYBRID, FilterStrategy.ITERATIVE_SCAN, 40, 10, 1, 1, 10, false,
                new SearchTimingsResponse(12L, 5L, 20L), List.of(hit), "Index Scan"));

        // Act
        MvcTestResult result = search("{\"query\":\"IZN-99\"}");

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.mode").isEqualTo("HYBRID");
        assertThat(result).bodyJson().extractingPath("$.filterStrategy").isEqualTo("ITERATIVE_SCAN");
        assertThat(result).bodyJson().extractingPath("$.vectorShort").isEqualTo(false);
        assertThat(result).bodyJson().extractingPath("$.timings.embeddingMs").isEqualTo(12);
        assertThat(result).bodyJson().extractingPath("$.vectorPlan").isEqualTo("Index Scan");
        assertThat(result).bodyJson().extractingPath("$.hits[0].matchedBy[1]").isEqualTo("SEMANTIC");
        assertThat(result).bodyJson().extractingPath("$.hits[0].distance").isEqualTo(0.25);
        assertThat(result).bodyJson().extractingPath("$.hits[0].matchedTerms[0].field").isEqualTo("CONTENT");
        assertThat(result).bodyJson().extractingPath("$.hits[0].matchedTerms[0].end").isEqualTo(6);
    }

    static Stream<Arguments> serviceFailures() {
        return Stream.of(
                Arguments.of(new NotFoundException("WORKSPACE_NOT_FOUND", "x"), 404),
                Arguments.of(new ConflictException("EMBEDDING_MODEL_MISMATCH", "x"), 409),
                Arguments.of(new ServiceUnavailableException("EMBEDDING_UNAVAILABLE", "x"), 503),
                Arguments.of(new IllegalStateException("boom"), 500));
    }

    @ParameterizedTest
    @MethodSource("serviceFailures")
    void givenServiceFailure_whenSearch_thenProblemDetailWithMatchingStatusAndCode(RuntimeException exception,
            int status) {
        // Arrange
        given(searchService.searchChunks(eq(42L), any())).willThrow(exception);
        String expectedCode = exception instanceof com.embedding.pgvector.exception.BaseException base
                ? base.getErrorCode() : "INTERNAL_ERROR";

        // Act
        MvcTestResult result = search("{\"query\":\"x\"}");

        // Assert
        assertThat(result).hasStatus(status).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo(expectedCode);
        assertThat(result).bodyJson().extractingPath("$.detail").asString().doesNotContain("boom");
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(
                "https://embedding-pgvector.example/errors/" + expectedCode.toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo(SEARCH_URL);
        assertThat(result).bodyJson().extractingPath("$.title").isEqualTo(
                org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase());
    }

    @Test
    void givenSearchEvents_whenListSearchEvents_thenReturnsPageResponse() {
        // Arrange
        SearchEventResponse event = new SearchEventResponse(3L, Instant.parse("2026-10-25T10:00:00Z"), "İzin notları",
                "IZN-99", SearchMode.HYBRID, FilterStrategy.ITERATIVE_SCAN, 10, 4, 5L, "Hata kodları", 21L);
        given(searchService.listSearchEvents(42L, 0, 20))
                .willReturn(new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/search-events").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.content[0].searchEventId").isEqualTo(3);
        assertThat(result).bodyJson().extractingPath("$.content[0].topChunkHeading").isEqualTo("Hata kodları");
        assertThat(result).bodyJson().extractingPath("$.content[0].channel").isEqualTo("İzin notları");
        assertThat(result).bodyJson().extractingPath("$.totalPages").isEqualTo(1);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"page=-1,page,must be greater than or equal to 0",
            "size=0,size,must be between 1 and 100", "size=101,size,must be between 1 and 100"})
    void givenInvalidPaging_whenListSearchEvents_thenReturns400(String query, String field, String message) {
        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/search-events?" + query).exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.violations[0].field").isEqualTo(field);
        assertThat(result).bodyJson().extractingPath("$.violations[0].message").isEqualTo(message);
        then(searchService).should(never()).listSearchEvents(anyLong(), anyInt(), anyInt());
    }

    @Test
    void givenUnknownWorkspace_whenGetSearchEventStats_thenReturns404WorkspaceNotFound() {
        // Arrange
        given(searchService.getSearchEventStats(999L)).willThrow(new NotFoundException("WORKSPACE_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/999/search-events/stats").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).hasContentTypeCompatibleWith("application/problem+json");
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("WORKSPACE_NOT_FOUND");
    }

    @Test
    void givenStats_whenGetSearchEventStats_thenReturnsContractFields() {
        // Arrange
        given(searchService.getSearchEventStats(42L)).willReturn(new com.embedding.pgvector.search.domain.dto.SearchEventStatsResponse(
                0L, 0L, 0L, 0L, 0L, null, null, "Europe/Istanbul", Instant.parse("2026-10-24T21:00:00Z"),
                Instant.parse("2026-10-18T21:00:00Z")));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/search-events/stats").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.timeZone").isEqualTo("Europe/Istanbul");
        assertThat(result).bodyJson().extractingPath("$.todayStart").isEqualTo("2026-10-24T21:00:00Z");
        assertThat(result).bodyJson().extractingPath("$.last7DaysStart").isEqualTo("2026-10-18T21:00:00Z");
        assertThat(result).bodyJson().hasPath("$.zeroResultRate");
        assertThat(result).bodyJson().extractingPath("$.zeroResultRate").isNull();
        assertThat(result).bodyJson().hasPath("$.lowResultRate");
        for (String field : List.of("totalCount", "todayCount", "last7DaysCount", "zeroResultCount", "lowResultCount")) {
            assertThat(result).bodyJson().extractingPath("$." + field).isEqualTo(0);
        }
    }

    @Test
    void givenPopularQueries_whenGetPopularQueries_thenReturnsQueriesJson() {
        // Arrange
        given(searchService.listPopularQueries(42L)).willReturn(new com.embedding.pgvector.search.domain.dto.PopularQueriesResponse(
                List.of(new com.embedding.pgvector.search.domain.dto.PopularQueryResponse("Yıllık izin kaç gün?", 2L,
                        Instant.parse("2026-10-25T09:20:11.437Z")))));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/42/search-events/popular-queries").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyJson().extractingPath("$.queries[0].query").isEqualTo("Yıllık izin kaç gün?");
        assertThat(result).bodyJson().extractingPath("$.queries[0].searchCount").isEqualTo(2);
        assertThat(result).bodyJson().extractingPath("$.queries[0].lastSearchedAt")
                .isEqualTo("2026-10-25T09:20:11.437Z");
    }

    @Test
    void givenUnknownWorkspace_whenGetPopularQueries_thenReturns404WorkspaceNotFound() {
        // Arrange
        given(searchService.listPopularQueries(999L)).willThrow(new NotFoundException("WORKSPACE_NOT_FOUND", "x"));

        // Act
        MvcTestResult result = mockMvcTester.get().uri("/api/workspaces/999/search-events/popular-queries").exchange();

        // Assert
        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.errorCode").isEqualTo("WORKSPACE_NOT_FOUND");
    }
}

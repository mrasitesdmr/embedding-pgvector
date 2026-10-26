package com.embedding.pgvector.search.domain.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Bir çalışma alanında sonuç bulan en sık aramaların listesini yanıt gövdesinde taşır.
 */
public record PopularQueriesResponse(
        @ArraySchema(arraySchema = @Schema(description = "At most search.popular-query-limit (5) items, in the order "
                + "described in listPopularQueries", requiredMode = Schema.RequiredMode.REQUIRED), maxItems = 5)
        List<PopularQueryResponse> queries) {

    public PopularQueriesResponse {
        queries = List.copyOf(queries);
    }
}

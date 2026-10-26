package com.embedding.pgvector.search.domain.dto;

import com.embedding.pgvector.search.domain.entity.MatchedTermField;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Kelime aramasında eşleşen bir kelimenin başlıkta ya da içerikte nerede başlayıp bittiğini taşır.
 */
public record MatchedTermResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        MatchedTermField field,

        @Schema(description = "Start offset in the field text, UTF-16 code units (JavaScript string index), inclusive",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int start,

        @Schema(description = "End offset, exclusive", requiredMode = Schema.RequiredMode.REQUIRED)
        int end) {
}

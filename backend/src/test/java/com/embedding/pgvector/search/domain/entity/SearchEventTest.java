package com.embedding.pgvector.search.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SearchEvent'in arama metnini büyük küçük harf ve boşluktan bağımsız bir anahtara çevirdiğini ve kaydı
 * oluşturduğunu doğrular.
 */
class SearchEventTest {

    @ParameterizedTest
    @ValueSource(strings = {"İzin nasıl alınır", "izin nasıl alınır ", "IZIN NASIL ALINIR", "izin  nasıl\talınır",
            "  Izin Nasil Alinir  "})
    void givenQueriesDifferingInCaseAndSpaces_whenToQueryKey_thenReturnsSameKey(String query) {
        // Act
        String key = SearchEvent.toQueryKey(query);

        // Assert
        assertThat(key).isEqualTo("izin nasil alinir");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Yıllık izin kaç gün?|yillik izin kaç gün?",
            "Yıllık izin kaç gün|yillik izin kaç gün",
            "Şube|şube",
            "Sube|sube"})
    void givenQueriesDifferingInPunctuationOrLetters_whenToQueryKey_thenReturnsDifferentKeys(String query,
            String expectedKey) {
        // Act
        String key = SearchEvent.toQueryKey(query);

        // Assert
        assertThat(key).isEqualTo(expectedKey);
    }

    @Test
    void givenQuery_whenRecord_thenKeepsTextAndStoresKey() {
        // Act
        SearchEvent event = SearchEvent.record(42L, Instant.parse("2026-10-25T10:00:00Z"), "Web", "  Yıllık İZİN ",
                SearchMode.KEYWORD, null, null, 10, 1, 5L, "h", 3L, 1, null);

        // Assert
        assertThat(event.getQuery()).isEqualTo("  Yıllık İZİN ");
        assertThat(event.getQueryKey()).isEqualTo("yillik izin");
    }
}

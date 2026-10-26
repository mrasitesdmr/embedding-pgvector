package com.embedding.pgvector.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.embedding.pgvector.search.domain.dto.MatchedTermResponse;
import com.embedding.pgvector.search.domain.entity.MatchedTermField;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * KeywordHighlighter'ın arama kelimelerini ayıklamasını ve başlık ile içerikte vurgulanacak konumları
 * bulmasını doğrular.
 */
class KeywordHighlighterTest {

    private final KeywordHighlighter highlighter = new KeywordHighlighter();

    @Test
    void givenHyphenatedAndTurkishWords_whenWords_thenExtractsWholeWordsInOrderWithoutDuplicates() {
        // Act
        Set<String> words = highlighter.words("Hata kodları", "IZN-99 hata kodu, nedir? ışık ışık");

        // Assert
        assertThat(words).containsExactly("Hata", "kodları", "IZN-99", "hata", "kodu", "nedir", "ışık");
    }

    @Test
    void givenNullHeading_whenWords_thenOnlyContentWords() {
        // Act
        Set<String> words = highlighter.words(null, "bir iki");

        // Assert
        assertThat(words).containsExactly("bir", "iki");
    }

    @Test
    void givenMatchingStems_whenHighlight_thenPositionsOfWholeWordsInHeadingAndContent() {
        // Arrange
        Set<String> queryLexemes = Set.of("izn-99", "hata", "kod");
        Map<String, Set<String>> wordLexemes = Map.of(
                "IZN-99", Set.of("izn-99"), "hata", Set.of("hata"), "kodu", Set.of("kod"),
                "nedir", Set.of("nedir"), "Hata", Set.of("hata"));

        // Act
        List<MatchedTermResponse> terms = highlighter.highlight(queryLexemes, wordLexemes, "Hata kodları",
                "IZN-99 hata kodu nedir");

        // Assert
        assertThat(terms).containsExactly(
                new MatchedTermResponse(MatchedTermField.HEADING, 0, 4),
                new MatchedTermResponse(MatchedTermField.CONTENT, 0, 6),
                new MatchedTermResponse(MatchedTermField.CONTENT, 7, 11),
                new MatchedTermResponse(MatchedTermField.CONTENT, 12, 16));
    }

    @Test
    void givenNoQueryLexemes_whenHighlight_thenEmpty() {
        // Act
        List<MatchedTermResponse> terms = highlighter.highlight(Set.of(), Map.of("a", Set.of("a")), "a", "a");

        // Assert
        assertThat(terms).isEmpty();
    }

    @Test
    void givenNullHeading_whenHighlight_thenOnlyContentMatches() {
        // Act
        List<MatchedTermResponse> terms = highlighter.highlight(Set.of("izin"), Map.of("izin", Set.of("izin")),
                null, "izin");

        // Assert
        assertThat(terms).containsExactly(new MatchedTermResponse(MatchedTermField.CONTENT, 0, 4));
    }
}

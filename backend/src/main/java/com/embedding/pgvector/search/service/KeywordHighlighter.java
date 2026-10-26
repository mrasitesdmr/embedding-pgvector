package com.embedding.pgvector.search.service;

import com.embedding.pgvector.search.domain.dto.MatchedTermResponse;
import com.embedding.pgvector.search.domain.entity.MatchedTermField;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Kelime aramasıyla bulunan parçalarda arama metniyle aynı köke sahip kelimelerin konumlarını bulur.
 */
@Component
public class KeywordHighlighter {

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*");

    public Set<String> words(@Nullable String heading, String content) {
        Set<String> words = new LinkedHashSet<>();
        collectWords(heading, words);
        collectWords(content, words);
        return words;
    }

    public List<MatchedTermResponse> highlight(Set<String> queryLexemes, Map<String, Set<String>> wordLexemes,
            @Nullable String heading, String content) {
        List<MatchedTermResponse> matchedTerms = new ArrayList<>();
        if (queryLexemes.isEmpty()) {
            return matchedTerms;
        }
        addMatches(MatchedTermField.HEADING, heading, queryLexemes, wordLexemes, matchedTerms);
        addMatches(MatchedTermField.CONTENT, content, queryLexemes, wordLexemes, matchedTerms);
        return matchedTerms;
    }

    private static void collectWords(@Nullable String text, Set<String> words) {
        if (text == null) {
            return;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(matcher.group());
        }
    }

    private static void addMatches(MatchedTermField field, @Nullable String text, Set<String> queryLexemes,
            Map<String, Set<String>> wordLexemes, List<MatchedTermResponse> matchedTerms) {
        if (text == null) {
            return;
        }
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            Set<String> lexemes = wordLexemes.getOrDefault(matcher.group(), Set.of());
            if (!Collections.disjoint(lexemes, queryLexemes)) {
                matchedTerms.add(new MatchedTermResponse(field, matcher.start(), matcher.end()));
            }
        }
    }
}

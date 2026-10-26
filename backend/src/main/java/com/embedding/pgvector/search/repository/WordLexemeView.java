package com.embedding.pgvector.search.repository;

/**
 * Bir kelimeyi Türkçe metin aramasındaki köküyle eşleyen sorgu satırını taşır.
 */
public interface WordLexemeView {

    String getWord();

    String getLexeme();
}

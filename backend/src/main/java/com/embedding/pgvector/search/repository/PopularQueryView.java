package com.embedding.pgvector.search.repository;

import java.time.Instant;

/**
 * Sık aranan aramalar sorgusunun bir satırını taşır.
 */
public interface PopularQueryView {

    String getQuery();

    long getSearchCount();

    Instant getLastSearchedAt();
}

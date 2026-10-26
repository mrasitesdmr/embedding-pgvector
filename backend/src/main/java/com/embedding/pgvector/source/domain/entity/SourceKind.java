package com.embedding.pgvector.source.domain.entity;

/**
 * Bir kaynağın hangi dosya ya da içerik türünden oluştuğunu belirtir.
 */
public enum SourceKind {
    WEB_CRAWL,
    PDF,
    MARKDOWN,
    CSV,
    TICKET_EXPORT;

    public boolean isUploadable() {
        return this == PDF || this == MARKDOWN || this == CSV;
    }
}

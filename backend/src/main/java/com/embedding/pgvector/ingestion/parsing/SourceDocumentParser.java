package com.embedding.pgvector.ingestion.parsing;

import com.embedding.pgvector.source.domain.entity.SourceKind;
import java.util.List;

/**
 * Bir kaynak türündeki dosyayı başlıklı metin bölümlerine çeviren ayrıştırıcıların ortak sözleşmesidir.
 */
public interface SourceDocumentParser {

    SourceKind kind();

    List<TextSection> parse(byte[] content);
}

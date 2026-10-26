package com.embedding.pgvector.ingestion.parsing;

/**
 * Yüklenen belge okunamadığında fırlatılır; içe alma işi dosya okunamadı hatasıyla kapanır.
 */
public class DocumentUnreadableException extends RuntimeException {

    DocumentUnreadableException(String message, Throwable cause) {
        super(message, cause);
    }
}

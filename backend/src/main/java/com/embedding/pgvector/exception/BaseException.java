package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

/**
 * Sabit hata kodu ve HTTP durumu taşıyan uygulama hatalarının ortak tabanıdır.
 */
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final HttpStatus status;

    protected BaseException(String errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 409 yanıtına dönüşen, isteğin mevcut durumla çakıştığı hataları taşır.
 */
public class ConflictException extends BaseException {

    public ConflictException(String errorCode, String message) {
        super(errorCode, HttpStatus.CONFLICT, message);
    }
}

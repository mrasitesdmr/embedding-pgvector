package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 404 yanıtına dönüşen, istenen kaydın bulunamadığı hataları taşır.
 */
public class NotFoundException extends BaseException {

    public NotFoundException(String errorCode, String message) {
        super(errorCode, HttpStatus.NOT_FOUND, message);
    }
}

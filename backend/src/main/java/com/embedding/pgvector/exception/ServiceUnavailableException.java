package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 503 yanıtına dönüşen, embedding servisine ulaşılamadığı ya da modelin bulunmadığı hataları taşır.
 */
public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String errorCode, String message) {
        super(errorCode, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

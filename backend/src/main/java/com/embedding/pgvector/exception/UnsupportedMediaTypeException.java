package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

/**
 * HTTP 415 yanıtına dönüşen, desteklenmeyen dosya türü ya da metin kodlaması hatalarını taşır.
 */
public class UnsupportedMediaTypeException extends BaseException {

    public UnsupportedMediaTypeException(String errorCode, String message) {
        super(errorCode, HttpStatus.UNSUPPORTED_MEDIA_TYPE, message);
    }
}

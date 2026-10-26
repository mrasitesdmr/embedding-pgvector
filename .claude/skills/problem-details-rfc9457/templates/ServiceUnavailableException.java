package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BaseException {

    public ServiceUnavailableException(String errorCode, String message) {
        super(errorCode, HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}

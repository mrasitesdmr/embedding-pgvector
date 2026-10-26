package com.embedding.pgvector.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BaseException {

    public ForbiddenException(String errorCode, String message) {
        super(errorCode, HttpStatus.FORBIDDEN, message);
    }
}

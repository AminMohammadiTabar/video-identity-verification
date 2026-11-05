package com.example.videokyc.dms.service;

import org.springframework.http.HttpStatus;

public class DmsException extends RuntimeException {
    private final HttpStatus status;

    public DmsException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public DmsException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}

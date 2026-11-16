package com.example.videokyc.auth.service;

import org.springframework.http.HttpStatus;

public class VerificationException extends RuntimeException {
    private final HttpStatus status;
    public VerificationException(HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.status = status;
    }
    public HttpStatus getStatus() { return status; }
}

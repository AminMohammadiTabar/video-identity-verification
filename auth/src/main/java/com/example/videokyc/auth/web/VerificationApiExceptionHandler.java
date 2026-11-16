package com.example.videokyc.auth.web;

import com.example.videokyc.auth.service.VerificationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {VerificationApiController.class, ReviewApiController.class})
public class VerificationApiExceptionHandler {
    @ExceptionHandler(VerificationException.class)
    ResponseEntity<ProblemDetail> handle(VerificationException error) {
        return ResponseEntity.status(error.getStatus())
                .body(ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage()));
    }
}

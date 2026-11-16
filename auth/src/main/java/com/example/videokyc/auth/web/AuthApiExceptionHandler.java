package com.example.videokyc.auth.web;

import com.example.videokyc.auth.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = AuthApiController.class)
public class AuthApiExceptionHandler {
    @ExceptionHandler(RegistrationService.EmailAlreadyRegisteredException.class)
    ResponseEntity<ProblemDetail> duplicate() {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "Email already registered"));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ResponseEntity<ProblemDetail> validation(Exception ex) {
        return ResponseEntity.badRequest()
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid registration input"));
    }
}

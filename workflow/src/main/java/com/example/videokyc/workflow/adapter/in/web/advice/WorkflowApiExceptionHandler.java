package com.example.videokyc.workflow.adapter.in.web.advice;

import com.example.videokyc.workflow.domain.shared.DomainException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.Locale;

/**
 * Centralized REST API exception handler for the Workflow service.
 * <p>
 * Translates domain exceptions and validation failures into standard RFC 7807 {@link ProblemDetail}
 * responses with localized error messages resolved from {@link MessageSource} according to the
 * client's {@link LocaleContextHolder} (Accept-Language header, defaulting to Persian 'fa').
 */
@RestControllerAdvice
public class WorkflowApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WorkflowApiExceptionHandler.class);

    private final MessageSource messageSource;

    public WorkflowApiExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ProblemDetail> handleDomainException(DomainException ex) {
        Locale locale = LocaleContextHolder.getLocale();
        String errorCode = ex.getErrorCode();
        HttpStatus status = resolveHttpStatus(errorCode);

        // Resolve localized message using the domain error code; fall back to the domain exception's English message
        String localizedDetail = messageSource.getMessage(errorCode, null, ex.getMessage(), locale);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, localizedDetail);
        problem.setTitle(errorCode);
        problem.setType(URI.create("urn:error:workflow:" + (errorCode != null ? errorCode.toLowerCase().replace('_', '-') : "domain-error")));
        problem.setProperty("errorCode", errorCode);

        log.atWarn()
                .addKeyValue("event", "workflow.error")
                .addKeyValue("errorCode", errorCode)
                .addKeyValue("httpStatus", status.value())
                .addKeyValue("locale", locale.toLanguageTag())
                .log("Workflow domain exception: {}", ex.getMessage());

        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleValidation(Exception ex) {
        Locale locale = LocaleContextHolder.getLocale();
        String errorCode = "VALIDATION_ERROR";
        String localizedDetail = messageSource.getMessage(errorCode, null, "Invalid input request", locale);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, localizedDetail);
        problem.setTitle(errorCode);
        problem.setType(URI.create("urn:error:workflow:validation-error"));
        problem.setProperty("errorCode", errorCode);

        return ResponseEntity.badRequest().body(problem);
    }

    private HttpStatus resolveHttpStatus(String errorCode) {
        if (errorCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        if (errorCode.endsWith("_NOT_FOUND")) {
            return HttpStatus.NOT_FOUND;
        }
        if (errorCode.startsWith("DUPLICATE_")
                || errorCode.equals("PENDING_PUBLICATION_EXISTS")
                || errorCode.equals("PUBLICATION_IN_PROGRESS")
                || errorCode.startsWith("VERSION_ALREADY_")
                || errorCode.startsWith("CANNOT_ARCHIVE_")) {
            return HttpStatus.CONFLICT;
        }
        if (errorCode.equals("VERSION_NOT_EDITABLE")
                || errorCode.equals("DEFINITION_NOT_ACTIVE")
                || errorCode.equals("DEFINITION_ARCHIVED")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_REQUEST;
    }
}

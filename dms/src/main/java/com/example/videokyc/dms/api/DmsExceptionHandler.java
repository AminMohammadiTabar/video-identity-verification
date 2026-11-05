package com.example.videokyc.dms.api;

import com.example.videokyc.dms.service.DmsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class DmsExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(DmsExceptionHandler.class);

    @ExceptionHandler(DmsException.class)
    ResponseEntity<ProblemDetail> dmsFailure(DmsException error) {
        if (error.getStatus().is5xxServerError()) {
            // No cause stack trace here: filesystem exceptions may reveal private storage paths.
            log.atError().addKeyValue("event", "dms.action")
                    .addKeyValue("action", "HANDLE_ERROR")
                    .addKeyValue("httpStatus", error.getStatus().value())
                    .addKeyValue("errorType", error.getClass().getSimpleName())
                    .log("DMS storage operation failed");
        }
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(error.getStatus(),
                error.getStatus().is5xxServerError() ? "DMS operation failed" : error.getMessage());
        return ResponseEntity.status(error.getStatus()).body(detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ProblemDetail> tooLarge(MaxUploadSizeExceededException error) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                        "Upload exceeds the configured size limit"));
    }
}

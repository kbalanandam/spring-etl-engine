package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes ScheduleController exception-to-response mapping.
 */
@RestControllerAdvice(assignableTypes = ScheduleController.class)
class ScheduleControllerAdvice {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleControllerAdvice.class);

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Void> handleConflict(IllegalStateException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(ScheduleValidationException.class)
    ResponseEntity<ScheduleValidationErrorResponse> handleScheduleValidation(ScheduleValidationException invalid) {
        return ResponseEntity.badRequest().body(new ScheduleValidationErrorResponse(invalid.reasonToken(), invalid.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ScheduleValidationErrorResponse> handleInvalidSchedule(IllegalArgumentException invalid) {
        return ResponseEntity.badRequest().body(new ScheduleValidationErrorResponse("invalid_schedule", invalid.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ScheduleValidationErrorResponse> handleUnexpected(Exception unexpected) {
        // Keep error payload safe for API callers while preserving full diagnostics in server logs.
        logger.error("Unhandled schedule API exception", unexpected);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ScheduleValidationErrorResponse("internal_error", "Unexpected schedule API error."));
    }
}


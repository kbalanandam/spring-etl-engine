package com.etl.controlplane.api;

import com.etl.controlplane.schedules.ScheduleValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralizes ScheduleController exception-to-response mapping.
 */
@RestControllerAdvice(assignableTypes = ScheduleController.class)
class ScheduleControllerAdvice {

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
}


package com.etl.controlplane.api;

public record ScheduleValidationErrorResponse(
		String reason,
		String message
) {
}


package com.etl.controlplane.schedules;

/**
 * Stable validation exception used to expose reason tokens for schedule API callers.
 */
public class ScheduleValidationException extends IllegalArgumentException {

	private final String reasonToken;

	public ScheduleValidationException(String reasonToken, String message) {
		super(message);
		this.reasonToken = reasonToken == null ? "invalid_schedule" : reasonToken;
	}

	public String reasonToken() {
		return reasonToken;
	}
}


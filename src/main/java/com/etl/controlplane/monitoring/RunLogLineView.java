package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * One operator-facing log line projected for a selected run instance.
 */
public record RunLogLineView(
		Integer lineNumber,
		LocalDateTime loggedAt,
		String level,
		String recordType,
		String event,
		String message,
		boolean structured
) {
}


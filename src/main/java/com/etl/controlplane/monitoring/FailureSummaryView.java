package com.etl.controlplane.monitoring;

/**
 * Run-level failure summary projected from JOB_FAILURE evidence.
 */
public record FailureSummaryView(
		String category,
		String exceptionType,
		String rootCause,
		String message
) {
}


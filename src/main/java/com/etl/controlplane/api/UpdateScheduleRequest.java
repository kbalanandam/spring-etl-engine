package com.etl.controlplane.api;

public record UpdateScheduleRequest(
		String selectedJobKey,
		String expression,
		String timezone,
		Boolean enabled,
		String description
) {
}


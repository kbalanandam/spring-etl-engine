package com.etl.controlplane.api;

public record CreateScheduleRequest(
		String scheduleKey,
		String selectedJobKey,
		String expression,
		String timezone,
		Boolean enabled,
		String description
) {
}


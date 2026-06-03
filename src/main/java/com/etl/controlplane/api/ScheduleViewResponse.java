package com.etl.controlplane.api;

import java.time.LocalDateTime;
import java.time.Instant;

public record ScheduleViewResponse(
		String scheduleId,
		String scheduleKey,
		String selectedJobKey,
		String expression,
		String timezone,
		boolean enabled,
		boolean paused,
		String description,
		LocalDateTime updatedAt,
		Instant nextDueAt
) {
}


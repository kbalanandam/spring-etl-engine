package com.etl.controlplane.schedules;

import java.time.LocalDateTime;

/**
 * Internal control-plane schedule projection used by persistence and services.
 */
public record ScheduleView(
		String scheduleId,
		String scheduleKey,
		String selectedJobKey,
		String expression,
		String timezone,
		boolean enabled,
		boolean paused,
		String description,
		LocalDateTime createdAt,
		LocalDateTime updatedAt,
		String watcherKey
) {
}


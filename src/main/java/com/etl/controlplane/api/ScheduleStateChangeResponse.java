package com.etl.controlplane.api;

import java.time.LocalDateTime;

public record ScheduleStateChangeResponse(
		String scheduleId,
		boolean enabled,
		boolean paused,
		LocalDateTime updatedAt
) {
}


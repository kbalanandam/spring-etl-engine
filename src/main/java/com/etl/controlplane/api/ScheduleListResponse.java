package com.etl.controlplane.api;

import java.util.List;

public record ScheduleListResponse(
		List<ScheduleViewResponse> items,
		int page,
		int size,
		long totalItems
) {
}


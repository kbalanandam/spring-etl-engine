package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunSummaryView;

import java.util.List;

/**
 * Minimal list envelope for /api/v1/runs.
 */
public record RunSummaryListResponse(
		List<RunSummaryView> items,
		int page,
		int size,
		long totalItems
) {
}



package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleSummaryView;

import java.util.List;

/**
 * Minimal list envelope for /api/v1/jobs.
 */
public record JobBundleListResponse(
		List<JobBundleSummaryView> items,
		int page,
		int size,
		long totalItems
) {
}



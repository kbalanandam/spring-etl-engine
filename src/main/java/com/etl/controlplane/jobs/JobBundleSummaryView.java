package com.etl.controlplane.jobs;

import java.util.List;

/**
 * Minimal job-bundle projection for monitoring-first Jobs endpoint.
 */
public record JobBundleSummaryView(
		String jobKey,
		String displayName,
		String jobConfigPath,
		String readinessStatus,
		List<String> validationMessages
) {
}


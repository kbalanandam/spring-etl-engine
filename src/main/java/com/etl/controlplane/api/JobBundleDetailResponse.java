package com.etl.controlplane.api;

import com.etl.controlplane.jobs.JobBundleSummaryView;
import com.etl.controlplane.monitoring.RunSummaryView;
import com.etl.controlplane.triggers.TriggerEventView;

import java.util.List;

/**
 * Aggregated payload for one job detail view in the monitoring-first control plane.
 */
public record JobBundleDetailResponse(
		JobBundleSummaryView job,
		List<RunSummaryView> recentRuns,
		List<TriggerEventView> recentTriggerEvents
) {
}


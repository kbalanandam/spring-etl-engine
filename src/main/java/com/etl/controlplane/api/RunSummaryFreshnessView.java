package com.etl.controlplane.api;

/**
 * Lightweight freshness metadata for the /api/v1/runs list response.
 */
public record RunSummaryFreshnessView(
		boolean refreshRequested,
		boolean forceReplayApplied,
		boolean reindexInProgress,
		long lastReindexEpochMs
) {
}


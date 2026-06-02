package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunArtifactRecordView;

import java.util.List;

/**
 * List envelope for persisted run artifact records.
 */
public record RunArtifactRecordListResponse(
		List<RunArtifactRecordView> items,
		int page,
		int size,
		long totalItems
) {
}


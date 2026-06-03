package com.etl.controlplane.api;

import com.etl.controlplane.monitoring.RunStepRecordView;

import java.util.List;

/**
 * List envelope for persisted run step records.
 */
public record RunStepRecordListResponse(
		List<RunStepRecordView> items,
		int page,
		int size,
		long totalItems
) {
}


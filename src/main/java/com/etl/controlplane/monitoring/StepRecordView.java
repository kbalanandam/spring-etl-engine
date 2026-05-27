package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Ordered step-level view projected from STEP_EVENT evidence.
 */
public record StepRecordView(
		String stepName,
		int sequence,
		String status,
		Long stepExecutionId,
		Long readCount,
		Long writeCount,
		Long filterCount,
		Long skipCount,
		Long rollbackCount,
		Long rejectedCount,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		String subFlow,
		String stepSummary
) {
}


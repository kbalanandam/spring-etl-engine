package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Persisted control-plane step record projection keyed by run identity.
 */
public record RunStepRecordView(
		String stepRecordId,
		String runRecordId,
		String stepName,
		String stepStatus,
		LocalDateTime startedAt,
		LocalDateTime finishedAt,
		Long durationSeconds,
		Long readCount,
		Long writeCount,
		Long filterCount,
		Long skipCount,
		Long rollbackCount,
		Long rejectedCount
) {
}


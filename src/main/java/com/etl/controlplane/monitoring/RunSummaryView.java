package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Lightweight read-model projection for one RUN_SUMMARY evidence line.
 */
public record RunSummaryView(
		String scenario,
		Long jobExecutionId,
		String status,
		LocalDateTime startTime,
		LocalDateTime endTime,
		Long durationSeconds,
		Long sourceCount,
		Long writtenCount,
		Long rejectedCount,
		String runMode,
		String recoveryPolicy,
		String triggerOrigin,
		String logPath
) {
	public RunSummaryView(
			String scenario,
			Long jobExecutionId,
			String status,
			LocalDateTime startTime,
			LocalDateTime endTime,
			Long durationSeconds,
			Long sourceCount,
			Long writtenCount,
			Long rejectedCount,
			String runMode,
			String recoveryPolicy,
			String logPath
	) {
		this(scenario, jobExecutionId, status, startTime, endTime, durationSeconds, sourceCount, writtenCount, rejectedCount, runMode, recoveryPolicy, null, logPath);
	}

	public RunSummaryView(
			String scenario,
			Long jobExecutionId,
			String status,
			LocalDateTime startTime,
			LocalDateTime endTime,
			Long durationSeconds,
			Long sourceCount,
			Long writtenCount,
			Long rejectedCount,
			String logPath
	) {
		this(scenario, jobExecutionId, status, startTime, endTime, durationSeconds, sourceCount, writtenCount, rejectedCount, null, null, null, logPath);
	}
}


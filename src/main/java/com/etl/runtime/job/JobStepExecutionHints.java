package com.etl.runtime.job;

/**
 * Runtime-facing execution hints for one step, intended for orchestration, logs, and UI projection.
 */
public record JobStepExecutionHints(
		JobStepExecutionMode plannedMode,
		boolean countKnown,
		Integer recordCount,
		boolean rejectHandlingEnabled,
		boolean duplicateHandlingEnabled,
		boolean orderedDuplicateSelection,
		boolean archiveOnSuccessEnabled,
		String summary
) {

	public JobStepExecutionHints {
		if (plannedMode == null) {
			throw new IllegalArgumentException("plannedMode must not be null.");
		}
		summary = summary == null || summary.isBlank()
				? "plannedMode=" + plannedMode
				+ ", countKnown=" + countKnown
				+ ", recordCount=" + (recordCount == null ? "unknown" : recordCount)
				+ ", rejectHandlingEnabled=" + rejectHandlingEnabled
				+ ", duplicateHandlingEnabled=" + duplicateHandlingEnabled
				+ ", orderedDuplicateSelection=" + orderedDuplicateSelection
				+ ", archiveOnSuccessEnabled=" + archiveOnSuccessEnabled
				: summary.trim();
	}
}


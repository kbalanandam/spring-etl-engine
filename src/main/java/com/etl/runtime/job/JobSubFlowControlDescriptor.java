package com.etl.runtime.job;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Descriptor for control-plane rules that determine when a subflow may start and when it must be blocked.
 */
public record JobSubFlowControlDescriptor(
		List<JobSubFlowExecutionStatus> startAfterStatuses,
		List<JobSubFlowExecutionStatus> blockOnStatuses,
		boolean requiresHandoffReady,
		String summary
) {

	public JobSubFlowControlDescriptor {
		startAfterStatuses = normalize(startAfterStatuses);
		blockOnStatuses = normalize(blockOnStatuses);
		summary = summary == null || summary.isBlank()
				? "startAfterStatuses=" + startAfterStatuses
				+ ", blockOnStatuses=" + blockOnStatuses
				+ ", requiresHandoffReady=" + requiresHandoffReady
				: summary.trim();
	}

	public boolean startsAfter(JobSubFlowExecutionStatus status) {
		return status != null && startAfterStatuses.contains(status);
	}

	public boolean blocksOn(JobSubFlowExecutionStatus status) {
		return status != null && blockOnStatuses.contains(status);
	}

	public static JobSubFlowControlDescriptor defaultSequentialControl(boolean requiresHandoffReady) {
		return new JobSubFlowControlDescriptor(
				List.of(JobSubFlowExecutionStatus.COMPLETED),
				List.of(JobSubFlowExecutionStatus.FAILED, JobSubFlowExecutionStatus.BLOCKED),
				requiresHandoffReady,
				null
		);
	}

	private static List<JobSubFlowExecutionStatus> normalize(List<JobSubFlowExecutionStatus> statuses) {
		return statuses == null ? List.of() : List.copyOf(new LinkedHashSet<>(statuses));
	}
}


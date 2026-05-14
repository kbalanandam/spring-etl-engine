package com.etl.runtime.job;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Descriptor for control-plane rules that determine when a subflow may start and when it must be blocked.
 *
 * <p>This metadata mirrors the synthesized MainFlow/SubFlow hierarchy used for observability. It
 * records readiness and blocking rules at the subflow level without introducing a new runtime
 * orchestrator separate from the flat ordered Spring Batch plan.</p>
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

	/**
	 * Returns whether the subflow may start after the supplied upstream status is reached.
	 */
	public boolean startsAfter(JobSubFlowExecutionStatus status) {
		return status != null && startAfterStatuses.contains(status);
	}

	/**
	 * Returns whether the supplied upstream status should keep this subflow blocked.
	 */
	public boolean blocksOn(JobSubFlowExecutionStatus status) {
		return status != null && blockOnStatuses.contains(status);
	}

	/**
	 * Builds the default control contract for the current sequential runtime: start after upstream
	 * completion, block on failed/blocked upstream states, and optionally require a ready handoff.
	 */
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


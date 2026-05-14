package com.etl.runtime.job;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Control-plane rules for a step-to-step link.
 *
 * <p>This metadata tells observability and future orchestration layers what upstream statuses are
 * required, which statuses should block downstream readiness, and whether a concrete handoff must
 * be present before the downstream step may proceed.</p>
 */
public record JobStepLinkControlDescriptor(
		List<JobSubFlowExecutionStatus> requiredUpstreamStatuses,
		List<JobSubFlowExecutionStatus> blockingUpstreamStatuses,
		boolean requiresHandoffReady,
		String summary
) {

	public JobStepLinkControlDescriptor {
		requiredUpstreamStatuses = normalize(requiredUpstreamStatuses);
		blockingUpstreamStatuses = normalize(blockingUpstreamStatuses);
		summary = summary == null || summary.isBlank()
				? "requiredUpstreamStatuses=" + requiredUpstreamStatuses
				+ ", blockingUpstreamStatuses=" + blockingUpstreamStatuses
				+ ", requiresHandoffReady=" + requiresHandoffReady
				: summary.trim();
	}

	/**
	 * Returns whether the downstream step requires the supplied upstream subflow status.
	 */
	public boolean requiresUpstreamStatus(JobSubFlowExecutionStatus status) {
		return status != null && requiredUpstreamStatuses.contains(status);
	}

	/**
	 * Returns whether the downstream step should remain blocked for the supplied upstream status.
	 */
	public boolean blocksOnUpstreamStatus(JobSubFlowExecutionStatus status) {
		return status != null && blockingUpstreamStatuses.contains(status);
	}

	/**
	 * Builds the default control contract for the current sequential runtime: wait for upstream
	 * completion, block on failure/blocked states, and optionally require a materialized handoff.
	 */
	public static JobStepLinkControlDescriptor defaultSequentialControl(boolean requiresHandoffReady) {
		return new JobStepLinkControlDescriptor(
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


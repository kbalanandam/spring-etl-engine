package com.etl.runtime.job;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Control-plane rules for a step-to-step link.
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

	public boolean requiresUpstreamStatus(JobSubFlowExecutionStatus status) {
		return status != null && requiredUpstreamStatuses.contains(status);
	}

	public boolean blocksOnUpstreamStatus(JobSubFlowExecutionStatus status) {
		return status != null && blockingUpstreamStatuses.contains(status);
	}

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


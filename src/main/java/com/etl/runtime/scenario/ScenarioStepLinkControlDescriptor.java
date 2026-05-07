package com.etl.runtime.scenario;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Control-plane rules for a step-to-step link.
 */
public record ScenarioStepLinkControlDescriptor(
		List<ScenarioSubFlowExecutionStatus> requiredUpstreamStatuses,
		List<ScenarioSubFlowExecutionStatus> blockingUpstreamStatuses,
		boolean requiresHandoffReady,
		String summary
) {

	public ScenarioStepLinkControlDescriptor {
		requiredUpstreamStatuses = normalize(requiredUpstreamStatuses);
		blockingUpstreamStatuses = normalize(blockingUpstreamStatuses);
		summary = summary == null || summary.isBlank()
				? "requiredUpstreamStatuses=" + requiredUpstreamStatuses
				+ ", blockingUpstreamStatuses=" + blockingUpstreamStatuses
				+ ", requiresHandoffReady=" + requiresHandoffReady
				: summary.trim();
	}

	public boolean requiresUpstreamStatus(ScenarioSubFlowExecutionStatus status) {
		return status != null && requiredUpstreamStatuses.contains(status);
	}

	public boolean blocksOnUpstreamStatus(ScenarioSubFlowExecutionStatus status) {
		return status != null && blockingUpstreamStatuses.contains(status);
	}

	public static ScenarioStepLinkControlDescriptor defaultSequentialControl(boolean requiresHandoffReady) {
		return new ScenarioStepLinkControlDescriptor(
				List.of(ScenarioSubFlowExecutionStatus.COMPLETED),
				List.of(ScenarioSubFlowExecutionStatus.FAILED, ScenarioSubFlowExecutionStatus.BLOCKED),
				requiresHandoffReady,
				null
		);
	}

	private static List<ScenarioSubFlowExecutionStatus> normalize(List<ScenarioSubFlowExecutionStatus> statuses) {
		return statuses == null ? List.of() : List.copyOf(new LinkedHashSet<>(statuses));
	}
}


package com.etl.runtime.scenario;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Descriptor for control-plane rules that determine when a subflow may start and when it must be blocked.
 */
public record ScenarioSubFlowControlDescriptor(
		List<ScenarioSubFlowExecutionStatus> startAfterStatuses,
		List<ScenarioSubFlowExecutionStatus> blockOnStatuses,
		boolean requiresHandoffReady,
		String summary
) {

	public ScenarioSubFlowControlDescriptor {
		startAfterStatuses = normalize(startAfterStatuses);
		blockOnStatuses = normalize(blockOnStatuses);
		summary = summary == null || summary.isBlank()
				? "startAfterStatuses=" + startAfterStatuses
				+ ", blockOnStatuses=" + blockOnStatuses
				+ ", requiresHandoffReady=" + requiresHandoffReady
				: summary.trim();
	}

	public boolean startsAfter(ScenarioSubFlowExecutionStatus status) {
		return status != null && startAfterStatuses.contains(status);
	}

	public boolean blocksOn(ScenarioSubFlowExecutionStatus status) {
		return status != null && blockOnStatuses.contains(status);
	}

	public static ScenarioSubFlowControlDescriptor defaultSequentialControl(boolean requiresHandoffReady) {
		return new ScenarioSubFlowControlDescriptor(
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


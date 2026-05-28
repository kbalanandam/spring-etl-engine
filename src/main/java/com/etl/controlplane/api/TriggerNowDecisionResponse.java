package com.etl.controlplane.api;

/**
 * Placeholder trigger-now decision envelope until launch orchestration is wired.
 */
public record TriggerNowDecisionResponse(
		String jobKey,
		String decisionStatus,
		String message,
		String triggerEventId
) {
}


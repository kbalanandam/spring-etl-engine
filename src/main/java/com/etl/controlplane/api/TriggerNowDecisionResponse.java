package com.etl.controlplane.api;

/**
 * Trigger-now decision envelope returned by job/schedule trigger endpoints.
 */
public record TriggerNowDecisionResponse(
		String jobKey,
		String decisionStatus,
		String message,
		String triggerEventId
) {
}


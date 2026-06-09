package com.etl.controlplane.triggers;

import java.time.Instant;

/**
 * Read model for one trigger event tied to a job key.
 */
public record TriggerEventView(
		String triggerEventId,
		String jobKey,
		String decisionStatus,
		String reason,
		String requestedBy,
		Instant requestedAt,
		String launchedRunId,
		String message,
		String triggerOrigin
) {
	public TriggerEventView(
			String triggerEventId,
			String jobKey,
			String decisionStatus,
			String reason,
			String requestedBy,
			Instant requestedAt,
			String launchedRunId,
			String message
	) {
		this(triggerEventId, jobKey, decisionStatus, reason, requestedBy, requestedAt, launchedRunId, message, null);
	}
}


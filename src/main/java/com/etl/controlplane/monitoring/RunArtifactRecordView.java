package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Persisted control-plane artifact record projection keyed by run/step identity.
 */
public record RunArtifactRecordView(
		String artifactRecordId,
		String runRecordId,
		String stepRecordId,
		String artifactRole,
		String artifactPath,
		LocalDateTime createdAt
) {
}


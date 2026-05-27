package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Artifact path surfaced from step-finished runtime evidence.
 */
public record ArtifactRecordView(
		String artifactId,
		String role,
		String label,
		String pathOrUri,
		LocalDateTime createdAt,
		Long recordCount,
		String stepName
) {
}


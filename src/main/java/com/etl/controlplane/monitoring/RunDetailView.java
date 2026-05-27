package com.etl.controlplane.monitoring;

import java.util.List;

/**
 * Aggregated operator-facing run drill-down view built from runtime evidence.
 */
public record RunDetailView(
		RunSummaryView run,
		List<StepRecordView> steps,
		List<ArtifactRecordView> artifacts,
		FailureSummaryView failureSummary,
		List<EvidenceLinkView> evidenceLinks
) {
}


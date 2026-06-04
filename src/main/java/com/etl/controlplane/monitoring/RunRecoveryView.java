package com.etl.controlplane.monitoring;

import java.util.List;

/**
 * Advisory recovery-oriented projection for one retained run.
 *
 * <p>This view exposes current attempt lineage and checkpoint anchors without claiming that
 * runtime checkpoint resume is currently supported.</p>
 */
public record RunRecoveryView(
		Long jobExecutionId,
		String runRecordId,
		String attemptLinkId,
		String linkKind,
		String priorRunRecordId,
		Long priorJobExecutionId,
		boolean resumeSupported,
		String resumeBlockedReason,
		List<RunCheckpointAnchorView> checkpointAnchors
) {
}


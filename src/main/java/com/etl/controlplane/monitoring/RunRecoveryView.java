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
	public static final String RESUME_BLOCKED_REASON_CHECKPOINT_NOT_SHIPPED =
			"resume-from-checkpoint is not supported in the current shipped runtime; rerun-from-start remains the active execution boundary.";

	public static RunRecoveryView advisoryResumeNotSupported(Long jobExecutionId,
	                                                       String runRecordId,
	                                                       String attemptLinkId,
	                                                       String linkKind,
	                                                       String priorRunRecordId,
	                                                       Long priorJobExecutionId,
	                                                       List<RunCheckpointAnchorView> checkpointAnchors) {
		return new RunRecoveryView(
				jobExecutionId,
				runRecordId,
				attemptLinkId,
				linkKind,
				priorRunRecordId,
				priorJobExecutionId,
				false,
				RESUME_BLOCKED_REASON_CHECKPOINT_NOT_SHIPPED,
				checkpointAnchors == null ? List.of() : checkpointAnchors
		);
	}
}


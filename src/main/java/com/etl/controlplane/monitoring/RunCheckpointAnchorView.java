package com.etl.controlplane.monitoring;

import java.time.LocalDateTime;

/**
 * Advisory retained checkpoint anchor reference for one run.
 */
public record RunCheckpointAnchorView(
		String checkpointAnchorId,
		String stepRecordId,
		String anchorKind,
		String anchorRef,
		String anchorStatus,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {
}


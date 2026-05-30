package com.etl.controlplane.monitoring;

import java.util.List;

/**
 * Run-scoped log projection used by the Operator UI run-detail view.
 */
public record RunScopedLogView(
		Long jobExecutionId,
		String scenario,
		String logPath,
		int totalLines,
		boolean truncated,
		List<RunLogLineView> lines
) {
}


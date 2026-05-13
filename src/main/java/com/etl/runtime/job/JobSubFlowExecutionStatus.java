package com.etl.runtime.job;

/**
 * Planned or observed execution status for a named subflow within the synthesized MainFlow.
 *
 * <p>These states support hierarchy logging and runtime summaries. They describe readiness and
 * completion at the subflow level without changing the fact that execution is ultimately a flat
 * ordered Spring Batch step plan.</p>
 */
public enum JobSubFlowExecutionStatus {
	NOT_STARTED,
	READY,
	RUNNING,
	COMPLETED,
	FAILED,
	BLOCKED,
	SKIPPED
}


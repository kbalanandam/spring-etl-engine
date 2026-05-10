package com.etl.runtime.job;

/**
 * Planned/observed execution status for a named subflow within a MainFlow.
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


package com.etl.runtime.scenario;

/**
 * Planned/observed execution status for a named subflow within a MainFlow.
 */
public enum ScenarioSubFlowExecutionStatus {
	NOT_STARTED,
	READY,
	RUNNING,
	COMPLETED,
	FAILED,
	BLOCKED,
	SKIPPED
}


package com.etl.runtime.job;

/**
 * Execution mode intended or resolved for one scenario step.
 */
public enum JobStepExecutionMode {
	UNRESOLVED,
	CHUNK,
	TASKLET
}


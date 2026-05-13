package com.etl.runtime.job;

/**
 * Execution mode intended or resolved for one scenario step.
 *
 * <p>This captures whether the descriptor expects chunk-style processing, tasklet-style processing,
 * or has not yet resolved the final execution mode. It is especially useful for explaining why
 * ordered duplicate winner selection forces some mappings onto the tasklet path.</p>
 */
public enum JobStepExecutionMode {
	UNRESOLVED,
	CHUNK,
	TASKLET
}


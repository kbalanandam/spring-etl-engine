package com.etl.runtime.job;

/**
 * Describes the effective output contract for one step.
 *
 * <p>The runtime descriptor distinguishes configured targets, intermediate handoff datasets, and
 * final scenario output so run summaries can separate internal handoff traffic from the published
 * business result.</p>
 */
public enum JobStepOutputType {
	CONFIG_TARGET,
	INTERMEDIATE_DATASET,
	FINAL_OUTPUT
}


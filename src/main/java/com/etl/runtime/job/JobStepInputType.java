package com.etl.runtime.job;

/**
 * Describes where one step reads its effective input from.
 */
public enum JobStepInputType {
	CONFIG_SOURCE,
	UPSTREAM_STEP_OUTPUT,
	NAMED_INTERMEDIATE
}


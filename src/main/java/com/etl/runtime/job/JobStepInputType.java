package com.etl.runtime.job;

/**
 * Describes where one step reads its effective input from.
 *
 * <p>These values are part of the synthesized runtime descriptor used for observability. They do
 * not imply a separate execution engine; the underlying batch plan remains an explicit flat step
 * sequence.</p>
 */
public enum JobStepInputType {
	CONFIG_SOURCE,
	UPSTREAM_STEP_OUTPUT,
	NAMED_INTERMEDIATE
}


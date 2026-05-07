package com.etl.runtime.scenario;

/**
 * Describes where one step reads its effective input from.
 */
public enum ScenarioStepInputType {
	CONFIG_SOURCE,
	UPSTREAM_STEP_OUTPUT,
	NAMED_INTERMEDIATE
}


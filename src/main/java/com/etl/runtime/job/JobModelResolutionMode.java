package com.etl.runtime.job;

/**
 * Declares how model classes were expected to be available for one step.
 */
public enum JobModelResolutionMode {
	PREGENERATED,
	SCENARIO_GENERATED,
	LEGACY_BRIDGE
}


package com.etl.runtime.job;

/**
 * Declares how generated model classes were expected to be available for one step.
 *
 * <p>The descriptor uses this to explain whether a step depends on pre-generated classes,
 * scenario-scoped generation for the selected bundle, or a legacy bridge path retained for
 * compatibility while the naming/generation architecture continues to evolve.</p>
 */
public enum JobModelResolutionMode {
	PREGENERATED,
	SCENARIO_GENERATED,
	LEGACY_BRIDGE
}


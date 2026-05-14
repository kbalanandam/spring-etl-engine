package com.etl.runtime.job;

/**
 * Relationship between scenario steps in the synthesized runtime descriptor.
 *
 * <p>These link types explain whether two adjacent steps are connected only by order, by direct
 * data handoff, or through a named intermediate alias that may outlive one subflow boundary.</p>
 */
public enum JobStepLinkType {
	ORDER_ONLY,
	DATA_HANDOFF,
	NAMED_INTERMEDIATE
}


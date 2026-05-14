package com.etl.runtime.job;

/**
 * Identifies how the current scenario runtime descriptor was selected.
 *
 * <p>This is observability metadata, not an execution strategy switch. It explains whether the
 * runtime contract came from the required explicit job-config path or from the optional local demo
 * fallback that must be enabled separately.</p>
 */
public enum JobRunMode {
	EXPLICIT_JOB,
	DEMO_FALLBACK
}


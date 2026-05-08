package com.etl.runtime.job;

/**
 * Recovery policy for the selected scenario execution.
 */
public enum JobRecoveryPolicy {

	RERUN_FROM_START("rerun-from-start",
			"If any step fails, the next attempt reruns the whole selected scenario from the beginning."),
	RESUME_FROM_CHECKPOINT("resume-from-checkpoint",
			"Future direction: restart from a persisted checkpoint instead of replaying the whole scenario.");

	private final String logValue;
	private final String summary;

	JobRecoveryPolicy(String logValue, String summary) {
		this.logValue = logValue;
		this.summary = summary;
	}

	public String logValue() {
		return logValue;
	}

	public String summary() {
		return summary;
	}
}


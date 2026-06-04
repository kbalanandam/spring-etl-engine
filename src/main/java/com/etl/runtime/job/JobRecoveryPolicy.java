package com.etl.runtime.job;

import java.util.Arrays;
import java.util.Optional;

/**
 * Recovery policy advertised by the selected scenario runtime descriptor.
 *
 * <p>This policy describes restart expectations for operators and logs. The shipped runtime is
 * still centered on rerunning an explicit ordered scenario from the beginning; checkpoint resume
 * remains a forward-looking descriptor value so observability and design discussions can use a
 * stable vocabulary.</p>
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

	/**
	 * Returns the normalized value emitted in logs and structured runtime summaries.
	 */
	public String logValue() {
		return logValue;
	}

	/**
	 * Returns the operator-facing description for this recovery policy.
	 */
	public String summary() {
		return summary;
	}

	public static Optional<JobRecoveryPolicy> fromLogValue(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = value.trim();
		return Arrays.stream(values())
				.filter(policy -> policy.logValue.equalsIgnoreCase(normalized))
				.findFirst();
	}
}


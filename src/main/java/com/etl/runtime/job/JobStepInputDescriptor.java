package com.etl.runtime.job;

/**
 * Describes the effective input contract for one scenario step.
 *
 * <p>This descriptor explains whether the step reads directly from the configured source, from an
 * upstream step output, or from a named intermediate alias. It is used for runtime summaries and
 * hierarchy logging rather than as an alternate execution graph.</p>
 */
public record JobStepInputDescriptor(
		JobStepInputType type,
		String sourceName,
		String upstreamStepName,
		String inputAlias,
		String displayLabel,
		String summary
) {

	public JobStepInputDescriptor {
		if (type == null) {
			throw new IllegalArgumentException("type must not be null.");
		}
		displayLabel = normalize(displayLabel, defaultDisplayLabel(type, sourceName, upstreamStepName, inputAlias));
		summary = normalize(summary, defaultSummary(type, sourceName, upstreamStepName, inputAlias));
	}

	/**
	 * Creates the descriptor for a step that reads straight from the selected source config.
	 */
	public static JobStepInputDescriptor fromConfiguredSource(String sourceName) {
		return new JobStepInputDescriptor(
				JobStepInputType.CONFIG_SOURCE,
				sourceName,
				null,
				sourceName,
				null,
				null
		);
	}

	private static String defaultDisplayLabel(JobStepInputType type, String sourceName, String upstreamStepName, String inputAlias) {
		return switch (type) {
			case CONFIG_SOURCE -> sourceName == null || sourceName.isBlank() ? "Configured source" : sourceName.trim();
			case UPSTREAM_STEP_OUTPUT -> upstreamStepName == null || upstreamStepName.isBlank() ? "Upstream step output" : upstreamStepName.trim();
			case NAMED_INTERMEDIATE -> inputAlias == null || inputAlias.isBlank() ? "Named intermediate input" : inputAlias.trim();
		};
	}

	private static String defaultSummary(JobStepInputType type, String sourceName, String upstreamStepName, String inputAlias) {
		return switch (type) {
			case CONFIG_SOURCE -> "Reads from configured source '" + safe(sourceName, "unnamed-source") + "'.";
			case UPSTREAM_STEP_OUTPUT -> "Consumes output from upstream step '" + safe(upstreamStepName, "unnamed-step") + "'.";
			case NAMED_INTERMEDIATE -> "Consumes named intermediate input '" + safe(inputAlias, "unnamed-input") + "'.";
		};
	}

	private static String safe(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String normalize(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}


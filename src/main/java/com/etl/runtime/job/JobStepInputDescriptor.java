package com.etl.runtime.job;

/**
 * Describes the effective input contract for one scenario step.
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


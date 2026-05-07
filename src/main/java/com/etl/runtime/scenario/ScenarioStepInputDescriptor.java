package com.etl.runtime.scenario;

/**
 * Describes the effective input contract for one scenario step.
 */
public record ScenarioStepInputDescriptor(
		ScenarioStepInputType type,
		String sourceName,
		String upstreamStepName,
		String inputAlias,
		String displayLabel,
		String summary
) {

	public ScenarioStepInputDescriptor {
		if (type == null) {
			throw new IllegalArgumentException("type must not be null.");
		}
		displayLabel = normalize(displayLabel, defaultDisplayLabel(type, sourceName, upstreamStepName, inputAlias));
		summary = normalize(summary, defaultSummary(type, sourceName, upstreamStepName, inputAlias));
	}

	public static ScenarioStepInputDescriptor fromConfiguredSource(String sourceName) {
		return new ScenarioStepInputDescriptor(
				ScenarioStepInputType.CONFIG_SOURCE,
				sourceName,
				null,
				sourceName,
				null,
				null
		);
	}

	private static String defaultDisplayLabel(ScenarioStepInputType type, String sourceName, String upstreamStepName, String inputAlias) {
		return switch (type) {
			case CONFIG_SOURCE -> sourceName == null || sourceName.isBlank() ? "Configured source" : sourceName.trim();
			case UPSTREAM_STEP_OUTPUT -> upstreamStepName == null || upstreamStepName.isBlank() ? "Upstream step output" : upstreamStepName.trim();
			case NAMED_INTERMEDIATE -> inputAlias == null || inputAlias.isBlank() ? "Named intermediate input" : inputAlias.trim();
		};
	}

	private static String defaultSummary(ScenarioStepInputType type, String sourceName, String upstreamStepName, String inputAlias) {
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


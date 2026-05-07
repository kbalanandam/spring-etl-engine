package com.etl.runtime.scenario;

/**
 * Describes the effective output contract for one scenario step.
 */
public record ScenarioStepOutputDescriptor(
		ScenarioStepOutputType type,
		String targetName,
		String outputAlias,
		boolean finalScenarioOutput,
		String displayLabel,
		String summary
) {

	public ScenarioStepOutputDescriptor {
		if (type == null) {
			throw new IllegalArgumentException("type must not be null.");
		}
		displayLabel = normalize(displayLabel, defaultDisplayLabel(type, targetName, outputAlias));
		summary = normalize(summary, defaultSummary(type, targetName, outputAlias, finalScenarioOutput));
	}

	public static ScenarioStepOutputDescriptor configuredTarget(String targetName, boolean finalScenarioOutput) {
		return new ScenarioStepOutputDescriptor(
				finalScenarioOutput ? ScenarioStepOutputType.FINAL_OUTPUT : ScenarioStepOutputType.CONFIG_TARGET,
				targetName,
				targetName,
				finalScenarioOutput,
				null,
				null
		);
	}

	private static String defaultDisplayLabel(ScenarioStepOutputType type, String targetName, String outputAlias) {
		return switch (type) {
			case CONFIG_TARGET, FINAL_OUTPUT -> targetName == null || targetName.isBlank() ? "Configured target" : targetName.trim();
			case INTERMEDIATE_DATASET -> outputAlias == null || outputAlias.isBlank() ? "Intermediate dataset" : outputAlias.trim();
		};
	}

	private static String defaultSummary(ScenarioStepOutputType type, String targetName, String outputAlias, boolean finalScenarioOutput) {
		return switch (type) {
			case CONFIG_TARGET -> "Produces configured target '" + safe(targetName, "unnamed-target") + "'.";
			case FINAL_OUTPUT -> "Produces final scenario output '" + safe(targetName, "unnamed-target") + "'.";
			case INTERMEDIATE_DATASET -> "Produces intermediate output '" + safe(outputAlias, "unnamed-output") + "'.";
		} + (finalScenarioOutput ? " This output completes the scenario." : "");
	}

	private static String safe(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}

	private static String normalize(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value.trim();
	}
}


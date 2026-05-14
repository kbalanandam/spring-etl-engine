package com.etl.runtime.job;

import java.util.List;

/**
 * Scenario-level validation summary intended for runtime diagnostics, logs, and UI views.
 *
 * <p>This record captures whether the selected scenario passed the major validation stages
 * required before execution: source config, target config, processor config, and generated-model
 * availability.</p>
 */
public record JobValidationSummary(
		boolean sourceValidated,
		boolean targetValidated,
		boolean processorValidated,
		boolean modelValidated,
		List<String> warnings,
		List<String> blockers,
		String summary
) {

	public JobValidationSummary {
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
		blockers = blockers == null ? List.of() : List.copyOf(blockers);
		summary = defaultSummary(summary, sourceValidated, targetValidated, processorValidated, modelValidated, blockers);
	}

	public boolean passed() {
		return blockers.isEmpty() && sourceValidated && targetValidated && processorValidated && modelValidated;
	}

	private static String defaultSummary(String summary,
	                                     boolean sourceValidated,
	                                     boolean targetValidated,
	                                     boolean processorValidated,
	                                     boolean modelValidated,
	                                     List<String> blockers) {
		if (summary != null && !summary.isBlank()) {
			return summary.trim();
		}
		return "sourceValidated=" + sourceValidated
				+ ", targetValidated=" + targetValidated
				+ ", processorValidated=" + processorValidated
				+ ", modelValidated=" + modelValidated
				+ ", blockerCount=" + blockers.size();
	}
}


package com.etl.runtime.job;

import java.util.List;

/**
 * Step-level validation summary intended for startup diagnostics and later UI projection.
 *
 * <p>This record is the per-step counterpart to {@link JobValidationSummary}. It captures whether
 * mapping selection, source/target validation, and model validation all succeeded for one resolved
 * executable step.</p>
 */
public record JobStepValidationSummary(
		boolean mappingValidated,
		boolean sourceValidated,
		boolean targetValidated,
		boolean modelValidated,
		List<String> warnings,
		List<String> blockers,
		String summary
) {

	public JobStepValidationSummary {
		warnings = warnings == null ? List.of() : List.copyOf(warnings);
		blockers = blockers == null ? List.of() : List.copyOf(blockers);
		summary = defaultSummary(summary, mappingValidated, sourceValidated, targetValidated, modelValidated, blockers);
	}

	public boolean passed() {
		return blockers.isEmpty() && mappingValidated && sourceValidated && targetValidated && modelValidated;
	}

	private static String defaultSummary(String summary,
	                                     boolean mappingValidated,
	                                     boolean sourceValidated,
	                                     boolean targetValidated,
	                                     boolean modelValidated,
	                                     List<String> blockers) {
		if (summary != null && !summary.isBlank()) {
			return summary.trim();
		}
		return "mappingValidated=" + mappingValidated
				+ ", sourceValidated=" + sourceValidated
				+ ", targetValidated=" + targetValidated
				+ ", modelValidated=" + modelValidated
				+ ", blockerCount=" + blockers.size();
	}
}


package com.etl.runtime.job;

import java.util.Objects;

/**
 * Canonical config paths selected by one job-config entry.
 *
 * <p>This record keeps the resolved source, target, and processor config paths together so
 * runtime descriptors, logs, and diagnostics all refer to the same selected bundle files.</p>
 */
public record JobConfigPaths(
		String sourceConfigPath,
		String targetConfigPath,
		String processorConfigPath
) {

	public JobConfigPaths {
		sourceConfigPath = requireNonBlank(sourceConfigPath, "sourceConfigPath");
		targetConfigPath = requireNonBlank(targetConfigPath, "targetConfigPath");
		processorConfigPath = requireNonBlank(processorConfigPath, "processorConfigPath");
	}

	/**
	 * Returns a compact stable summary used by runtime descriptor logs and diagnostics.
	 */
	public String summary() {
		return "source=" + sourceConfigPath + ", target=" + targetConfigPath + ", processor=" + processorConfigPath;
	}

	private static String requireNonBlank(String value, String field) {
		Objects.requireNonNull(value, field + " must not be null.");
		if (value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank.");
		}
		return value.trim();
	}
}



package com.etl.runtime.job;

import java.util.Objects;

/**
 * Canonical config paths selected by one job-config entry.
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



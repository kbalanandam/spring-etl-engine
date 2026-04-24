package com.etl.config;

/**
 * Minimal runtime selection metadata shared with startup and logging concerns.
 *
 * @param scenarioName resolved scenario name for the selected run
 * @param jobConfigPath absolute job-config path when explicit job selection is used
 * @param demoFallbackMode whether the runtime is using the explicit demo fallback path
 */
public record RunConfigurationMetadata(
        String scenarioName,
        String jobConfigPath,
        boolean demoFallbackMode
) {
}


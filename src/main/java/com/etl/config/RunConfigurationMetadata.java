package com.etl.config;

import com.etl.config.job.JobConfig;

import java.util.List;

/**
 * Minimal runtime selection metadata shared with startup and logging concerns.
 *
 * @param scenarioName resolved scenario name for the selected run
 * @param jobConfigPath absolute job-config path when explicit job selection is used
 * @param demoFallbackMode whether the runtime is using the explicit demo fallback path
 * @param steps explicit step definitions in execution order for the selected run
 */
public record RunConfigurationMetadata(
        String scenarioName,
        String jobConfigPath,
        boolean demoFallbackMode,
        List<JobConfig.JobStepConfig> steps
) {
}


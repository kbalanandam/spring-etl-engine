package com.etl.controlplane.jobs;

/**
 * Read-only job-config payload used by Operator UI config drill-down.
 */
public record JobBundleConfigView(
        String jobKey,
        String displayName,
        String jobConfigPath,
        String rawYaml,
        String sourceConfigPath,
        String sourceRawYaml,
        String targetConfigPath,
        String targetRawYaml,
        String processorConfigPath,
        String processorRawYaml
) {
}


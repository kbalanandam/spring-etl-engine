package com.etl.controlplane.api;

/**
 * Read-only payload for one preserved job-config.yaml file.
 */
public record JobBundleConfigResponse(
        String jobKey,
        String displayName,
        String jobConfigPath,
        String rawYaml
) {
}


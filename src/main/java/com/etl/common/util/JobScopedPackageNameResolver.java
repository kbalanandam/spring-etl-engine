package com.etl.common.util;

import com.etl.config.job.JobConfig;

import java.nio.file.Path;

/**
 * Resolves compatibility-first defaults for job-scoped generated model packages.
 * <p>
 * Explicit packageName values in source/target configs still win. When a selected
 * explicit job omits packageName, the runtime and build-time generation paths can
 * derive a stable default package from the selected job identity.
 * </p>
 */
public final class JobScopedPackageNameResolver {

    private static final String DEFAULT_JOB_NAME = "selected-job";
    private static final String DEFAULT_JOB_SEGMENT = "selectedjob";
    private static final String BASE_PACKAGE = "com.etl.generated.job";

    private JobScopedPackageNameResolver() {
    }

    public static String deriveJobName(JobConfig jobConfig, Path jobConfigDirectory) {
        if (jobConfig != null) {
            String configuredName = jobConfig.getName();
            if (configuredName != null && !configuredName.isBlank()) {
                return configuredName.trim();
            }
        }

        if (jobConfigDirectory != null) {
            Path directoryName = jobConfigDirectory.getFileName();
            if (directoryName != null) {
                String folderName = directoryName.toString().trim();
                if (!folderName.isBlank()) {
                    return folderName;
                }
            }
        }

        return DEFAULT_JOB_NAME;
    }

    public static String requireExplicitJobName(JobConfig jobConfig, Path jobConfigPath) {
        if (jobConfig != null) {
            String configuredName = jobConfig.getName();
            if (configuredName != null && !configuredName.isBlank()) {
                return configuredName.trim();
            }
        }

        String jobPath = jobConfigPath == null ? "selected job-config" : jobConfigPath.toString();
        throw new IllegalStateException(
                "Explicit job-config runs require a non-blank 'name' in " + jobPath
                        + " so generated-model naming stays deterministic."
        );
    }

    public static String resolveSourcePackage(String jobName) {
        return resolvePackage(jobName, "source");
    }

    public static String resolveTargetPackage(String jobName) {
        return resolvePackage(jobName, "target");
    }

    public static boolean usesDerivedJobScopedPackage(String packageName) {
        return packageName != null && packageName.trim().startsWith(BASE_PACKAGE + ".");
    }

    public static boolean isDeprecatedBridgePackageDrift(String packageName, String derivedPackageName) {
        if (packageName == null || packageName.isBlank() || derivedPackageName == null || derivedPackageName.isBlank()) {
            return false;
        }
        String authoredPackage = packageName.trim();
        return usesDerivedJobScopedPackage(authoredPackage)
                && !authoredPackage.equals(derivedPackageName.trim());
    }

    public static String buildPackageDriftWarning(String configType,
                                                  String logicalName,
                                                  String jobName,
                                                  Path configPath,
                                                  String authoredPackageName,
                                                  String derivedPackageName) {
        return "Selected job '" + defaultValue(jobName, DEFAULT_JOB_NAME)
                + "' uses a deprecated explicit packageName bridge for " + defaultValue(configType, "config")
                + " '" + defaultValue(logicalName, "unnamed") + "' in " + defaultPath(configPath)
                + ": authored packageName='" + defaultValue(authoredPackageName, "")
                + "' differs from the derived package '" + defaultValue(derivedPackageName, "")
                + "'. The authored value is still honored for compatibility, but new and updated explicit job bundles should omit packageName so generated-model package derivation stays anchored to job-config.yaml name.";
    }

    public static String normalizeJobPackageSegment(String jobName) {
        if (jobName == null || jobName.isBlank()) {
            return DEFAULT_JOB_SEGMENT;
        }

        StringBuilder normalized = new StringBuilder();
        String trimmed = jobName.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            char current = trimmed.charAt(i);
            if (isAsciiLetterOrDigit(current)) {
                normalized.append(Character.toLowerCase(current));
            }
        }

        if (normalized.isEmpty()) {
            return DEFAULT_JOB_SEGMENT;
        }

        if (Character.isDigit(normalized.charAt(0))) {
            normalized.insert(0, "job");
        }

        return normalized.toString();
    }

    private static String resolvePackage(String jobName, String role) {
        String normalizedRole = role == null || role.isBlank() ? "model" : role.trim().toLowerCase();
        return BASE_PACKAGE + "." + normalizeJobPackageSegment(jobName) + "." + normalizedRole;
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String defaultPath(Path value) {
        return value == null ? "selected config" : value.toString();
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9');
    }
}



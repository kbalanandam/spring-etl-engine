package com.etl.common.util;

import com.etl.config.job.JobConfig;

import java.nio.file.Path;

/**
 * Resolves selected-job generated model packages from the explicit job identity.
 * <p>
 * On the active explicit-job path, source/target package names are derived internally from
 * {@code job-config.yaml -> name}. Authored source/target {@code packageName} values are no
 * longer part of that contract and now fail fast so runtime lookup and build-time generation
 * stay deterministic.
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

    public static boolean hasExplicitPackageName(String packageName) {
        return packageName != null && !packageName.isBlank();
    }

    public static void requireNoExplicitSelectedJobPackageName(String configType,
                                                               String logicalName,
                                                               String jobName,
                                                               Path configPath,
                                                               String authoredPackageName,
                                                               String derivedPackageName) {
        if (!hasExplicitPackageName(authoredPackageName)) {
            return;
        }

        throw new IllegalStateException(buildExplicitPackageNameNotAllowedMessage(
                configType,
                logicalName,
                jobName,
                configPath,
                authoredPackageName,
                derivedPackageName
        ));
    }

    public static String buildExplicitPackageNameNotAllowedMessage(String configType,
                                                                   String logicalName,
                                                                   String jobName,
                                                                   Path configPath,
                                                                   String authoredPackageName,
                                                                   String derivedPackageName) {
        return "Selected job '" + defaultValue(jobName, DEFAULT_JOB_NAME)
                + "' does not allow explicit packageName for " + defaultValue(configType, "config")
                + " '" + defaultValue(logicalName, "unnamed") + "' in " + defaultPath(configPath)
                + ": authored packageName='" + defaultValue(authoredPackageName, "")
                + "'. Remove packageName so the selected job derives generated package '"
                + defaultValue(derivedPackageName, "")
                + "' from job-config.yaml -> name.";
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



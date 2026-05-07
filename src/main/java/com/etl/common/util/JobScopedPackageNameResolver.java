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

    public static String resolveSourcePackage(String jobName) {
        return resolvePackage(jobName, "source");
    }

    public static String resolveTargetPackage(String jobName) {
        return resolvePackage(jobName, "target");
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

    private static boolean isAsciiLetterOrDigit(char value) {
        return (value >= 'a' && value <= 'z')
                || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9');
    }
}



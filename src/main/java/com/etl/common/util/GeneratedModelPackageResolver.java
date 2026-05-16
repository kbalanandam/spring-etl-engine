package com.etl.common.util;

import com.etl.config.source.SourceConfig;
import com.etl.config.source.XmlSourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.config.target.XmlTargetConfig;

/**
 * Centralizes generated-model package resolution for runtime class lookup and source generation.
 *
 * <p>This keeps package validation and error messaging in one place while bridge cleanup continues.
 * For now the resolver still reads the package cached onto config objects after loader/generation
 * defaulting, but callers no longer need to reach into those fields directly.</p>
 */
public final class GeneratedModelPackageResolver {

    private static final String EXPLICIT_JOB_PACKAGE_HINT = " In explicit job mode this package should be derived during ConfigLoader package defaulting before model resolution.";

    private GeneratedModelPackageResolver() {
    }

    public static String resolveSourcePackage(SourceConfig sourceConfig) {
        if (sourceConfig instanceof XmlSourceConfig xmlSourceConfig) {
            return validatedPackageName(xmlSourceConfig.getPackageName(), "XML source", xmlSourceConfig.getSourceName());
        }
        return validatedPackageName(sourceConfig.getPackageName(), "source", sourceConfig.getSourceName());
    }

    public static String resolveTargetPackage(TargetConfig targetConfig) {
        if (targetConfig instanceof XmlTargetConfig xmlTargetConfig) {
            return validatedPackageName(xmlTargetConfig.getPackageName(), "XML target", xmlTargetConfig.getTargetName());
        }
        return validatedPackageName(targetConfig.getPackageName(), "target", targetConfig.getTargetName());
    }

    public static String validatedPackageName(String value, String configType, String configName) {
        String trimmed = requirePackageName(value, configType, configName);
        if (!isQualifiedIdentifier(trimmed)) {
            throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName) + "' has invalid "
                    + "packageName='" + trimmed + "'. Expected a dot-separated Java package name." + EXPLICIT_JOB_PACKAGE_HINT);
        }
        return trimmed;
    }

    private static String requirePackageName(String value, String configType, String configName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(configType + " config '" + defaultConfigName(configName)
                    + "' must define a non-blank packageName before generated model class resolution."
                    + EXPLICIT_JOB_PACKAGE_HINT);
        }
        return value.trim();
    }

    private static String defaultConfigName(String configName) {
        return configName == null || configName.isBlank() ? "unnamed" : configName.trim();
    }

    private static boolean isQualifiedIdentifier(String value) {
        String[] segments = value.split("\\.");
        if (segments.length == 0) {
            return false;
        }
        for (String segment : segments) {
            if (!isJavaIdentifier(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isJavaIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}

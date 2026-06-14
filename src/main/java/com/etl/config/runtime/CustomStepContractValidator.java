package com.etl.config.runtime;

import com.etl.config.job.JobConfig;
import com.etl.exception.config.ConfigException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates and normalizes custom-step context and result metadata contracts.
 */
public final class CustomStepContractValidator {

    private static final Set<String> SUPPORTED_CONSUME_TYPES = Set.of(
            "string", "int", "long", "double", "decimal", "boolean", "object"
    );
    private static final Set<String> SUPPORTED_OUTCOME_ACTIONS = Set.of("CONTINUE", "STOP", "FAIL");

    public JobConfig.CustomStepConfig normalizeAndValidate(String stepName, JobConfig.CustomStepConfig configured) {
        JobConfig.CustomStepConfig normalized = new JobConfig.CustomStepConfig();
        normalized.setType(requireNonBlank(configured.getType(), stepName, "custom.type"));
        normalized.setPublish(normalizePublish(stepName, configured.getPublish()));
        normalized.setConsume(normalizeConsume(stepName, configured.getConsume()));
        normalized.setOnResult(normalizeOnResult(stepName, configured.getOnResult()));
        normalized.setConfig(configured.getConfig());
        return normalized;
    }

    private Map<String, String> normalizePublish(String stepName, Map<String, String> configuredPublish) {
        if (configuredPublish == null || configuredPublish.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuredPublish.entrySet()) {
            String publishName = requireNonBlank(entry.getKey(), stepName, "custom.publish key");
            String contextKey = requireNonBlank(entry.getValue(), stepName, "custom.publish['" + publishName + "']");
            requireNamespacedContextKey(contextKey, stepName, "custom.publish['" + publishName + "']");
            normalized.put(publishName, contextKey);
        }
        return Map.copyOf(normalized);
    }

    private Map<String, String> normalizeConsume(String stepName, Map<String, String> configuredConsume) {
        if (configuredConsume == null || configuredConsume.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuredConsume.entrySet()) {
            String consumeName = requireNonBlank(entry.getKey(), stepName, "custom.consume key");
            String consumeSpec = requireNonBlank(entry.getValue(), stepName, "custom.consume['" + consumeName + "']");

            String[] segments = consumeSpec.split(":", 2);
            String contextKey = segments[0].trim();
            requireNonBlank(contextKey, stepName, "custom.consume['" + consumeName + "'] context key");
            requireNamespacedContextKey(contextKey, stepName, "custom.consume['" + consumeName + "'] context key");

            if (segments.length == 2) {
                String typeToken = segments[1].trim().toLowerCase(Locale.ROOT);
                if (!SUPPORTED_CONSUME_TYPES.contains(typeToken)) {
                    throw new ConfigException("JobConfig step '" + stepName + "' custom.consume['" + consumeName + "'] uses unsupported type '"
                            + segments[1].trim() + "'. Supported types: " + String.join(", ", SUPPORTED_CONSUME_TYPES));
                }
                normalized.put(consumeName, contextKey + ":" + typeToken);
            } else {
                normalized.put(consumeName, contextKey);
            }
        }
        return Map.copyOf(normalized);
    }

    private Map<String, String> normalizeOnResult(String stepName, Map<String, String> configuredOnResult) {
        if (configuredOnResult == null || configuredOnResult.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : configuredOnResult.entrySet()) {
            String providerResult = requireNonBlank(entry.getKey(), stepName, "custom.onResult key");
            String actionToken = requireNonBlank(entry.getValue(), stepName, "custom.onResult['" + providerResult + "']");
            String normalizedAction = actionToken.trim().toUpperCase(Locale.ROOT);
            if (!SUPPORTED_OUTCOME_ACTIONS.contains(normalizedAction)) {
                throw new ConfigException("JobConfig step '" + stepName + "' custom.onResult['" + providerResult + "'] uses unsupported action '"
                        + actionToken + "'. Supported actions: " + String.join(", ", List.copyOf(SUPPORTED_OUTCOME_ACTIONS)));
            }
            normalized.put(providerResult, normalizedAction);
        }
        return Map.copyOf(normalized);
    }

    private static String requireNonBlank(String value, String stepName, String propertyPath) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("JobConfig step '" + stepName + "' is missing required property " + propertyPath + ".");
        }
        return value.trim();
    }

    private static void requireNamespacedContextKey(String contextKey, String stepName, String propertyPath) {
        if (!contextKey.contains(".")) {
            throw new ConfigException("JobConfig step '" + stepName + "' " + propertyPath
                    + " must use a namespaced context key (for example header.fileId). Received '" + contextKey + "'.");
        }
    }
}


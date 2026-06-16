package com.etl.config.runtime;

import com.etl.config.job.JobConfig;

import org.springframework.batch.item.ExecutionContext;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Enforces runtime context handoff contracts for custom steps.
 */
public final class CustomStepRuntimeContextGuard {

    private static final String OWNER_KEY_PREFIX = "custom.context.owner.";

    public void validateBeforeExecution(String stepName, JobConfig.CustomStepConfig customConfig, ExecutionContext executionContext) {
        if (customConfig == null || executionContext == null) {
            return;
        }
        validateConsumeContracts(stepName, customConfig.getConsume(), executionContext);
        validatePublishOwnership(stepName, customConfig.getPublish(), executionContext);
    }

    public void validateAfterExecution(String stepName, JobConfig.CustomStepConfig customConfig, ExecutionContext executionContext) {
        if (customConfig == null || executionContext == null || customConfig.getPublish() == null) {
            return;
        }
        for (Map.Entry<String, String> entry : customConfig.getPublish().entrySet()) {
            String publishName = entry.getKey();
            String contextKey = entry.getValue();
            if (!executionContext.containsKey(contextKey)) {
                throw new IllegalStateException("Custom step '" + stepName + "' declared custom.publish['" + publishName
                        + "'] -> '" + contextKey + "' but did not publish that key before step completion.");
            }
            executionContext.putString(ownerKey(contextKey), stepName);
        }
    }

    private void validateConsumeContracts(String stepName,
                                          Map<String, String> consume,
                                          ExecutionContext executionContext) {
        if (consume == null || consume.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : consume.entrySet()) {
            String consumeName = entry.getKey();
            String consumeSpec = entry.getValue();
            String[] segments = consumeSpec.split(":", 2);
            String contextKey = segments[0].trim();

            if (!executionContext.containsKey(contextKey)) {
                throw new IllegalStateException("Custom step '" + stepName + "' requires custom.consume['" + consumeName
                        + "'] key '" + contextKey + "' before execution, but it is missing from execution context.");
            }

            if (segments.length == 2) {
                String expectedType = segments[1].trim();
                Object value = executionContext.get(contextKey);
                if (!isCompatibleType(value, expectedType)) {
                    String actualType = value == null ? "null" : value.getClass().getSimpleName();
                    throw new IllegalStateException("Custom step '" + stepName + "' requires custom.consume['" + consumeName
                            + "'] key '" + contextKey + "' with type '" + expectedType + "' but found '" + actualType + "'.");
                }
            }
        }
    }

    private void validatePublishOwnership(String stepName,
                                          Map<String, String> publish,
                                          ExecutionContext executionContext) {
        if (publish == null || publish.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : publish.entrySet()) {
            String contextKey = entry.getValue();
            if (!executionContext.containsKey(contextKey)) {
                continue;
            }
            String owner = executionContext.getString(ownerKey(contextKey), "");
            if (owner.isBlank()) {
                throw new IllegalStateException("Custom step '" + stepName + "' attempted to publish context key '" + contextKey
                        + "' that already exists without ownership metadata. Overwrite is not allowed in this slice.");
            }
            throw new IllegalStateException("Custom step '" + stepName + "' attempted to publish context key '" + contextKey
                    + "' already owned by step '" + owner + "'. Overwrite is not allowed in this slice.");
        }
    }

    private static boolean isCompatibleType(Object value, String expectedType) {
        if (value == null) {
            return false;
        }
        return switch (expectedType) {
            case "string" -> value instanceof CharSequence;
            case "int", "long", "double" -> value instanceof Number;
            case "decimal" -> value instanceof BigDecimal || value instanceof Number;
            case "boolean" -> value instanceof Boolean;
            case "object" -> true;
            default -> false;
        };
    }

    private static String ownerKey(String contextKey) {
        return OWNER_KEY_PREFIX + contextKey;
    }
}


package com.etl.processor.spi;

import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.runtime.FileIngestionRuntimeSupport;

import java.util.List;

/**
 * Supplies processor validation-rule and transform extensions.
 */
public interface ProcessorExtensionProvider {

    /**
     * Stable provider id used for diagnostics and deterministic ordering.
     */
    String providerId();

    /**
     * Lower values load first when multiple providers are discovered.
     */
    default int order() {
        return 0;
    }

    /**
     * Explicitly marks this provider as an override source for conflicting keys.
     */
    default boolean isOverride() {
        return false;
    }

    default List<ProcessorValidationRule> validationRules(FileIngestionRuntimeSupport runtimeSupport) {
        return List.of();
    }

    default List<ProcessorFieldTransform> transforms() {
        return List.of();
    }
}


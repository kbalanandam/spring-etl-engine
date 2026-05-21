package com.etl.processor;

import com.etl.processor.transform.ConditionalProcessorTransform;
import com.etl.processor.transform.ExpressionProcessorTransform;
import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.transform.ValueMapProcessorTransform;
import com.etl.processor.validation.DuplicateProcessorValidationRule;
import com.etl.processor.validation.NotNullProcessorValidationRule;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.validation.TimeFormatProcessorValidationRule;
import com.etl.processor.validation.XmlDuplicateProcessorValidationRule;
import com.etl.runtime.FileIngestionRuntimeSupport;

import java.util.List;

/**
 * Built-in extension registrations for non-Spring/manual construction paths.
 */
public final class ProcessorExtensionDefaults {

    private ProcessorExtensionDefaults() {
    }

    public static List<ProcessorValidationRule> defaultValidationRules(FileIngestionRuntimeSupport runtimeSupport) {
        return List.of(
                new NotNullProcessorValidationRule(),
                new TimeFormatProcessorValidationRule(),
                new XmlDuplicateProcessorValidationRule(runtimeSupport),
                new DuplicateProcessorValidationRule(runtimeSupport)
        );
    }

    public static List<ProcessorFieldTransform> defaultTransforms() {
        return List.of(
                new ValueMapProcessorTransform(),
                new ExpressionProcessorTransform(),
                new ConditionalProcessorTransform()
        );
    }
}


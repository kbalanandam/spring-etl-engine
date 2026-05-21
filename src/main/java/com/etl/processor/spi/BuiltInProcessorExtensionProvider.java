package com.etl.processor.spi;

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
 * Built-in processor extensions shipped with the engine.
 */
public final class BuiltInProcessorExtensionProvider implements ProcessorExtensionProvider {

    @Override
    public String providerId() {
        return "builtin";
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public List<ProcessorValidationRule> validationRules(FileIngestionRuntimeSupport runtimeSupport) {
        return List.of(
                new NotNullProcessorValidationRule(),
                new TimeFormatProcessorValidationRule(),
                new XmlDuplicateProcessorValidationRule(runtimeSupport),
                new DuplicateProcessorValidationRule(runtimeSupport)
        );
    }

    @Override
    public List<ProcessorFieldTransform> transforms() {
        return List.of(
                new ValueMapProcessorTransform(),
                new ExpressionProcessorTransform(),
                new ConditionalProcessorTransform()
        );
    }
}

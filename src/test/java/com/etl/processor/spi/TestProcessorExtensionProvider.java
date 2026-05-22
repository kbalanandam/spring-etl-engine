package com.etl.processor.spi;

import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.validation.ValidationIssue;
import com.etl.runtime.FileIngestionRuntimeSupport;

import java.util.List;

public class TestProcessorExtensionProvider implements ProcessorExtensionProvider {

    @Override
    public String providerId() {
        return "test-provider";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public List<ProcessorValidationRule> validationRules(FileIngestionRuntimeSupport runtimeSupport) {
        return List.of(new TestValidationRule());
    }

    @Override
    public List<ProcessorFieldTransform> transforms() {
        return List.of(new TestTransform());
    }

    private static final class TestValidationRule implements ProcessorValidationRule {
        @Override
        public String getRuleType() {
            return "test-rule";
        }

        @Override
        public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
            return null;
        }
    }

    private static final class TestTransform implements ProcessorFieldTransform {
        @Override
        public String getTransformType() {
            return "test-transform";
        }

        @Override
        public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
            return value;
        }
    }
}

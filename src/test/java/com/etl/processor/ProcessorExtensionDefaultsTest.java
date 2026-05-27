package com.etl.processor;

import com.etl.processor.transform.ProcessorFieldTransform;
import com.etl.processor.validation.ProcessorValidationRule;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.spi.ProcessorExtensionProvider;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorExtensionDefaultsTest {

    @Test
    void defaultValidationRules_includeBuiltInAndDiscoveredProviders() {
        Set<String> ruleTypes = ProcessorExtensionDefaults
                .defaultValidationRules(new FileIngestionRuntimeSupport())
                .stream()
                .map(ProcessorValidationRule::getRuleType)
                .collect(Collectors.toSet());

        assertTrue(ruleTypes.contains("notNull"));
        assertTrue(ruleTypes.contains("duplicate"));
        assertTrue(ruleTypes.contains("test-rule"));
    }

    @Test
    void defaultTransforms_includeBuiltInAndDiscoveredProviders() {
        Set<String> transformTypes = ProcessorExtensionDefaults
                .defaultTransforms()
                .stream()
                .map(ProcessorFieldTransform::getTransformType)
                .collect(Collectors.toSet());

        assertTrue(transformTypes.contains("valueMap"));
        assertTrue(transformTypes.contains("expression"));
        assertTrue(transformTypes.contains("conditional"));
        assertTrue(transformTypes.contains("zoneConvert"));
        assertTrue(transformTypes.contains("test-transform"));
    }

    @Test
    void resolveValidationRules_failsWhenTwoOverrideProvidersClaimSameRuleType() {
        ProcessorExtensionProvider firstOverride = new ProcessorExtensionProvider() {
            @Override
            public String providerId() {
                return "override-1";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<ProcessorValidationRule> validationRules(FileIngestionRuntimeSupport runtimeSupport) {
                return List.of(new ProcessorValidationRule() {
                    @Override
                    public String getRuleType() {
                        return "collisionRule";
                    }

                    @Override
                    public ValidationIssue evaluate(String fieldName, Object value, com.etl.config.processor.ProcessorConfig.FieldRule rule) {
                        return null;
                    }
                });
            }
        };

        ProcessorExtensionProvider secondOverride = new ProcessorExtensionProvider() {
            @Override
            public String providerId() {
                return "override-2";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<ProcessorValidationRule> validationRules(FileIngestionRuntimeSupport runtimeSupport) {
                return List.of(new ProcessorValidationRule() {
                    @Override
                    public String getRuleType() {
                        return "collisionRule";
                    }

                    @Override
                    public ValidationIssue evaluate(String fieldName, Object value, com.etl.config.processor.ProcessorConfig.FieldRule rule) {
                        return null;
                    }
                });
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ProcessorExtensionDefaults.resolveValidationRules(List.of(firstOverride, secondOverride), new FileIngestionRuntimeSupport()));
        assertTrue(failure.getMessage().contains("Multiple processor validation rules registered for type 'collisionRule'"));
        assertTrue(failure.getMessage().contains("override-1"));
        assertTrue(failure.getMessage().contains("override-2"));
    }

    @Test
    void resolveTransforms_failsWhenTwoOverrideProvidersClaimSameTransformType() {
        ProcessorExtensionProvider firstOverride = new ProcessorExtensionProvider() {
            @Override
            public String providerId() {
                return "override-1";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<ProcessorFieldTransform> transforms() {
                return List.of(new ProcessorFieldTransform() {
                    @Override
                    public String getTransformType() {
                        return "collisionTransform";
                    }

                    @Override
                    public Object apply(Object value, com.etl.config.processor.ProcessorConfig.FieldTransform transform) {
                        return value;
                    }
                });
            }
        };

        ProcessorExtensionProvider secondOverride = new ProcessorExtensionProvider() {
            @Override
            public String providerId() {
                return "override-2";
            }

            @Override
            public boolean isOverride() {
                return true;
            }

            @Override
            public List<ProcessorFieldTransform> transforms() {
                return List.of(new ProcessorFieldTransform() {
                    @Override
                    public String getTransformType() {
                        return "collisionTransform";
                    }

                    @Override
                    public Object apply(Object value, com.etl.config.processor.ProcessorConfig.FieldTransform transform) {
                        return value;
                    }
                });
            }
        };

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> ProcessorExtensionDefaults.resolveTransforms(List.of(firstOverride, secondOverride)));
        assertTrue(failure.getMessage().contains("Multiple processor transforms registered for type 'collisionTransform'"));
        assertTrue(failure.getMessage().contains("override-1"));
        assertTrue(failure.getMessage().contains("override-2"));
    }
}


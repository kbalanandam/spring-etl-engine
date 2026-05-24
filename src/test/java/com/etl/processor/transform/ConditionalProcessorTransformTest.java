package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionalProcessorTransformTest {

    @Test
    void reusesCachedExpressionForRepeatedConditions() throws Exception {
        ConditionalProcessorTransform transform = new ConditionalProcessorTransform();

        ProcessorConfig.FieldTransform conditional = conditionalTransform("#value == 'A'", "ACTIVE", "OTHER");
        assertEquals("ACTIVE", transform.apply("A", conditional));
        assertEquals("OTHER", transform.apply("P", conditional));

        assertEquals(1, expressionCacheSize(transform));
    }

    @Test
    void keepsConditionalExpressionCacheBounded() throws Exception {
        ConditionalProcessorTransform transform = new ConditionalProcessorTransform();

        for (int i = 0; i < 1100; i++) {
            String when = "#value == '" + i + "'";
            ProcessorConfig.FieldTransform conditional = conditionalTransform(when, "MATCH", "MISS");
            transform.apply("x", conditional);
        }

        assertTrue(expressionCacheSize(transform) <= 1024,
                "Conditional SpEL cache should stay bounded to prevent unbounded memory growth");
    }

    @Test
    void throwsDedicatedEvaluationExceptionForInvalidRuntimeExpression() {
        ConditionalProcessorTransform transform = new ConditionalProcessorTransform();

        ProcessorConfig.FieldTransform conditional = conditionalTransform("#value.unknownMethod()", "ACTIVE", "OTHER");
        ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
        fieldMapping.setTo("status");
        ProcessorTransformContext context = new ProcessorTransformContext(Map.of("status", "A"), null, fieldMapping, Map.of());

        ProcessorTransformEvaluationException exception = assertThrows(ProcessorTransformEvaluationException.class,
                () -> transform.apply("A", conditional, context));

        assertTrue(exception.getMessage().contains("status"));
    }

    private ProcessorConfig.FieldTransform conditionalTransform(String when, Object then, Object defaultValue) {
        ProcessorConfig.ConditionalCase conditionalCase = new ProcessorConfig.ConditionalCase();
        conditionalCase.setWhen(when);
        conditionalCase.setThen(then);

        ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
        transform.setType("conditional");
        transform.setCases(List.of(conditionalCase));
        transform.setDefaultValue(defaultValue);
        return transform;
    }

    @SuppressWarnings("unchecked")
    private int expressionCacheSize(ConditionalProcessorTransform transform) throws Exception {
        Field cacheField = ConditionalProcessorTransform.class.getDeclaredField("expressionCache");
        cacheField.setAccessible(true);
        Map<String, ?> cache = (Map<String, ?>) cacheField.get(transform);
        return cache.size();
    }
}




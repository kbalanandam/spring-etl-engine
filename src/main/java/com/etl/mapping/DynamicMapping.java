package com.etl.mapping;

import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;
import com.etl.processor.ProcessorExtensionDefaults;
import com.etl.processor.transform.TransformEvaluator;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

/**
 * Maps one input record into a generated target model using the configured processor field map.
 *
 * <p><strong>Transition status:</strong> BRIDGE.</p>
 *
 * <p>This is the lightweight mapping path used when a step has transforms but no active processor
 * validation/reject handling requirements. Field extraction, transform application, and output
 * population are delegated to {@link MappedFieldValueResolver} so this class stays a thin Spring
 * Batch adapter.</p>
 *
 * @param <I> runtime input type
 * @param <O> generated target output type
 */
public class DynamicMapping<I, O> implements ItemProcessor<I, O> {

    private final ProcessorConfig.EntityMapping mapping;
    private final Class<O> targetClass;
    private final MappedFieldValueResolver mappedFieldValueResolver;

    public DynamicMapping(ProcessorConfig.EntityMapping mapping, Class<O> targetClass) {
        this(mapping, targetClass, new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms()));
    }

    public DynamicMapping(ProcessorConfig.EntityMapping mapping, Class<O> targetClass, TransformEvaluator transformEvaluator) {
        this(mapping, targetClass, transformEvaluator, null);
    }

    public DynamicMapping(ProcessorConfig.EntityMapping mapping,
                          Class<O> targetClass,
                          TransformEvaluator transformEvaluator,
                          ModelFormat sourceFormat) {
        this.mapping = mapping;
        this.targetClass = targetClass;
        this.mappedFieldValueResolver = new MappedFieldValueResolver(transformEvaluator, sourceFormat);
    }

    @Override
    public O process(@NonNull I input) throws Exception {
        return mappedFieldValueResolver.createOutput(targetClass, mapping, mappedFieldValueResolver.resolve(input, mapping));
    }
}
package com.etl.mapping;

import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.TransformEvaluator;
import org.springframework.batch.item.ItemProcessor;
/**
 * DynamicMapping is a generic ItemProcessor that performs dynamic field
 * mapping between input and output objects based on the provided mapping
 * configuration.
 *
 * <p>
 * It uses reflection to read field values from the input object and set them
 * on the output object according to the specified field mappings.
 * </p>
 *
 * @param <I> The type of the input object.
 * @param <O> The type of the output object.
 */
public class DynamicMapping<I, O> implements ItemProcessor<I, O> {

    private final ProcessorConfig.EntityMapping mapping;
    private final Class<O> targetClass;
    private final MappedFieldValueResolver mappedFieldValueResolver;

    public DynamicMapping(ProcessorConfig.EntityMapping mapping, Class<O> targetClass) {
        this(mapping, targetClass, new TransformEvaluator());
    }

    public DynamicMapping(ProcessorConfig.EntityMapping mapping, Class<O> targetClass, TransformEvaluator transformEvaluator) {
        this.mapping = mapping;
        this.targetClass = targetClass;
        this.mappedFieldValueResolver = new MappedFieldValueResolver(transformEvaluator);
    }

    @Override
    public O process(I input) throws Exception {
        return mappedFieldValueResolver.createOutput(targetClass, mapping, mappedFieldValueResolver.resolve(input, mapping));
    }
}
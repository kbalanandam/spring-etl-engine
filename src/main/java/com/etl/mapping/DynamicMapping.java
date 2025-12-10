package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
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

    public DynamicMapping(ProcessorConfig.EntityMapping mapping, Class<O> targetClass) {
        this.mapping = mapping;
        this.targetClass = targetClass;
    }

    @Override
    public O process(I input) throws Exception {
        O output = ReflectionUtils.createInstance(targetClass);

        for (ProcessorConfig.FieldMapping fm : mapping.getFields()) {

            Object value = ReflectionUtils.getFieldValue(input, fm.getFrom());
            ReflectionUtils.setFieldValue(output, fm.getTo(), value);
        }

        return output;
    }
}
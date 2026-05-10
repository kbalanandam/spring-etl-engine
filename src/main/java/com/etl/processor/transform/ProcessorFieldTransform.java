package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;

public interface ProcessorFieldTransform {

	String getTransformType();

	default void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
	}

	default Object apply(Object value,
	                  ProcessorConfig.FieldTransform transform,
	                  ProcessorTransformContext context) {
		return apply(value, transform);
	}

	Object apply(Object value, ProcessorConfig.FieldTransform transform);
}


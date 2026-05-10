package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;

import java.util.Map;

public record ProcessorTransformContext(
		Object input,
		ProcessorConfig.EntityMapping entityMapping,
		ProcessorConfig.FieldMapping fieldMapping,
		Map<String, Object> resolvedValues
) {
}


package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;

import java.util.Map;

/**
 * Runtime context supplied to processor transforms while one mapping is being resolved.
 *
 * <p>The context exposes the original input record, the active entity/field mapping metadata, and
 * an immutable snapshot of values already resolved earlier in the same mapping pass so transforms
 * can perform context-aware normalization without mutating shared processor state.</p>
 */
public record ProcessorTransformContext(
		Object input,
		ProcessorConfig.EntityMapping entityMapping,
		ProcessorConfig.FieldMapping fieldMapping,
		Map<String, Object> resolvedValues
) {
}


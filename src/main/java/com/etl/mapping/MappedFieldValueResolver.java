package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.TransformEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

public class MappedFieldValueResolver {

	private final TransformEvaluator transformEvaluator;

	public MappedFieldValueResolver(TransformEvaluator transformEvaluator) {
		this.transformEvaluator = transformEvaluator;
	}

	public Map<String, Object> resolve(Object input, ProcessorConfig.EntityMapping mapping) {
		Map<String, Object> resolvedValues = new LinkedHashMap<>();
		if (mapping == null || mapping.getFields() == null) {
			return resolvedValues;
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			String fromField = normalize(fieldMapping.getFrom());
			String toField = normalize(fieldMapping.getTo());
			Object value = fromField == null ? null : ReflectionUtils.getFieldValue(input, fromField);
			value = transformEvaluator.apply(value, mapping, fieldMapping, input, resolvedValues);
			if (fromField != null) {
				resolvedValues.put(fromField, value);
			}
			if (toField != null) {
				resolvedValues.put(toField, value);
			}
		}
		return resolvedValues;
	}

	public <O> O createOutput(Class<O> targetClass,
	                         ProcessorConfig.EntityMapping mapping,
	                         Map<String, Object> resolvedValues) {
		O output = ReflectionUtils.createInstance(targetClass);
		populateOutput(output, mapping, resolvedValues);
		return output;
	}

	public void populateOutput(Object output,
	                         ProcessorConfig.EntityMapping mapping,
	                         Map<String, Object> resolvedValues) {
		if (mapping == null || mapping.getFields() == null) {
			return;
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			ReflectionUtils.setFieldValue(output, fieldMapping.getTo(), resolveFieldValue(fieldMapping, resolvedValues));
		}
	}

	private Object resolveFieldValue(ProcessorConfig.FieldMapping fieldMapping, Map<String, Object> resolvedValues) {
		String toField = normalize(fieldMapping.getTo());
		if (toField != null && resolvedValues.containsKey(toField)) {
			return resolvedValues.get(toField);
		}
		String fromField = normalize(fieldMapping.getFrom());
		return fromField == null ? null : resolvedValues.get(fromField);
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}


package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.TransformEvaluator;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves mapped field values and populates generated output models for processor mappings.
 *
 * <p>The resolver is shared by both plain and validation-aware mapping paths so field extraction,
 * transform execution, and output population all follow the same authored mapping order.</p>
 */
public class MappedFieldValueResolver {

	private final TransformEvaluator transformEvaluator;

	public MappedFieldValueResolver(TransformEvaluator transformEvaluator) {
		this.transformEvaluator = transformEvaluator;
	}

	/**
	 * Reads source fields, applies configured transforms, and returns the intermediate resolved-value
	 * map keyed by both source and target field names when available.
	 */
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

	/**
	 * Instantiates the generated target model and populates it from the resolved mapping values.
	 */
	public <O> O createOutput(Class<O> targetClass,
	                         ProcessorConfig.EntityMapping mapping,
	                         Map<String, Object> resolvedValues) {
		O output = ReflectionUtils.createInstance(targetClass);
		populateOutput(output, mapping, resolvedValues);
		return output;
	}

	/**
	 * Writes resolved values into an existing output object using the configured target field names.
	 */
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


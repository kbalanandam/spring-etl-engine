package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;
import com.etl.exception.EtlException;
import com.etl.exception.TransformationException;
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
	private final ModelFormat sourceFormat;

	public MappedFieldValueResolver(TransformEvaluator transformEvaluator) {
		this(transformEvaluator, null);
	}

	public MappedFieldValueResolver(TransformEvaluator transformEvaluator, ModelFormat sourceFormat) {
		this.transformEvaluator = transformEvaluator;
		this.sourceFormat = sourceFormat;
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
			Object value = readSourceValue(input, mapping, fieldMapping, fromField);
			value = applyTransforms(value, mapping, fieldMapping, input, resolvedValues);
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
		O output = createTargetInstance(targetClass, mapping);
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
			writeTargetValue(output, mapping, fieldMapping, resolvedValues);
		}
	}

	private Object readSourceValue(Object input,
	                             ProcessorConfig.EntityMapping mapping,
	                             ProcessorConfig.FieldMapping fieldMapping,
	                             String fromField) {
		if (fromField == null) {
			return null;
		}
		try {
			return ReflectionUtils.getFieldValue(input, fromField);
		} catch (EtlException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new TransformationException("Failed to read source field '" + fromField + "' for mapping '"
					+ mappingName(mapping) + "' while preparing processor output field '" + displayField(fieldMapping) + "'.", e);
		}
	}

	private Object applyTransforms(Object value,
	                             ProcessorConfig.EntityMapping mapping,
	                             ProcessorConfig.FieldMapping fieldMapping,
	                             Object input,
	                             Map<String, Object> resolvedValues) {
		try {
			return transformEvaluator.apply(value, mapping, fieldMapping, input, resolvedValues, sourceFormat);
		} catch (EtlException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new TransformationException("Failed to apply processor transforms for mapping '" + mappingName(mapping)
					+ "' field '" + displayField(fieldMapping) + "'.", e);
		}
	}

	private <O> O createTargetInstance(Class<O> targetClass, ProcessorConfig.EntityMapping mapping) {
		try {
			return ReflectionUtils.createInstance(targetClass);
		} catch (EtlException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new TransformationException("Failed to create target output for mapping '" + mappingName(mapping) + "'.", e);
		}
	}

	private void writeTargetValue(Object output,
	                           ProcessorConfig.EntityMapping mapping,
	                           ProcessorConfig.FieldMapping fieldMapping,
	                           Map<String, Object> resolvedValues) {
		String targetField = fieldMapping == null ? null : fieldMapping.getTo();
		try {
			ReflectionUtils.setFieldValue(output, targetField, resolveFieldValue(fieldMapping, resolvedValues));
		} catch (EtlException e) {
			throw e;
		} catch (RuntimeException e) {
			throw new TransformationException("Failed to populate target field '" + displayField(fieldMapping)
					+ "' for mapping '" + mappingName(mapping) + "'.", e);
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

	private String mappingName(ProcessorConfig.EntityMapping mapping) {
		if (mapping == null) {
			return "unknown -> unknown";
		}
		return String.valueOf(mapping.getSource()) + " -> " + String.valueOf(mapping.getTarget());
	}

	private String displayField(ProcessorConfig.FieldMapping fieldMapping) {
		if (fieldMapping == null) {
			return "unknown";
		}
		String targetField = normalize(fieldMapping.getTo());
		if (targetField != null) {
			return targetField;
		}
		String sourceField = normalize(fieldMapping.getFrom());
		return sourceField == null ? "unknown" : sourceField;
	}
}


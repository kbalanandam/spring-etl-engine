package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;

@Component
public class TransformEvaluator {

	private final Map<String, ProcessorFieldTransform> transformsByType;

	public TransformEvaluator() {
		this(defaultTransforms());
	}

	@Autowired
	public TransformEvaluator(List<ProcessorFieldTransform> transforms) {
		Map<String, ProcessorFieldTransform> indexedTransforms = new LinkedHashMap<>();
		for (ProcessorFieldTransform transform : transforms == null ? List.<ProcessorFieldTransform>of() : transforms) {
			String normalizedType = normalizeType(transform.getTransformType());
			ProcessorFieldTransform previous = indexedTransforms.putIfAbsent(normalizedType, transform);
			if (previous != null) {
				throw new IllegalStateException("Duplicate processor transform type registration: " + normalizedType);
			}
		}
		this.transformsByType = Map.copyOf(indexedTransforms);
	}

	public Object apply(Object value, ProcessorConfig.FieldMapping fieldMapping) {
		return apply(value, null, fieldMapping, null, Map.of());
	}

	public Object apply(Object value,
	                 ProcessorConfig.EntityMapping entityMapping,
	                 ProcessorConfig.FieldMapping fieldMapping,
	                 Object input,
	                 Map<String, Object> resolvedValues) {
		if (fieldMapping == null || fieldMapping.getTransforms() == null || fieldMapping.getTransforms().isEmpty()) {
			return value;
		}

		Map<String, Object> resolvedSnapshot = resolvedValues == null || resolvedValues.isEmpty()
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(resolvedValues));
		ProcessorTransformContext context = new ProcessorTransformContext(input, entityMapping, fieldMapping, resolvedSnapshot);
		Object transformedValue = value;
		for (ProcessorConfig.FieldTransform transform : fieldMapping.getTransforms()) {
			transformedValue = resolveTransform(transform == null ? null : transform.getType())
					.apply(transformedValue, transform, context);
		}
		return transformedValue;
	}

	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldTransform transform) {
		if (transform == null || transform.getType() == null || transform.getType().isBlank()) {
			throw new IllegalStateException("FieldMapping transform missing 'type' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}

		resolveTransform(transform.getType()).validateConfiguration(entityMapping, fieldMapping, transform);
	}

	private ProcessorFieldTransform resolveTransform(String transformType) {
		String normalizedType = normalizeType(transformType);
		ProcessorFieldTransform transform = transformsByType.get(normalizedType);
		if (transform == null) {
			throw new IllegalArgumentException("Unsupported processor transform type: " + normalizedType);
		}
		return transform;
	}

	private String normalizeType(String transformType) {
		if (transformType == null || transformType.isBlank()) {
			throw new IllegalArgumentException("Processor transform type must not be blank.");
		}
		return transformType.trim();
	}

	private static List<ProcessorFieldTransform> defaultTransforms() {
		return List.of(new ValueMapProcessorTransform(), new ExpressionProcessorTransform());
	}
}


package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Evaluates configured processor transforms for one mapped field value.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This class is the active transform SPI dispatcher. It keeps transform registration strict,
 * executes transforms in authored order, and provides a contextual view of already-resolved field
 * values so multi-field normalization stays deterministic within a single record.</p>
 */
@Component
public class TransformEvaluator {

	private final Map<String, ProcessorFieldTransform> transformsByType;
	private final Map<TransformDispatchKey, ProcessorFieldTransform> transformsByTypeAndFormat;

	@Autowired
	public TransformEvaluator(List<ProcessorFieldTransform> transforms) {
		List<ExtensionConflictPolicy.Candidate<String, ProcessorFieldTransform>> globalTransformCandidates = new ArrayList<>();
		List<ExtensionConflictPolicy.Candidate<TransformDispatchKey, ProcessorFieldTransform>> scopedTransformCandidates = new ArrayList<>();
		for (ProcessorFieldTransform transform : transforms == null ? List.<ProcessorFieldTransform>of() : transforms) {
			String normalizedType = normalizeType(transform.getTransformType());
			Set<ModelFormat> scopedFormats = normalizedFormats(transform.supportedSourceFormats());
			if (scopedFormats.isEmpty()) {
				globalTransformCandidates.add(new ExtensionConflictPolicy.Candidate<>(
						normalizedType,
						transform,
						providerMetadata(transform)
				));
				continue;
			}

			for (ModelFormat scopedFormat : scopedFormats) {
				scopedTransformCandidates.add(new ExtensionConflictPolicy.Candidate<>(
						new TransformDispatchKey(normalizedType, scopedFormat),
						transform,
						providerMetadata(transform)
				));
			}
		}
		this.transformsByType = ExtensionConflictPolicy.resolve(globalTransformCandidates, globalConflictReporter());
		this.transformsByTypeAndFormat = ExtensionConflictPolicy.resolve(scopedTransformCandidates, scopedConflictReporter());
	}

	private ExtensionConflictPolicy.ConflictReporter<String> globalConflictReporter() {
		return new ExtensionConflictPolicy.ConflictReporter<>() {
			@Override
			public void onOverride(String key,
			                       ExtensionConflictPolicy.ProviderMetadata winner,
			                       ExtensionConflictPolicy.ProviderMetadata replaced) {
			}

			@Override
			public void onIgnored(String key,
			                      ExtensionConflictPolicy.ProviderMetadata ignored,
			                      ExtensionConflictPolicy.ProviderMetadata winner) {
			}

			@Override
			public RuntimeException duplicateFailure(String key,
			                                         ExtensionConflictPolicy.ProviderMetadata existing,
			                                         ExtensionConflictPolicy.ProviderMetadata candidate) {
				return new IllegalStateException("Duplicate processor transform type registration: " + key
						+ " (extensions: " + existing.providerId() + ", " + candidate.providerId() + ")"
						+ ". Set exactly one extension with isOverride=true to replace an existing transform.");
			}
		};
	}

	private ExtensionConflictPolicy.ConflictReporter<TransformDispatchKey> scopedConflictReporter() {
		return new ExtensionConflictPolicy.ConflictReporter<>() {
			@Override
			public void onOverride(TransformDispatchKey key,
			                       ExtensionConflictPolicy.ProviderMetadata winner,
			                       ExtensionConflictPolicy.ProviderMetadata replaced) {
			}

			@Override
			public void onIgnored(TransformDispatchKey key,
			                      ExtensionConflictPolicy.ProviderMetadata ignored,
			                      ExtensionConflictPolicy.ProviderMetadata winner) {
			}

			@Override
			public RuntimeException duplicateFailure(TransformDispatchKey key,
			                                         ExtensionConflictPolicy.ProviderMetadata existing,
			                                         ExtensionConflictPolicy.ProviderMetadata candidate) {
				return new IllegalStateException("Duplicate processor transform registration for type '"
						+ key.transformType() + "' and source format '" + key.sourceFormat().getFormat() + "'"
						+ " (extensions: " + existing.providerId() + ", " + candidate.providerId() + ")"
						+ ". Set exactly one extension with isOverride=true to replace an existing transform.");
			}
		};
	}

	private ExtensionConflictPolicy.ProviderMetadata providerMetadata(ProcessorFieldTransform transform) {
		return new ExtensionConflictPolicy.ProviderMetadata(transform.extensionId(), transform.isOverride());
	}

	/**
	 * Convenience overload used by legacy callers that only need field-level transforms.
	 */
	public Object apply(Object value, ProcessorConfig.FieldMapping fieldMapping) {
		return apply(value, null, fieldMapping, null, Map.of(), null);
	}

	public Object apply(Object value,
	                 ProcessorConfig.FieldMapping fieldMapping,
	                 ModelFormat sourceFormat) {
		return apply(value, null, fieldMapping, null, Map.of(), sourceFormat);
	}

	/**
	 * Applies the configured transforms in declared order for the current mapped field.
	 */
	public Object apply(Object value,
	                 ProcessorConfig.EntityMapping entityMapping,
	                 ProcessorConfig.FieldMapping fieldMapping,
	                 Object input,
	                 Map<String, Object> resolvedValues) {
		return apply(value, entityMapping, fieldMapping, input, resolvedValues, null);
	}

	public Object apply(Object value,
	                 ProcessorConfig.EntityMapping entityMapping,
	                 ProcessorConfig.FieldMapping fieldMapping,
	                 Object input,
	                 Map<String, Object> resolvedValues,
	                 ModelFormat sourceFormat) {
		if (fieldMapping == null || fieldMapping.getTransforms() == null || fieldMapping.getTransforms().isEmpty()) {
			return value;
		}

		Map<String, Object> resolvedSnapshot = resolvedValues == null || resolvedValues.isEmpty()
				? Map.of()
				: Collections.unmodifiableMap(new LinkedHashMap<>(resolvedValues));
		ProcessorTransformContext context = new ProcessorTransformContext(input, entityMapping, fieldMapping, resolvedSnapshot);
		Object transformedValue = value;
		for (ProcessorConfig.FieldTransform transform : fieldMapping.getTransforms()) {
			transformedValue = resolveTransform(transform == null ? null : transform.getType(), sourceFormat)
					.apply(transformedValue, transform, context);
		}
		return transformedValue;
	}

	/**
	 * Validates one transform declaration against the active entity mapping before runtime.
	 */
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldTransform transform) {
		validateConfiguration(entityMapping, fieldMapping, transform, null);
	}

	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldTransform transform,
	                               ModelFormat sourceFormat) {
		if (transform == null || transform.getType() == null || transform.getType().isBlank()) {
			throw new IllegalStateException("FieldMapping transform missing 'type' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}

		resolveTransform(transform.getType(), sourceFormat).validateConfiguration(entityMapping, fieldMapping, transform);
	}

	private ProcessorFieldTransform resolveTransform(String transformType, ModelFormat sourceFormat) {
		String normalizedType = normalizeType(transformType);
		if (sourceFormat != null) {
			ProcessorFieldTransform scopedTransform = transformsByTypeAndFormat.get(new TransformDispatchKey(normalizedType, sourceFormat));
			if (scopedTransform != null) {
				return scopedTransform;
			}
		}

		ProcessorFieldTransform transform = transformsByType.get(normalizedType);
		if (transform != null) {
			return transform;
		}

		if (sourceFormat == null) {
			ProcessorFieldTransform uniqueScopedTransform = resolveUniqueScopedTransform(normalizedType);
			if (uniqueScopedTransform != null) {
				return uniqueScopedTransform;
			}
		}

		if (sourceFormat != null) {
			throw new IllegalArgumentException("Unsupported processor transform type: " + normalizedType
					+ " for source format " + sourceFormat.getFormat());
		}
		throw new IllegalArgumentException("Unsupported processor transform type: " + normalizedType);
	}

	private ProcessorFieldTransform resolveUniqueScopedTransform(String normalizedType) {
		Set<ProcessorFieldTransform> matches = new HashSet<>();
		for (Map.Entry<TransformDispatchKey, ProcessorFieldTransform> entry : transformsByTypeAndFormat.entrySet()) {
			if (entry.getKey().transformType().equals(normalizedType)) {
				matches.add(entry.getValue());
			}
		}
		if (matches.size() == 1) {
			return matches.iterator().next();
		}
		if (matches.size() > 1) {
			throw new IllegalStateException("Processor transform type '" + normalizedType
					+ "' has multiple source-format registrations and requires source-format context.");
		}
		return null;
	}

	private String normalizeType(String transformType) {
		if (transformType == null || transformType.isBlank()) {
			throw new IllegalArgumentException("Processor transform type must not be blank.");
		}
		return transformType.trim();
	}

	private Set<ModelFormat> normalizedFormats(Set<ModelFormat> formats) {
		if (formats == null || formats.isEmpty()) {
			return Set.of();
		}
		Set<ModelFormat> normalized = new HashSet<>();
		for (ModelFormat format : formats) {
			if (format == null) {
				throw new IllegalStateException("Processor transform source-format scope must not contain null values.");
			}
			normalized.add(format);
		}
		return Set.copyOf(normalized);
	}


	private record TransformDispatchKey(String transformType, ModelFormat sourceFormat) {
	}
}


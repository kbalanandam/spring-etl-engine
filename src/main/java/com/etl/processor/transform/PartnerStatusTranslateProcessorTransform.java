package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/**
 * Showcase custom transform loaded through ProcessorExtensionProvider.
 */
@Component
public class PartnerStatusTranslateProcessorTransform implements ProcessorFieldTransform {

	private static final String TRANSFORM_TYPE = "partnerStatusTranslate";

	@Override
	public String getTransformType() {
		return TRANSFORM_TYPE;
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
		Map<String, Object> config = requiredConfig(transform);
		Object mappingsValue = config.get("mappings");
		if (!(mappingsValue instanceof Map<?, ?> mappings) || mappings.isEmpty()) {
			throw new IllegalStateException("FieldMapping transform 'partnerStatusTranslate' requires transforms[].config.mappings.");
		}
		for (Object key : mappings.keySet()) {
			if (key == null || String.valueOf(key).isBlank()) {
				throw new IllegalStateException("FieldMapping transform 'partnerStatusTranslate' does not allow blank mapping keys.");
			}
		}
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		Map<String, Object> config = requiredConfig(transform);
		@SuppressWarnings("unchecked")
		Map<Object, Object> mappings = (Map<Object, Object>) config.get("mappings");
		Object fallbackValue = config.get("fallbackValue");
		if (value == null) {
			return fallbackValue;
		}

		String candidate = String.valueOf(value);
		if (mappings.containsKey(candidate)) {
			return mappings.get(candidate);
		}

		// Case-insensitive lookup keeps partner feeds resilient when upstream casing drifts.
		String normalizedCandidate = candidate.toUpperCase(Locale.ROOT);
		for (Map.Entry<Object, Object> entry : mappings.entrySet()) {
			String mappingKey = String.valueOf(entry.getKey());
			if (normalizedCandidate.equals(mappingKey.toUpperCase(Locale.ROOT))) {
				return entry.getValue();
			}
		}
		return fallbackValue != null ? fallbackValue : value;
	}

	private Map<String, Object> requiredConfig(ProcessorConfig.FieldTransform transform) {
		if (transform == null || transform.getConfig() == null || transform.getConfig().isEmpty()) {
			throw new IllegalStateException("FieldMapping transform 'partnerStatusTranslate' requires a non-empty 'config' object.");
		}
		return transform.getConfig();
	}
}


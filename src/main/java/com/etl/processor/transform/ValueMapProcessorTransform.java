package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ValueMapProcessorTransform implements ProcessorFieldTransform {

	@Override
	public String getTransformType() {
		return "valueMap";
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
		if (transform.getMappings() == null || transform.getMappings().isEmpty()) {
			throw new IllegalStateException("FieldMapping transform 'valueMap' requires a non-empty 'mappings' object for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}

		for (String key : transform.getMappings().keySet()) {
			if (key == null) {
				throw new IllegalStateException("FieldMapping transform 'valueMap' does not allow null mapping keys for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
			}
		}
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		for (Map.Entry<String, Object> entry : transform.getMappings().entrySet()) {
			if (matches(value, entry.getKey(), isCaseSensitive(transform))) {
				return entry.getValue();
			}
		}
		return transform.getDefaultValue() != null ? transform.getDefaultValue() : value;
	}

	private boolean matches(Object value, String configuredKey, boolean caseSensitive) {
		if (value == null) {
			return configuredKey == null;
		}
		String candidate = String.valueOf(value);
		if (caseSensitive) {
			return candidate.equals(configuredKey);
		}
		return candidate.equalsIgnoreCase(configuredKey);
	}

	private boolean isCaseSensitive(ProcessorConfig.FieldTransform transform) {
		return transform.getCaseSensitive() == null || transform.getCaseSensitive();
	}
}


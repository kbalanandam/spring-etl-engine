package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Converts a date-time text value between configured time zones.
 */
@Component
public class ZoneConvertProcessorTransform implements ProcessorFieldTransform {

	private static final String TRANSFORM_TYPE = "zoneConvert";
	private static final String FALLBACK_SYSTEM_TIME = "systemTime";

	@Override
	public String getTransformType() {
		return TRANSFORM_TYPE;
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldTransform transform) {
		Map<String, Object> config = requiredConfig(transform);
		String fromZone = requiredConfigString(config, "fromZone");
		String toZone = requiredConfigString(config, "toZone");
		String inputPattern = requiredConfigString(config, "inputPattern");
		String outputPattern = optionalConfigString(config, "outputPattern");

		requireValidZone(fromZone, "fromZone");
		requireValidZone(toZone, "toZone");
		requireValidPattern(inputPattern, "inputPattern");
		if (outputPattern != null) {
			requireValidPattern(outputPattern, "outputPattern");
		}
	}

	@Override
	public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
		Map<String, Object> config = requiredConfig(transform);
		String fromZone = requiredConfigString(config, "fromZone");
		String toZone = requiredConfigString(config, "toZone");
		String inputPattern = requiredConfigString(config, "inputPattern");
		String outputPattern = optionalConfigString(config, "outputPattern");

		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(inputPattern);
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(outputPattern == null ? inputPattern : outputPattern);

		if (value == null) {
			return fallbackOrOriginal(value, config, toZone, outputFormatter);
		}

		String candidate = String.valueOf(value).trim();
		if (candidate.isEmpty()) {
			return fallbackOrOriginal(value, config, toZone, outputFormatter);
		}

		try {
			LocalDateTime sourceDateTime = LocalDateTime.parse(candidate, inputFormatter);
			ZonedDateTime sourceZonedDateTime = sourceDateTime.atZone(ZoneId.of(fromZone));
			ZonedDateTime targetZonedDateTime = sourceZonedDateTime.withZoneSameInstant(ZoneId.of(toZone));
			return targetZonedDateTime.format(outputFormatter);
		} catch (DateTimeParseException | IllegalArgumentException e) {
			return fallbackOrOriginal(value, config, toZone, outputFormatter);
		}
	}

	private Object fallbackOrOriginal(Object originalValue,
	                                 Map<String, Object> config,
	                                 String toZone,
	                                 DateTimeFormatter outputFormatter) {
		Object fallbackValue = config.get("fallbackValue");
		if (fallbackValue == null) {
			return originalValue;
		}

		if (fallbackValue instanceof String fallbackText
				&& FALLBACK_SYSTEM_TIME.equalsIgnoreCase(fallbackText.trim())) {
			return ZonedDateTime.now(ZoneId.of(toZone)).format(outputFormatter);
		}
		return fallbackValue;
	}

	private Map<String, Object> requiredConfig(ProcessorConfig.FieldTransform transform) {
		if (transform == null || transform.getConfig() == null || transform.getConfig().isEmpty()) {
			throw new IllegalStateException("FieldMapping transform 'zoneConvert' requires a non-empty 'config' object.");
		}
		return transform.getConfig();
	}

	private String requiredConfigString(Map<String, Object> config, String key) {
		Object raw = config.get(key);
		if (raw == null || String.valueOf(raw).isBlank()) {
			throw new IllegalStateException("FieldMapping transform 'zoneConvert' requires transforms[].config." + key + ".");
		}
		return String.valueOf(raw).trim();
	}

	private String optionalConfigString(Map<String, Object> config, String key) {
		Object raw = config.get(key);
		if (raw == null || String.valueOf(raw).isBlank()) {
			return null;
		}
		return String.valueOf(raw).trim();
	}

	private void requireValidZone(String zoneId, String key) {
		try {
			ZoneId.of(zoneId);
		} catch (Exception ex) {
			throw new IllegalStateException("FieldMapping transform 'zoneConvert' has invalid transforms[].config." + key + "='" + zoneId + "'.", ex);
		}
	}

	private void requireValidPattern(String pattern, String key) {
		try {
			DateTimeFormatter.ofPattern(pattern);
		} catch (Exception ex) {
			throw new IllegalStateException("FieldMapping transform 'zoneConvert' has invalid transforms[].config." + key + "='" + pattern + "'.", ex);
		}
	}
}


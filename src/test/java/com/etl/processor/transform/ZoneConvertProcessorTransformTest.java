package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneConvertProcessorTransformTest {

	private final ZoneConvertProcessorTransform transform = new ZoneConvertProcessorTransform();

	@Test
	void convertsBetweenConfiguredZones() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		Object converted = transform.apply("2026-05-27 12:00:00", declaration);
		assertEquals("2026-05-27 07:00:00", converted);
	}

	@Test
	void convertsSpringForwardGapUsingDeterministicZoneRules() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "America/New_York",
				"toZone", "UTC",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		Object converted = transform.apply("2024-03-10 02:30:00", declaration);
		assertEquals("2024-03-10 07:30:00", converted);
	}

	@Test
	void convertsFallBackOverlapUsingEarlierOffsetByDefault() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "America/New_York",
				"toZone", "UTC",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		Object converted = transform.apply("2024-11-03 01:30:00", declaration);
		assertEquals("2024-11-03 05:30:00", converted);
	}

	@Test
	void usesSystemTimeFallbackWhenConfiguredAndInputCannotBeParsed() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss",
				"fallbackValue", "systemTime"
		));

		Object converted = transform.apply("bad-time-value", declaration);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
		LocalDateTime parsed = LocalDateTime.parse(String.valueOf(converted), formatter);
		assertTrue(parsed.getYear() >= 2020);
	}

	@Test
	void usesLiteralFallbackWhenConfiguredAndInputCannotBeParsed() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss",
				"fallbackValue", "UNKNOWN_TIME"
		));

		Object converted = transform.apply("bad-time-value", declaration);
		assertEquals("UNKNOWN_TIME", converted);
	}

	@Test
	void keepsOriginalValueWhenNoFallbackIsConfiguredAndInputCannotBeParsed() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		Object converted = transform.apply("bad-time-value", declaration);
		assertEquals("bad-time-value", converted);
	}

	@Test
	void failsValidationWhenRequiredConfigIsMissing() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"inputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> transform.validateConfiguration(entityMapping(), fieldMapping(), declaration));
		assertTrue(failure.getMessage().contains("transforms[].config.toZone"));
	}

	@Test
	void failsValidationWhenZoneIdIsInvalid() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "NotAZone",
				"inputPattern", "yyyy-MM-dd HH:mm:ss"
		));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> transform.validateConfiguration(entityMapping(), fieldMapping(), declaration));
		assertTrue(failure.getMessage().contains("invalid transforms[].config.toZone"));
	}

	@Test
	void failsValidationWhenInputPatternIsInvalid() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss'"
		));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> transform.validateConfiguration(entityMapping(), fieldMapping(), declaration));
		assertTrue(failure.getMessage().contains("invalid transforms[].config.inputPattern"));
	}

	@Test
	void failsValidationWhenOutputPatternIsInvalid() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"fromZone", "UTC",
				"toZone", "America/Chicago",
				"inputPattern", "yyyy-MM-dd HH:mm:ss",
				"outputPattern", "yyyy-MM-dd HH:mm:ss'"
		));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> transform.validateConfiguration(entityMapping(), fieldMapping(), declaration));
		assertTrue(failure.getMessage().contains("invalid transforms[].config.outputPattern"));
	}

	private ProcessorConfig.FieldTransform declaration(Map<String, Object> config) {
		ProcessorConfig.FieldTransform declaration = new ProcessorConfig.FieldTransform();
		declaration.setType("zoneConvert");
		declaration.setConfig(config);
		return declaration;
	}

	private ProcessorConfig.EntityMapping entityMapping() {
		ProcessorConfig.EntityMapping entity = new ProcessorConfig.EntityMapping();
		entity.setSource("Events");
		entity.setTarget("EventsOut");
		return entity;
	}

	private ProcessorConfig.FieldMapping fieldMapping() {
		ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
		field.setFrom("eventTimeUtc");
		field.setTo("eventTimeLocal");
		return field;
	}
}


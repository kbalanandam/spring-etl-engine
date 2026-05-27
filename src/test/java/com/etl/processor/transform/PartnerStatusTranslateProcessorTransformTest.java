package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartnerStatusTranslateProcessorTransformTest {

	private final PartnerStatusTranslateProcessorTransform transform = new PartnerStatusTranslateProcessorTransform();

	@Test
	void translatesConfiguredPartnerStatuses() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"mappings", Map.of("PARTNER_OPS", "Operations", "PARTNER_BATCH", "Batch"),
				"fallbackValue", "Unknown"
		));

		assertEquals("Operations", transform.apply("PARTNER_OPS", declaration));
		assertEquals("Batch", transform.apply("partner_batch", declaration));
	}

	@Test
	void returnsFallbackForUnknownPartnerStatus() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"mappings", Map.of("PARTNER_OPS", "Operations"),
				"fallbackValue", "Unknown"
		));

		assertEquals("Unknown", transform.apply("OTHER", declaration));
	}

	@Test
	void keepsOriginalValueWhenNoFallbackIsConfigured() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of(
				"mappings", Map.of("PARTNER_OPS", "Operations")
		));

		assertEquals("OTHER", transform.apply("OTHER", declaration));
	}

	@Test
	void failsValidationWhenMappingsMissing() {
		ProcessorConfig.FieldTransform declaration = declaration(Map.of("fallbackValue", "Unknown"));

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> transform.validateConfiguration(entityMapping(), fieldMapping(), declaration));
		assertTrue(failure.getMessage().contains("transforms[].config.mappings"));
	}

	private ProcessorConfig.FieldTransform declaration(Map<String, Object> config) {
		ProcessorConfig.FieldTransform declaration = new ProcessorConfig.FieldTransform();
		declaration.setType("partnerStatusTranslate");
		declaration.setConfig(config);
		return declaration;
	}

	private ProcessorConfig.EntityMapping entityMapping() {
		ProcessorConfig.EntityMapping entity = new ProcessorConfig.EntityMapping();
		entity.setSource("Events");
		entity.setTarget("EventsCsv");
		return entity;
	}

	private ProcessorConfig.FieldMapping fieldMapping() {
		ProcessorConfig.FieldMapping field = new ProcessorConfig.FieldMapping();
		field.setFrom("sourceSystem");
		field.setTo("sourceSystem");
		return field;
	}
}


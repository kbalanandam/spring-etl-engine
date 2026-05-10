package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransformEvaluatorTest {

	@Test
	void appliesDefaultValueWhenValueMapDoesNotMatch() {
		TransformEvaluator evaluator = new TransformEvaluator();
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("statusCode");
		fieldMapping.setTo("status");
		fieldMapping.setTransforms(List.of(valueMap(Map.of("1", "Success", "2", "Fail"), "Unknown", true)));

		assertEquals("Unknown", evaluator.apply("9", fieldMapping));
	}

	@Test
	void supportsCustomProcessorTransformExtensions() {
		TransformEvaluator evaluator = new TransformEvaluator(List.of(
				new ValueMapProcessorTransform(),
				new PrefixProcessorTransform()
		));
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("prefix");
		transform.setDefaultValue("ETL-");
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("id");
		fieldMapping.setTo("id");
		fieldMapping.setTransforms(List.of(transform));

		assertEquals("ETL-1001", evaluator.apply("1001", fieldMapping));
	}

	@Test
	void appliesBuiltInExpressionTransformUsingCurrentValue() {
		TransformEvaluator evaluator = new TransformEvaluator();
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("expression");
		transform.setExpression("#value + '-OK'");
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("statusCode");
		fieldMapping.setTo("status");
		fieldMapping.setTransforms(List.of(transform));

		assertEquals("1-OK", evaluator.apply("1", fieldMapping));
	}

	@Test
	void rejectsInvalidBuiltInExpressionConfig() {
		TransformEvaluator evaluator = new TransformEvaluator();
		ProcessorConfig.EntityMapping entityMapping = new ProcessorConfig.EntityMapping();
		entityMapping.setSource("Customers");
		entityMapping.setTarget("CustomersOut");
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("name");
		fieldMapping.setTo("displayName");
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("expression");
		transform.setExpression("#value + ");

		assertThrows(IllegalStateException.class, () -> evaluator.validateConfiguration(entityMapping, fieldMapping, transform));
	}

	@Test
	void rejectsUnsupportedTransformTypes() {
		TransformEvaluator evaluator = new TransformEvaluator();
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("statusCode");
		fieldMapping.setTo("status");
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("missing");
		fieldMapping.setTransforms(List.of(transform));

		assertThrows(IllegalArgumentException.class, () -> evaluator.apply("1", fieldMapping));
	}

	private ProcessorConfig.FieldTransform valueMap(Map<String, Object> mappings, Object defaultValue, boolean caseSensitive) {
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("valueMap");
		transform.setMappings(new LinkedHashMap<>(mappings));
		transform.setDefaultValue(defaultValue);
		transform.setCaseSensitive(caseSensitive);
		return transform;
	}

	private static final class PrefixProcessorTransform implements ProcessorFieldTransform {

		@Override
		public String getTransformType() {
			return "prefix";
		}

		@Override
		public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
		                               ProcessorConfig.FieldMapping fieldMapping,
		                               ProcessorConfig.FieldTransform transform) {
			if (transform.getDefaultValue() == null) {
				throw new IllegalStateException("prefix requires defaultValue");
			}
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return String.valueOf(transform.getDefaultValue()) + value;
		}
	}
}


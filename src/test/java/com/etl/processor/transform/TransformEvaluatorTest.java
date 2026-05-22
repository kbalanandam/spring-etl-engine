package com.etl.processor.transform;

import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;
import com.etl.processor.ProcessorExtensionDefaults;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformEvaluatorTest {

	@Test
	void appliesDefaultValueWhenValueMapDoesNotMatch() {
		TransformEvaluator evaluator = builtInTransformEvaluator();
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
		TransformEvaluator evaluator = builtInTransformEvaluator();
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
		TransformEvaluator evaluator = builtInTransformEvaluator();
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
	void appliesFirstMatchingConditionalCase() {
		TransformEvaluator evaluator = builtInTransformEvaluator();
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("amount");
		fieldMapping.setTo("tier");
		fieldMapping.setTransforms(List.of(conditional(List.of(
				conditionalCase("#value >= 10000", "ENTERPRISE"),
				conditionalCase("#value >= 1000", "MID")
		), "SMB")));

		assertEquals("MID", evaluator.apply(2500, fieldMapping));
	}

	@Test
	void keepsOriginalValueWhenConditionalDoesNotMatchAndNoDefaultIsConfigured() {
		TransformEvaluator evaluator = builtInTransformEvaluator();
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("status");
		fieldMapping.setTo("status");
		fieldMapping.setTransforms(List.of(conditional(List.of(
				conditionalCase("#value == 'A'", "ACTIVE")
		), null)));

		assertEquals("P", evaluator.apply("P", fieldMapping));
	}

	@Test
	void rejectsInvalidConditionalTransformConfig() {
		TransformEvaluator evaluator = builtInTransformEvaluator();
		ProcessorConfig.EntityMapping entityMapping = new ProcessorConfig.EntityMapping();
		entityMapping.setSource("Customers");
		entityMapping.setTarget("CustomersOut");
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("amount");
		fieldMapping.setTo("tier");
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("conditional");
		transform.setCases(List.of(conditionalCase("#value >", "HIGH")));

		assertThrows(IllegalStateException.class, () -> evaluator.validateConfiguration(entityMapping, fieldMapping, transform));
	}

	@Test
	void rejectsUnsupportedTransformTypes() {
		TransformEvaluator evaluator = builtInTransformEvaluator();
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("statusCode");
		fieldMapping.setTo("status");
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("missing");
		fieldMapping.setTransforms(List.of(transform));

		assertThrows(IllegalArgumentException.class, () -> evaluator.apply("1", fieldMapping));
	}

	@Test
	void prefersScopedTransformForMatchingSourceFormatAndFallsBackToGlobalTransform() {
		TransformEvaluator evaluator = new TransformEvaluator(List.of(
				new GlobalScopedPrefixTransform(),
				new CsvScopedPrefixTransform()
		));
		ProcessorConfig.FieldMapping fieldMapping = prefixScopedFieldMapping();

		assertEquals("CSV-1001", evaluator.apply("1001", fieldMapping, ModelFormat.CSV));
		assertEquals("GLOBAL-1001", evaluator.apply("1001", fieldMapping, ModelFormat.XML));
	}

	@Test
	void throwsWhenTransformIsScopedToDifferentFormat() {
		TransformEvaluator evaluator = new TransformEvaluator(List.of(new CsvOnlyTransform()));
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("id");
		fieldMapping.setTo("id");
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("csvOnly");
		fieldMapping.setTransforms(List.of(transform));

		IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
				() -> evaluator.apply("1001", fieldMapping, ModelFormat.XML));
		assertEquals("Unsupported processor transform type: csvOnly for source format xml", error.getMessage());
	}

	@Test
	void prefersOverrideTransformWhenGlobalTransformTypeConflicts() {
		TransformEvaluator evaluator = new TransformEvaluator(List.of(
				new GlobalScopedPrefixTransform(),
				new OverrideGlobalScopedPrefixTransform()
		));
		ProcessorConfig.FieldMapping fieldMapping = prefixScopedFieldMapping();

		assertEquals("OVERRIDE-1001", evaluator.apply("1001", fieldMapping, ModelFormat.XML));
	}

	@Test
	void failsWhenDuplicateGlobalTransformTypeHasNoOverride() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> new TransformEvaluator(List.of(new GlobalScopedPrefixTransform(), new DuplicateGlobalScopedPrefixTransform())));
		assertTrue(failure.getMessage().startsWith("Duplicate processor transform type registration: prefixScoped"));
	}

	@Test
	void failsWhenTwoOverrideTransformsRegisterSameGlobalType() {
		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> new TransformEvaluator(List.of(new OverrideGlobalScopedPrefixTransform(), new SecondOverrideGlobalScopedPrefixTransform())));
		assertTrue(failure.getMessage().contains("Duplicate processor transform type registration: prefixScoped"));
		assertTrue(failure.getMessage().contains("override-prefix-transform"));
		assertTrue(failure.getMessage().contains("second-override-prefix-transform"));
	}

	private ProcessorConfig.FieldTransform valueMap(Map<String, Object> mappings, Object defaultValue, boolean caseSensitive) {
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("valueMap");
		transform.setMappings(new LinkedHashMap<>(mappings));
		transform.setDefaultValue(defaultValue);
		transform.setCaseSensitive(caseSensitive);
		return transform;
	}

	private TransformEvaluator builtInTransformEvaluator() {
		return new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms());
	}

	private ProcessorConfig.FieldTransform conditional(List<ProcessorConfig.ConditionalCase> cases, Object defaultValue) {
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("conditional");
		transform.setCases(cases);
		transform.setDefaultValue(defaultValue);
		return transform;
	}

	private ProcessorConfig.ConditionalCase conditionalCase(String when, Object then) {
		ProcessorConfig.ConditionalCase conditionalCase = new ProcessorConfig.ConditionalCase();
		conditionalCase.setWhen(when);
		conditionalCase.setThen(then);
		return conditionalCase;
	}

	private ProcessorConfig.FieldMapping prefixScopedFieldMapping() {
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("prefixScoped");
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("id");
		fieldMapping.setTo("id");
		fieldMapping.setTransforms(List.of(transform));
		return fieldMapping;
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

	private static final class GlobalScopedPrefixTransform implements ProcessorFieldTransform {

		@Override
		public String getTransformType() {
			return "prefixScoped";
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return "GLOBAL-" + value;
		}
	}

	private static final class CsvScopedPrefixTransform implements ProcessorFieldTransform {

		@Override
		public String getTransformType() {
			return "prefixScoped";
		}

		@Override
		public java.util.Set<ModelFormat> supportedSourceFormats() {
			return java.util.Set.of(ModelFormat.CSV);
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return "CSV-" + value;
		}
	}

	private static final class CsvOnlyTransform implements ProcessorFieldTransform {

		@Override
		public String getTransformType() {
			return "csvOnly";
		}

		@Override
		public java.util.Set<ModelFormat> supportedSourceFormats() {
			return java.util.Set.of(ModelFormat.CSV);
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return value;
		}
	}

	private static final class OverrideGlobalScopedPrefixTransform implements ProcessorFieldTransform {

		@Override
		public String extensionId() {
			return "override-prefix-transform";
		}

		@Override
		public boolean isOverride() {
			return true;
		}

		@Override
		public String getTransformType() {
			return "prefixScoped";
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return "OVERRIDE-" + value;
		}
	}

	private static final class DuplicateGlobalScopedPrefixTransform implements ProcessorFieldTransform {

		@Override
		public String extensionId() {
			return "duplicate-prefix-transform";
		}

		@Override
		public String getTransformType() {
			return "prefixScoped";
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return "DUP-" + value;
		}
	}

	private static final class SecondOverrideGlobalScopedPrefixTransform implements ProcessorFieldTransform {

		@Override
		public String extensionId() {
			return "second-override-prefix-transform";
		}

		@Override
		public boolean isOverride() {
			return true;
		}

		@Override
		public String getTransformType() {
			return "prefixScoped";
		}

		@Override
		public Object apply(Object value, ProcessorConfig.FieldTransform transform) {
			return "SECOND-OVERRIDE-" + value;
		}
	}
}


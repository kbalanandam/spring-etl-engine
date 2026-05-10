package com.etl.mapping;

import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.transform.TransformEvaluator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DynamicMappingTransformTest {

	@Test
	void appliesValueMapTransformWithoutValidationRules() throws Exception {
		ProcessorConfig.FieldMapping countryCode = new ProcessorConfig.FieldMapping();
		countryCode.setFrom("countryCode");
		countryCode.setTo("countryCode");
		countryCode.setTransforms(List.of(valueMap(Map.of("USA", "US"), null, true)));

		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource("Customers");
		mapping.setTarget("CustomersOut");
		mapping.setFields(List.of(countryCode));

		DynamicMapping<InputRecord, TargetRecord> processor = new DynamicMapping<>(mapping, TargetRecord.class, new TransformEvaluator());
		TargetRecord output = processor.process(new InputRecord("USA"));

		assertNotNull(output);
		assertEquals("US", output.countryCode);
	}

	@Test
	void appliesOrderedTransformChain() throws Exception {
		ProcessorConfig.FieldMapping countryCode = new ProcessorConfig.FieldMapping();
		countryCode.setFrom("countryCode");
		countryCode.setTo("countryCode");
		countryCode.setTransforms(List.of(
				valueMap(Map.of("usa", "US"), null, false),
				valueMap(Map.of("US", "United States"), null, true)
		));

		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource("Customers");
		mapping.setTarget("CustomersOut");
		mapping.setFields(List.of(countryCode));

		DynamicMapping<InputRecord, TargetRecord> processor = new DynamicMapping<>(mapping, TargetRecord.class, new TransformEvaluator());
		TargetRecord output = processor.process(new InputRecord("USA"));

		assertNotNull(output);
		assertEquals("United States", output.countryCode);
	}

	@Test
	void derivesFieldFromMultipleSourcePropertiesUsingExpressionTransform() throws Exception {
		ProcessorConfig.FieldMapping fullName = new ProcessorConfig.FieldMapping();
		fullName.setTo("fullName");
		ProcessorConfig.FieldTransform expression = new ProcessorConfig.FieldTransform();
		expression.setType("expression");
		expression.setExpression("#input.firstName + ' ' + #input.lastName");
		fullName.setTransforms(List.of(expression));

		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource("Customers");
		mapping.setTarget("CustomersOut");
		mapping.setFields(List.of(fullName));

		DynamicMapping<NameInputRecord, NameTargetRecord> processor = new DynamicMapping<>(mapping, NameTargetRecord.class, new TransformEvaluator());
		NameTargetRecord output = processor.process(new NameInputRecord("Ada", "Lovelace"));

		assertNotNull(output);
		assertEquals("Ada Lovelace", output.fullName);
	}

	@Test
	void expressionTransformCanUsePreviouslyResolvedFieldValues() throws Exception {
		ProcessorConfig.FieldMapping countryCode = new ProcessorConfig.FieldMapping();
		countryCode.setFrom("countryCode");
		countryCode.setTo("countryCode");
		countryCode.setTransforms(List.of(valueMap(Map.of("USA", "US"), null, true)));

		ProcessorConfig.FieldMapping summary = new ProcessorConfig.FieldMapping();
		summary.setTo("summary");
		ProcessorConfig.FieldTransform expression = new ProcessorConfig.FieldTransform();
		expression.setType("expression");
		expression.setExpression("#resolved['countryCode'] + ':' + #input.customerId");
		summary.setTransforms(List.of(expression));

		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource("Customers");
		mapping.setTarget("CustomersOut");
		mapping.setFields(List.of(countryCode, summary));

		DynamicMapping<SummaryInputRecord, SummaryTargetRecord> processor = new DynamicMapping<>(mapping, SummaryTargetRecord.class, new TransformEvaluator());
		SummaryTargetRecord output = processor.process(new SummaryInputRecord("C-100", "USA"));

		assertNotNull(output);
		assertEquals("US:C-100", output.summary);
	}

	private ProcessorConfig.FieldTransform valueMap(Map<String, Object> mappings, Object defaultValue, boolean caseSensitive) {
		ProcessorConfig.FieldTransform transform = new ProcessorConfig.FieldTransform();
		transform.setType("valueMap");
		transform.setMappings(new LinkedHashMap<>(mappings));
		transform.setDefaultValue(defaultValue);
		transform.setCaseSensitive(caseSensitive);
		return transform;
	}

	private record InputRecord(String countryCode) {
	}

	private record NameInputRecord(String firstName, String lastName) {
	}

	private record SummaryInputRecord(String customerId, String countryCode) {
	}

	public static final class TargetRecord {
		public String countryCode;
	}

	public static final class NameTargetRecord {
		public String fullName;
	}

	public static final class SummaryTargetRecord {
		public String countryCode;
		public String summary;
	}
}



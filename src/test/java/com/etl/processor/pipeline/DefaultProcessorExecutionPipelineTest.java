package com.etl.processor.pipeline;

import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.ColumnConfig;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.CsvSourceConfig;
import com.etl.config.target.JsonTargetConfig;
import com.etl.mapping.DynamicMapping;
import com.etl.mapping.ValidationAwareDynamicMapping;
import com.etl.processor.ProcessorExtensionDefaults;
import com.etl.processor.pipeline.impl.DefaultProcessorExecutionPipeline;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.NotNullProcessorValidationRule;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.junit.jupiter.api.Test;
import org.springframework.batch.item.ItemProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DefaultProcessorExecutionPipelineTest {

	@Test
	void returnsTransformOnlyDynamicMappingWhenSelectedMappingHasNoRules() throws Exception {
		DefaultProcessorExecutionPipeline pipeline = defaultPipeline();
		ProcessorConfig processorConfig = processorConfig(false);

		ItemProcessor<Object, Object> processor = pipeline.createProcessor(
				processorConfig,
				sourceConfig(),
				targetConfig(),
				metadata()
		);

		assertInstanceOf(DynamicMapping.class, processor);
	}

	@Test
	void returnsValidationAwareMappingWhenSelectedMappingHasRules() throws Exception {
		DefaultProcessorExecutionPipeline pipeline = defaultPipeline();
		ProcessorConfig processorConfig = processorConfig(true);

		ItemProcessor<Object, Object> processor = pipeline.createProcessor(
				processorConfig,
				sourceConfig(),
				targetConfig(),
				metadata()
		);

		assertInstanceOf(ValidationAwareDynamicMapping.class, processor);
	}

	@Test
	void failsFastWhenMappingForSelectedSourceAndTargetIsMissing() {
		DefaultProcessorExecutionPipeline pipeline = defaultPipeline();
		ProcessorConfig processorConfig = new ProcessorConfig();
		processorConfig.setMappings(List.of());

		assertThrows(IllegalStateException.class, () -> pipeline.createProcessor(
				processorConfig,
				sourceConfig(),
				targetConfig(),
				metadata()
		));
	}

	private DefaultProcessorExecutionPipeline defaultPipeline() {
		ValidationRuleEvaluator validationRuleEvaluator = new ValidationRuleEvaluator(List.of(
				new NotNullProcessorValidationRule()
		));
		return new DefaultProcessorExecutionPipeline(
				validationRuleEvaluator,
				new TransformEvaluator(ProcessorExtensionDefaults.defaultTransforms()),
				new FileIngestionRuntimeSupport()
		);
	}

	private ProcessorConfig processorConfig(boolean includeRule) {
		ProcessorConfig.FieldMapping fieldMapping = new ProcessorConfig.FieldMapping();
		fieldMapping.setFrom("id");
		fieldMapping.setTo("id");
		if (includeRule) {
			ProcessorConfig.FieldRule rule = new ProcessorConfig.FieldRule();
			rule.setType("notNull");
			fieldMapping.setRules(List.of(rule));
		}

		ProcessorConfig.EntityMapping mapping = new ProcessorConfig.EntityMapping();
		mapping.setSource("Customers");
		mapping.setTarget("CustomersOut");
		mapping.setFields(List.of(fieldMapping));

		ProcessorConfig processorConfig = new ProcessorConfig();
		processorConfig.setMappings(List.of(mapping));
		return processorConfig;
	}

	private CsvSourceConfig sourceConfig() {
		return new CsvSourceConfig("Customers", null, List.<ColumnConfig>of(), "ignored.csv", ",");
	}

	private JsonTargetConfig targetConfig() {
		return new JsonTargetConfig("CustomersOut", null, List.of(), "ignored.json");
	}

	private ResolvedModelMetadata metadata() {
		return new ResolvedModelMetadata(
				"java.lang.Object",
				"java.util.LinkedHashMap",
				"java.util.LinkedHashMap",
				false,
				null
		);
	}
}




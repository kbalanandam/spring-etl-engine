package com.etl.processor.pipeline.impl;

import com.etl.common.util.GeneratedModelClassResolver;
import com.etl.common.util.ResolvedModelMetadata;
import com.etl.config.processor.ProcessorConfig;
import com.etl.config.source.SourceConfig;
import com.etl.config.target.TargetConfig;
import com.etl.mapping.DynamicMapping;
import com.etl.mapping.ValidationAwareDynamicMapping;
import com.etl.processor.pipeline.MappingSelectionService;
import com.etl.processor.pipeline.ProcessorExecutionPipeline;
import com.etl.processor.transform.TransformEvaluator;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

/**
 * Default processor orchestration pipeline used by the shared {@code default} processor type.
 */
public class DefaultProcessorExecutionPipeline implements ProcessorExecutionPipeline {

	private static final Logger logger = LoggerFactory.getLogger(DefaultProcessorExecutionPipeline.class);

	private final ValidationRuleEvaluator validationRuleEvaluator;
	private final TransformEvaluator transformEvaluator;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
	private final MappingSelectionService mappingSelectionService;

	public DefaultProcessorExecutionPipeline(
			ValidationRuleEvaluator validationRuleEvaluator,
			TransformEvaluator transformEvaluator,
			FileIngestionRuntimeSupport fileIngestionRuntimeSupport
	) {
		this(validationRuleEvaluator, transformEvaluator, fileIngestionRuntimeSupport, new MappingSelectionService());
	}

	DefaultProcessorExecutionPipeline(
			ValidationRuleEvaluator validationRuleEvaluator,
			TransformEvaluator transformEvaluator,
			FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
			MappingSelectionService mappingSelectionService
	) {
		this.validationRuleEvaluator = validationRuleEvaluator;
		this.transformEvaluator = transformEvaluator;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
		this.mappingSelectionService = mappingSelectionService;
	}

	@Override
	public ItemProcessor<Object, Object> createProcessor(
			ProcessorConfig processorConfig,
			SourceConfig sourceConfig,
			TargetConfig targetConfig,
			ResolvedModelMetadata metadata
	) throws ClassNotFoundException {
		ProcessorConfig.EntityMapping mapping = mappingSelectionService.select(processorConfig, sourceConfig, targetConfig);
		Class<Object> targetClass = metadata != null
				? GeneratedModelClassResolver.resolveTargetProcessingClass(metadata)
				: GeneratedModelClassResolver.resolveTargetProcessingClass(targetConfig);
		logger.info("Using mapping for {} -> {} with {} fields",
				sourceConfig.getSourceName(),
				targetConfig.getTargetName(),
				mapping.getFields().size());

		if (hasValidationRules(mapping)) {
			return new ValidationAwareDynamicMapping<>(
					mapping,
					targetClass,
					transformEvaluator,
					validationRuleEvaluator,
					fileIngestionRuntimeSupport,
					processorConfig.getRejectHandling() != null && processorConfig.getRejectHandling().isEnabled(),
					sourceConfig == null ? null : sourceConfig.getFormat()
			);
		}

		return new DynamicMapping<>(
				mapping,
				targetClass,
				transformEvaluator,
				sourceConfig == null ? null : sourceConfig.getFormat()
		);
	}

	private boolean hasValidationRules(ProcessorConfig.EntityMapping mapping) {
		return mapping.getFields() != null && mapping.getFields().stream()
				.anyMatch(fieldMapping -> fieldMapping.getRules() != null && !fieldMapping.getRules().isEmpty());
	}
}





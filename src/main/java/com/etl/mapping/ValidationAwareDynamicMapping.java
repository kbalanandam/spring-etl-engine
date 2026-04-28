package com.etl.mapping;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.processor.validation.ValidationIssue;
import com.etl.processor.validation.ValidationRuleEvaluator;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.batch.item.ItemProcessor;

import java.util.List;

public class ValidationAwareDynamicMapping<I, O> implements ItemProcessor<I, O> {

	private final ProcessorConfig.EntityMapping mapping;
	private final Class<O> targetClass;
	private final ValidationRuleEvaluator validationRuleEvaluator;
	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;
	private final boolean rejectHandlingEnabled;

	public ValidationAwareDynamicMapping(ProcessorConfig.EntityMapping mapping,
	                                   Class<O> targetClass,
	                                   ValidationRuleEvaluator validationRuleEvaluator,
	                                   FileIngestionRuntimeSupport fileIngestionRuntimeSupport,
	                                   boolean rejectHandlingEnabled) {
		this.mapping = mapping;
		this.targetClass = targetClass;
		this.validationRuleEvaluator = validationRuleEvaluator;
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
		this.rejectHandlingEnabled = rejectHandlingEnabled;
	}

	@Override
	public O process(I input) throws Exception {
		List<ValidationIssue> issues = validationRuleEvaluator.evaluate(input, mapping);
		if (!issues.isEmpty()) {
			if (!rejectHandlingEnabled) {
				throw new IllegalStateException("Validation rules rejected a record but reject handling is not enabled.");
			}

			boolean recorded = fileIngestionRuntimeSupport.recordRejected(input, issues);
			if (!recorded) {
				throw new IllegalStateException("Validation rules rejected a record but reject handling was not initialized for the current step.");
			}
			return null;
		}

		O output = ReflectionUtils.createInstance(targetClass);
		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			Object value = ReflectionUtils.getFieldValue(input, fieldMapping.getFrom());
			ReflectionUtils.setFieldValue(output, fieldMapping.getTo(), value);
		}
		return output;
	}
}

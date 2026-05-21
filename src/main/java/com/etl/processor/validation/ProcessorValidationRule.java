package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;

import java.util.Set;

public interface ProcessorValidationRule {

	String getRuleType();

	/**
	 * Optional source-format scope for this rule registration.
	 *
	 * <p>Empty means the rule applies to all source formats.</p>
	 */
	default Set<ModelFormat> supportedSourceFormats() {
		return Set.of();
	}

	default void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldRule rule) {
	}

	default ValidationIssue evaluate(Object input,
	                               String fieldName,
	                               Object value,
	                               ProcessorConfig.FieldRule rule) {
		return evaluate(fieldName, value, rule);
	}

	ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule);
}


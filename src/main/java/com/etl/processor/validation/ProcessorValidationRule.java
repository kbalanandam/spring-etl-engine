package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;

public interface ProcessorValidationRule {

	String getRuleType();

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


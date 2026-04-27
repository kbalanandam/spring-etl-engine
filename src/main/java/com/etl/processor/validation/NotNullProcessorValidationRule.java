package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

@Component
public class NotNullProcessorValidationRule implements ProcessorValidationRule {

	@Override
	public String getRuleType() {
		return "notNull";
	}

	@Override
	public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (value == null) {
			return new ValidationIssue(fieldName, getRuleType(), fieldName + " must not be null");
		}

		if (value instanceof String stringValue && stringValue.isBlank()) {
			return new ValidationIssue(fieldName, getRuleType(), fieldName + " must not be null");
		}

		return null;
	}
}


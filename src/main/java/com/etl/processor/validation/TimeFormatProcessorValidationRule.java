package com.etl.processor.validation;

import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Component
public class TimeFormatProcessorValidationRule implements ProcessorValidationRule {

	@Override
	public String getRuleType() {
		return "timeFormat";
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldRule rule) {
		if (rule.getPattern() == null || rule.getPattern().isBlank()) {
			throw new IllegalStateException("FieldMapping rule 'timeFormat' requires a non-blank 'pattern' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}
	}

	@Override
	public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (value == null) {
			return null;
		}

		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}
		if (rule.getPattern() == null || rule.getPattern().isBlank()) {
			throw new IllegalArgumentException("Validation rule 'timeFormat' requires a non-blank pattern.");
		}

		try {
			LocalTime.parse(text, DateTimeFormatter.ofPattern(rule.getPattern()));
			return null;
		} catch (DateTimeParseException e) {
			return new ValidationIssue(fieldName, getRuleType(), fieldName + " must match " + rule.getPattern());
		}
	}
}


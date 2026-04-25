package com.etl.processor.validation;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class ValidationRuleEvaluator {

	public List<ValidationIssue> evaluate(Object input, ProcessorConfig.EntityMapping mapping) {
		List<ValidationIssue> issues = new ArrayList<>();
		if (mapping == null || mapping.getFields() == null) {
			return issues;
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			if (fieldMapping.getRules() == null || fieldMapping.getRules().isEmpty()) {
				continue;
			}

			Object value = ReflectionUtils.getFieldValue(input, fieldMapping.getFrom());
			for (ProcessorConfig.FieldRule rule : fieldMapping.getRules()) {
				ValidationIssue issue = evaluateRule(fieldMapping.getFrom(), value, rule);
				if (issue != null) {
					issues.add(issue);
				}
			}
		}

		return issues;
	}

	private ValidationIssue evaluateRule(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getType() == null || rule.getType().isBlank()) {
			return null;
		}

		String normalizedType = rule.getType().trim();
		return switch (normalizedType) {
			case "notNull" -> evaluateNotNull(fieldName, value);
			case "timeFormat" -> evaluateTimeFormat(fieldName, value, rule.getPattern());
			default -> throw new IllegalArgumentException("Unsupported validation rule type: " + normalizedType);
		};
	}

	private ValidationIssue evaluateNotNull(String fieldName, Object value) {
		if (value == null) {
			return new ValidationIssue(fieldName, "notNull", fieldName + " must not be null");
		}

		if (value instanceof String stringValue && stringValue.isBlank()) {
			return new ValidationIssue(fieldName, "notNull", fieldName + " must not be null");
		}

		return null;
	}

	private ValidationIssue evaluateTimeFormat(String fieldName, Object value, String pattern) {
		if (value == null) {
			return null;
		}

		String text = value.toString().trim();
		if (text.isEmpty()) {
			return null;
		}

		try {
			LocalTime.parse(text, DateTimeFormatter.ofPattern(pattern));
			return null;
		} catch (DateTimeParseException e) {
			return new ValidationIssue(fieldName, "timeFormat", fieldName + " must match " + pattern);
		}
	}
}

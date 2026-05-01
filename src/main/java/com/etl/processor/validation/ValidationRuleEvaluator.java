package com.etl.processor.validation;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates configured processor-side validation rules for a mapped runtime record.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This class is part of the shared processor validation path and remains aligned
 * with the next architecture direction. Prefer extending validation through the active
 * processor-rule SPI rather than creating job-specific validation code paths.</p>
 */
@Component
public class ValidationRuleEvaluator {

	private final Map<String, ProcessorValidationRule> rulesByType;

	public ValidationRuleEvaluator() {
		this(defaultRules());
	}

	@Autowired
	public ValidationRuleEvaluator(List<ProcessorValidationRule> rules) {
		Map<String, ProcessorValidationRule> indexedRules = new LinkedHashMap<>();
		for (ProcessorValidationRule rule : rules == null ? List.<ProcessorValidationRule>of() : rules) {
			String normalizedType = normalizeType(rule.getRuleType());
			ProcessorValidationRule previous = indexedRules.putIfAbsent(normalizedType, rule);
			if (previous != null) {
				throw new IllegalStateException("Duplicate processor validation rule type registration: " + normalizedType);
			}
		}
		this.rulesByType = Map.copyOf(indexedRules);
	}

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
				ValidationIssue issue = evaluateRule(input, fieldMapping.getFrom(), value, rule);
				if (issue != null) {
					issues.add(issue);
				}
			}
		}

		return issues;
	}

	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getType() == null || rule.getType().isBlank()) {
			throw new IllegalStateException("FieldMapping rule missing 'type' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}

		resolveRule(rule.getType()).validateConfiguration(entityMapping, fieldMapping, rule);
	}

	private ValidationIssue evaluateRule(Object input, String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getType() == null || rule.getType().isBlank()) {
			return null;
		}

		return resolveRule(rule.getType()).evaluate(input, fieldName, value, rule);
	}

	private ProcessorValidationRule resolveRule(String ruleType) {
		String normalizedType = normalizeType(ruleType);
		ProcessorValidationRule rule = rulesByType.get(normalizedType);
		if (rule == null) {
			throw new IllegalArgumentException("Unsupported validation rule type: " + normalizedType);
		}
		return rule;
	}

	private String normalizeType(String ruleType) {
		if (ruleType == null || ruleType.isBlank()) {
			throw new IllegalArgumentException("Validation rule type must not be blank.");
		}
		return ruleType.trim();
	}

	private static List<ProcessorValidationRule> defaultRules() {
		FileIngestionRuntimeSupport runtimeSupport = new FileIngestionRuntimeSupport();
		return List.of(
				new NotNullProcessorValidationRule(),
				new TimeFormatProcessorValidationRule(),
				new DuplicateProcessorValidationRule(runtimeSupport)
		);
	}
}

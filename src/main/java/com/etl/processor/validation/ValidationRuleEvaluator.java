package com.etl.processor.validation;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.enums.ModelFormat;
import com.etl.extension.ExtensionConflictPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
	private final Map<RuleDispatchKey, ProcessorValidationRule> rulesByTypeAndFormat;

	@Autowired
	public ValidationRuleEvaluator(List<ProcessorValidationRule> rules) {
		List<ExtensionConflictPolicy.Candidate<String, ProcessorValidationRule>> globalRuleCandidates = new ArrayList<>();
		List<ExtensionConflictPolicy.Candidate<RuleDispatchKey, ProcessorValidationRule>> scopedRuleCandidates = new ArrayList<>();
		for (ProcessorValidationRule rule : rules == null ? List.<ProcessorValidationRule>of() : rules) {
			String normalizedType = normalizeType(rule.getRuleType());
			Set<ModelFormat> scopedFormats = normalizedFormats(rule.supportedSourceFormats());
			if (scopedFormats.isEmpty()) {
				globalRuleCandidates.add(new ExtensionConflictPolicy.Candidate<>(
						normalizedType,
						rule,
						providerMetadata(rule)
				));
				continue;
			}

			for (ModelFormat scopedFormat : scopedFormats) {
				scopedRuleCandidates.add(new ExtensionConflictPolicy.Candidate<>(
						new RuleDispatchKey(normalizedType, scopedFormat),
						rule,
						providerMetadata(rule)
				));
			}
		}
		this.rulesByType = ExtensionConflictPolicy.resolve(globalRuleCandidates, globalConflictReporter());
		this.rulesByTypeAndFormat = ExtensionConflictPolicy.resolve(scopedRuleCandidates, scopedConflictReporter());
	}

	private ExtensionConflictPolicy.ConflictReporter<String> globalConflictReporter() {
		return new ExtensionConflictPolicy.ConflictReporter<>() {
			@Override
			public void onOverride(String key,
			                       ExtensionConflictPolicy.ProviderMetadata winner,
			                       ExtensionConflictPolicy.ProviderMetadata replaced) {
			}

			@Override
			public void onIgnored(String key,
			                      ExtensionConflictPolicy.ProviderMetadata ignored,
			                      ExtensionConflictPolicy.ProviderMetadata winner) {
			}

			@Override
			public RuntimeException duplicateFailure(String key,
			                                         ExtensionConflictPolicy.ProviderMetadata existing,
			                                         ExtensionConflictPolicy.ProviderMetadata candidate) {
				return new IllegalStateException("Duplicate processor validation rule type registration: " + key
						+ " (extensions: " + existing.providerId() + ", " + candidate.providerId() + ")"
						+ ". Set exactly one extension with isOverride=true to replace an existing rule.");
			}
		};
	}

	private ExtensionConflictPolicy.ConflictReporter<RuleDispatchKey> scopedConflictReporter() {
		return new ExtensionConflictPolicy.ConflictReporter<>() {
			@Override
			public void onOverride(RuleDispatchKey key,
			                       ExtensionConflictPolicy.ProviderMetadata winner,
			                       ExtensionConflictPolicy.ProviderMetadata replaced) {
			}

			@Override
			public void onIgnored(RuleDispatchKey key,
			                      ExtensionConflictPolicy.ProviderMetadata ignored,
			                      ExtensionConflictPolicy.ProviderMetadata winner) {
			}

			@Override
			public RuntimeException duplicateFailure(RuleDispatchKey key,
			                                         ExtensionConflictPolicy.ProviderMetadata existing,
			                                         ExtensionConflictPolicy.ProviderMetadata candidate) {
				return new IllegalStateException("Duplicate processor validation rule registration for type '"
						+ key.ruleType() + "' and source format '" + key.sourceFormat().getFormat() + "'"
						+ " (extensions: " + existing.providerId() + ", " + candidate.providerId() + ")"
						+ ". Set exactly one extension with isOverride=true to replace an existing rule.");
			}
		};
	}

	private ExtensionConflictPolicy.ProviderMetadata providerMetadata(ProcessorValidationRule rule) {
		return new ExtensionConflictPolicy.ProviderMetadata(rule.extensionId(), rule.isOverride());
	}

	/**
	 * Evaluates the configured rules for one mapped runtime record.
	 *
	 * <p>The evaluator chooses the best field name to inspect from the active mapping, then delegates
	 * each configured rule to the registered SPI implementation. The returned issues are ordered in
	 * the same sequence as the mapping configuration so downstream logging and reject evidence remain
	 * predictable.</p>
	 */
	public List<ValidationIssue> evaluate(Object input, ProcessorConfig.EntityMapping mapping) {
		return evaluate(input, mapping, null);
	}

	public List<ValidationIssue> evaluate(Object input,
	                                  ProcessorConfig.EntityMapping mapping,
	                                  ModelFormat sourceFormat) {
		List<ValidationIssue> issues = new ArrayList<>();
		if (mapping == null || mapping.getFields() == null) {
			return issues;
		}

		for (ProcessorConfig.FieldMapping fieldMapping : mapping.getFields()) {
			if (fieldMapping.getRules() == null || fieldMapping.getRules().isEmpty()) {
				continue;
			}

			String fieldName = resolveEvaluationFieldName(input, fieldMapping);
			Object value = fieldName == null ? null : ReflectionUtils.getFieldValue(input, fieldName);
			for (ProcessorConfig.FieldRule rule : fieldMapping.getRules()) {
				ValidationIssue issue = evaluateRule(input, fieldName, value, rule, sourceFormat);
				if (issue != null) {
					issues.add(issue);
				}
			}
		}

		return issues;
	}

	/**
	 * Validates that the configured rule can run against the declared entity mapping before the job
	 * starts.
	 */
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldRule rule) {
		validateConfiguration(entityMapping, fieldMapping, rule, null);
	}

	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                               ProcessorConfig.FieldMapping fieldMapping,
	                               ProcessorConfig.FieldRule rule,
	                               ModelFormat sourceFormat) {
		if (rule == null || rule.getType() == null || rule.getType().isBlank()) {
			throw new IllegalStateException("FieldMapping rule missing 'type' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom() + "'.");
		}

		resolveRule(rule.getType(), sourceFormat).validateConfiguration(entityMapping, fieldMapping, rule);
	}

	private ValidationIssue evaluateRule(Object input,
	                                 String fieldName,
	                                 Object value,
	                                 ProcessorConfig.FieldRule rule,
	                                 ModelFormat sourceFormat) {
		if (rule == null || rule.getType() == null || rule.getType().isBlank()) {
			return null;
		}

		return resolveRule(rule.getType(), sourceFormat).evaluate(input, fieldName, value, rule);
	}

	private String resolveEvaluationFieldName(Object input, ProcessorConfig.FieldMapping fieldMapping) {
		String fromField = normalize(fieldMapping == null ? null : fieldMapping.getFrom());
		String toField = normalize(fieldMapping == null ? null : fieldMapping.getTo());
		if (input instanceof Map<?, ?> map) {
			if (fromField != null && map.containsKey(fromField)) {
				return fromField;
			}
			if (toField != null && map.containsKey(toField)) {
				return toField;
			}
		}
		return fromField != null ? fromField : toField;
	}

	private ProcessorValidationRule resolveRule(String ruleType, ModelFormat sourceFormat) {
		String normalizedType = normalizeType(ruleType);
		if (sourceFormat != null) {
			ProcessorValidationRule scopedRule = rulesByTypeAndFormat.get(new RuleDispatchKey(normalizedType, sourceFormat));
			if (scopedRule != null) {
				return scopedRule;
			}
		}

		ProcessorValidationRule rule = rulesByType.get(normalizedType);
		if (rule != null) {
			return rule;
		}

		if (sourceFormat == null) {
			ProcessorValidationRule uniqueScopedRule = resolveUniqueScopedRule(normalizedType);
			if (uniqueScopedRule != null) {
				return uniqueScopedRule;
			}
		}

		if (sourceFormat != null) {
			throw new IllegalArgumentException("Unsupported validation rule type: " + normalizedType
					+ " for source format " + sourceFormat.getFormat());
		}
		throw new IllegalArgumentException("Unsupported validation rule type: " + normalizedType);
	}

	private ProcessorValidationRule resolveUniqueScopedRule(String normalizedType) {
		Set<ProcessorValidationRule> matches = new HashSet<>();
		for (Map.Entry<RuleDispatchKey, ProcessorValidationRule> entry : rulesByTypeAndFormat.entrySet()) {
			if (entry.getKey().ruleType().equals(normalizedType)) {
				matches.add(entry.getValue());
			}
		}
		if (matches.size() == 1) {
			return matches.iterator().next();
		}
		if (matches.size() > 1) {
			throw new IllegalStateException("Validation rule type '" + normalizedType
					+ "' has multiple source-format registrations and requires source-format context.");
		}
		return null;
	}

	private String normalizeType(String ruleType) {
		if (ruleType == null || ruleType.isBlank()) {
			throw new IllegalArgumentException("Validation rule type must not be blank.");
		}
		return ruleType.trim();
	}

	private String normalize(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private Set<ModelFormat> normalizedFormats(Set<ModelFormat> formats) {
		if (formats == null || formats.isEmpty()) {
			return Set.of();
		}
		Set<ModelFormat> normalized = new HashSet<>();
		for (ModelFormat format : formats) {
			if (format == null) {
				throw new IllegalStateException("Processor validation rule source-format scope must not contain null values.");
			}
			normalized.add(format);
		}
		return Set.copyOf(normalized);
	}


	private record RuleDispatchKey(String ruleType, ModelFormat sourceFormat) {
	}
}

package com.etl.processor.validation;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class DuplicateProcessorValidationRule implements ProcessorValidationRule {

	private final FileIngestionRuntimeSupport fileIngestionRuntimeSupport;

	@Autowired
	public DuplicateProcessorValidationRule(FileIngestionRuntimeSupport fileIngestionRuntimeSupport) {
		this.fileIngestionRuntimeSupport = fileIngestionRuntimeSupport;
	}

	@Override
	public String getRuleType() {
		return "duplicate";
	}

	@Override
	public void validateConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                 ProcessorConfig.FieldMapping fieldMapping,
	                                 ProcessorConfig.FieldRule rule) {
		List<String> keyFields = configuredKeyFields(fieldMapping.getFrom(), rule);
		Set<String> availableFields = availableFields(entityMapping);

		List<String> missingFields = keyFields.stream()
				.filter(keyField -> !availableFields.contains(keyField))
				.toList();
		if (!missingFields.isEmpty()) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' references unknown keyFields "
					+ missingFields + " for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget()
					+ " field '" + fieldMapping.getFrom() + "'.");
		}

		if (isWinnerSelectionStrategy(rule)) {
			validateWinnerSelectionConfiguration(entityMapping, fieldMapping, rule, availableFields);
		}
	}

	@Override
	public ValidationIssue evaluate(Object input, String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (isWinnerSelectionStrategy(rule)) {
			return null;
		}
		List<String> keyFields = configuredKeyFields(fieldName, rule);
		boolean duplicate = fileIngestionRuntimeSupport.isDuplicateValues(
				trackingKey(fieldName, keyFields),
				resolveKeyValues(input, fieldName, value, keyFields)
		);
		if (!duplicate) {
			return null;
		}
		return new ValidationIssue(fieldName, getRuleType(), duplicateMessage(fieldName, keyFields));
	}

	@Override
	public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		return evaluate(null, fieldName, value, rule);
	}

	public static boolean isWinnerSelectionStrategy(ProcessorConfig.FieldRule rule) {
		return !configuredWinnerSelectors(rule).isEmpty();
	}

	public static List<String> configuredKeyFields(String fieldName, ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getKeyFields() == null || rule.getKeyFields().isEmpty()) {
			return List.of(fieldName);
		}

		List<String> normalizedKeyFields = rule.getKeyFields().stream()
				.map(keyField -> keyField == null ? null : keyField.trim())
				.toList();
		if (normalizedKeyFields.stream().anyMatch(keyField -> keyField == null || keyField.isBlank())) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' requires non-blank 'keyFields' values.");
		}

		Set<String> distinctFields = new LinkedHashSet<>(normalizedKeyFields);
		if (distinctFields.size() != normalizedKeyFields.size()) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' requires 'keyFields' to be unique.");
		}
		return List.copyOf(distinctFields);
	}

	public static List<OrderSelector> configuredWinnerSelectors(ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getOrderBy() == null || rule.getOrderBy().isEmpty()) {
			return List.of();
		}

		List<OrderSelector> selectors = new ArrayList<>();
		for (ProcessorConfig.OrderByField orderByField : rule.getOrderBy()) {
			if (orderByField == null) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' contains a null 'orderBy' entry.");
			}
			String field = normalizeBlank(orderByField.getField());
			if (field == null) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' requires non-blank 'orderBy[].field' values.");
			}
			String direction = normalizeBlank(orderByField.getDirection());
			if (direction == null) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' requires non-blank 'orderBy[].direction' values.");
			}
			if (!"ASC".equalsIgnoreCase(direction) && !"DESC".equalsIgnoreCase(direction)) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' has invalid order direction '" + direction
						+ "'. Use ASC or DESC.");
			}
			selectors.add(new OrderSelector(field, "DESC".equalsIgnoreCase(direction)));
		}
		if (selectors.isEmpty()) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' requires at least one valid 'orderBy' entry when 'orderBy' is configured.");
		}
		return selectors;
	}

	private void validateWinnerSelectionConfiguration(ProcessorConfig.EntityMapping entityMapping,
	                                               ProcessorConfig.FieldMapping fieldMapping,
	                                               ProcessorConfig.FieldRule rule,
	                                               Set<String> availableFields) {
		for (OrderSelector selector : configuredWinnerSelectors(rule)) {
			if (!availableFields.contains(selector.field())) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' references unknown winner-order field '"
						+ selector.field() + "' for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget()
						+ " field '" + fieldMapping.getFrom() + "'.");
			}
		}

		long winnerSelectingRuleCount = entityMapping.getFields() == null ? 0 : entityMapping.getFields().stream()
				.filter(candidate -> candidate.getRules() != null)
				.flatMap(candidate -> candidate.getRules().stream())
				.filter(candidateRule -> candidateRule != null && "duplicate".equals(candidateRule.getType()))
				.filter(DuplicateProcessorValidationRule::isWinnerSelectionStrategy)
				.count();
		if (winnerSelectingRuleCount > 1) {
			throw new IllegalStateException("Entity " + entityMapping.getSource() + " -> " + entityMapping.getTarget()
					+ " defines more than one winner-selecting 'duplicate' rule. Configure only one ordered duplicate rule per mapping.");
		}
	}

	private Set<String> availableFields(ProcessorConfig.EntityMapping entityMapping) {
		Set<String> availableFields = new LinkedHashSet<>();
		if (entityMapping.getFields() != null) {
			for (ProcessorConfig.FieldMapping mappingField : entityMapping.getFields()) {
				if (mappingField.getFrom() != null && !mappingField.getFrom().isBlank()) {
					availableFields.add(mappingField.getFrom().trim());
				}
			}
		}
		return availableFields;
	}

	private List<Object> resolveKeyValues(Object input, String fieldName, Object value, List<String> keyFields) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return List.of(value);
		}
		if (input == null) {
			return List.of();
		}
		return keyFields.stream()
				.map(keyField -> ReflectionUtils.getFieldValue(input, keyField))
				.toList();
	}

	private String trackingKey(String fieldName, List<String> keyFields) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return fieldName;
		}
		return fieldName + "::" + String.join("|", keyFields);
	}

	private String duplicateMessage(String fieldName, List<String> keyFields) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return fieldName + " contains a duplicate value within the current step";
		}
		return fieldName + " contains a duplicate composite key within the current step: " + keyFields;
	}

	private static String normalizeBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	public record OrderSelector(String field, boolean descending) {
		public String toDisplayString() {
			return field + " " + (descending ? "DESC" : "ASC");
		}
	}
}

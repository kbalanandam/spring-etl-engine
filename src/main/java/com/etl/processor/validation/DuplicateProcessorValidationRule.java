package com.etl.processor.validation;

import com.etl.common.util.ReflectionUtils;
import com.etl.config.processor.ProcessorConfig;
import com.etl.runtime.DuplicateRule;
import com.etl.runtime.FileIngestionRuntimeSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the processor-side {@code duplicate} validation rule.
 *
 * <p><strong>Transition status:</strong> REUSE.</p>
 *
 * <p>This rule serves two related contracts. Without {@code orderBy}, it performs immediate
 * duplicate detection within the current step and emits normal validation issues. With
 * {@code orderBy}, it acts as the configuration parser for ordered winner selection; actual
 * winner resolution is deferred to the tasklet-based duplicate runtime so the full candidate set
 * can be evaluated before anything is written.</p>
 */
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
		DuplicateIdentityMode identityMode = configuredIdentityMode(rule);
		if (!supportedIdentityModes().contains(identityMode)) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' uses duplicateIdentityMode='"
					+ identityMode.configValue() + "' for entity "
					+ entityMapping.getSource() + " -> " + entityMapping.getTarget() + " field '" + fieldMapping.getFrom()
					+ "', but this source format supports " + supportedIdentityModes().stream().map(DuplicateIdentityMode::configValue).toList() + ".");
		}

		List<String> keyFields = configuredKeyFields(fieldMapping.getFrom(), rule);
		Set<String> availableFields = availableFields(entityMapping);
		DuplicateRule.StorageMode storageMode = configuredStorageMode(rule);

		if (validateKeyFieldsAgainstMappedFields(rule)) {
			List<String> missingFields = keyFields.stream()
					.filter(keyField -> !availableFields.contains(keyField))
					.toList();
			if (!missingFields.isEmpty()) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' references unknown keyFields "
						+ missingFields + " for entity "
						+ entityMapping.getSource() + " -> " + entityMapping.getTarget()
						+ " field '" + fieldMapping.getFrom() + "'.");
			}
		}

		if (isWinnerSelectionStrategy(rule)) {
			validateWinnerSelectionConfiguration(entityMapping, fieldMapping, rule, availableFields);
			return;
		}

		if (storageMode != DuplicateRule.StorageMode.AUTO) {
			throw new IllegalStateException("FieldMapping rule 'duplicate' uses storageMode='"
					+ rule.getStorageMode() + "' for entity " + entityMapping.getSource() + " -> " + entityMapping.getTarget()
					+ " field '" + fieldMapping.getFrom() + "', but storageMode overrides are only supported when 'orderBy' winner selection is configured.");
		}
	}

	@Override
	public ValidationIssue evaluate(Object input, String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		if (isWinnerSelectionStrategy(rule)) {
			return null;
		}
		List<String> keyFields = configuredKeyFields(fieldName, rule);
		boolean duplicate = fileIngestionRuntimeSupport.isDuplicateValues(
				trackingKey(fieldName, keyFields, rule),
				resolveKeyValues(input, fieldName, value, keyFields, rule)
		);
		if (!duplicate) {
			return null;
		}
		return new ValidationIssue(fieldName, getRuleType(), duplicateMessage(fieldName, keyFields, rule));
	}

	@Override
	public ValidationIssue evaluate(String fieldName, Object value, ProcessorConfig.FieldRule rule) {
		return evaluate(null, fieldName, value, rule);
	}

	public static boolean isWinnerSelectionStrategy(ProcessorConfig.FieldRule rule) {
		return !configuredWinnerSelectors(rule).isEmpty();
	}

	/**
	 * Resolves the logical duplicate key fields for the rule, defaulting to the mapped field when
	 * the config does not declare an explicit composite key.
	 */
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

	/**
	 * Parses the ordered winner-selection selectors declared on {@code orderBy}. Presence of at
	 * least one selector is what upgrades the rule from immediate validation into winner-selection
	 * mode.</p>
	 */
	public static List<OrderSelector> configuredWinnerSelectors(ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getOrderBy() == null || rule.getOrderBy().isEmpty()) {
			return List.of();
		}

		List<OrderSelector> selectors = new ArrayList<>();
		Set<String> configuredFields = new LinkedHashSet<>();
		for (ProcessorConfig.OrderByField orderByField : rule.getOrderBy()) {
			if (orderByField == null) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' contains a null 'orderBy' entry.");
			}
			String field = normalizeBlank(orderByField.getField());
			if (field == null) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' requires non-blank 'orderBy[].field' values.");
			}
			if (!configuredFields.add(field)) {
				throw new IllegalStateException("FieldMapping rule 'duplicate' requires 'orderBy[].field' values to be unique.");
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

	public static DuplicateRule.StorageMode configuredStorageMode(ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getStorageMode() == null || rule.getStorageMode().isBlank()) {
			return DuplicateRule.StorageMode.AUTO;
		}

		String normalized = rule.getStorageMode().trim().toLowerCase();
		return switch (normalized) {
			case "auto" -> DuplicateRule.StorageMode.AUTO;
			case "memory" -> DuplicateRule.StorageMode.MEMORY;
			case "embeddeddb", "embedded_db", "embedded-db" -> DuplicateRule.StorageMode.EMBEDDED_DB;
			default -> throw new IllegalStateException("FieldMapping rule 'duplicate' has invalid storageMode '"
					+ rule.getStorageMode() + "'. Supported values are auto, memory, or embeddedDb.");
		};
	}

	public static DuplicateIdentityMode configuredIdentityMode(ProcessorConfig.FieldRule rule) {
		if (rule == null || rule.getDuplicateIdentityMode() == null || rule.getDuplicateIdentityMode().isBlank()) {
			return DuplicateIdentityMode.FLAT_MAPPED;
		}

		String normalized = rule.getDuplicateIdentityMode().trim().toLowerCase();
		return switch (normalized) {
			case "flatmapped", "flat_mapped", "flat-mapped", "flat" -> DuplicateIdentityMode.FLAT_MAPPED;
			case "xmlnative", "xml_native", "xml-native" -> DuplicateIdentityMode.XML_NATIVE;
			default -> throw new IllegalStateException("FieldMapping rule 'duplicate' has invalid duplicateIdentityMode '"
					+ rule.getDuplicateIdentityMode() + "'. Supported values are flatMapped or xmlNative.");
		};
	}

	public static String identityModeReason(ProcessorConfig.FieldRule rule) {
		return rule != null && rule.getDuplicateIdentityMode() != null && !rule.getDuplicateIdentityMode().isBlank()
				? "configured"
				: "default";
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

	protected List<Object> resolveKeyValues(Object input,
	                                     String fieldName,
	                                     Object value,
	                                     List<String> keyFields,
	                                     ProcessorConfig.FieldRule rule) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return Collections.singletonList(value);
		}
		if (input == null) {
			return List.of();
		}
		return keyFields.stream()
				.map(keyField -> resolveKeyValue(input, keyField, rule))
				.toList();
	}

	protected Object resolveKeyValue(Object input, String keyField, ProcessorConfig.FieldRule rule) {
		return ReflectionUtils.getFieldValue(input, keyField);
	}

	protected String trackingKey(String fieldName, List<String> keyFields, ProcessorConfig.FieldRule rule) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return fieldName;
		}
		return fieldName + "::" + String.join("|", keyFields);
	}

	protected String duplicateMessage(String fieldName, List<String> keyFields, ProcessorConfig.FieldRule rule) {
		if (keyFields.size() == 1 && keyFields.get(0).equals(fieldName)) {
			return fieldName + " contains a duplicate value within the current step"
					+ duplicateIdentityModeSuffix(rule);
		}
		return fieldName + " contains a duplicate composite key within the current step: " + keyFields
				+ duplicateIdentityModeSuffix(rule);
	}

	protected boolean validateKeyFieldsAgainstMappedFields(ProcessorConfig.FieldRule rule) {
		return true;
	}

	protected Set<DuplicateIdentityMode> supportedIdentityModes() {
		return Set.of(DuplicateIdentityMode.FLAT_MAPPED);
	}

	private String duplicateIdentityModeSuffix(ProcessorConfig.FieldRule rule) {
		DuplicateIdentityMode mode = configuredIdentityMode(rule);
		if (mode == DuplicateIdentityMode.FLAT_MAPPED) {
			return "";
		}
		return " (duplicateIdentityMode=" + mode.configValue() + ")";
	}

	private static String normalizeBlank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	/**
	 * Canonical runtime representation of one configured duplicate winner-order selector.
	 */
	public record OrderSelector(String field, boolean descending) {
		public String toDisplayString() {
			return field + " " + (descending ? "DESC" : "ASC");
		}
	}

	public enum DuplicateIdentityMode {
		FLAT_MAPPED("flatMapped"),
		XML_NATIVE("xmlNative");

		private final String configValue;

		DuplicateIdentityMode(String configValue) {
			this.configValue = configValue;
		}

		public String configValue() {
			return configValue;
		}
	}
}
